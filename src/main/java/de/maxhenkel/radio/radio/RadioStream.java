package de.maxhenkel.radio.radio;

import de.maxhenkel.radio.Radio;
import de.maxhenkel.radio.RadioVoicechatPlugin;
import de.maxhenkel.radio.utils.TrackHelper;
import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.mp3.Mp3Decoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoderMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import javax.sound.sampled.*;
import java.io.*;
import java.util.*;
import java.util.function.Supplier;

public class RadioStream implements Supplier<short[]> {

    private final RadioData radioData;
    private final UUID id;
    private final ServerLevel serverLevel;
    private final BlockPos position;
    private TrackHelper.QueueManager queueManager;

    // Voicechat components
    @Nullable
    private LocationalAudioChannel vcChannel;
    @Nullable
    private AudioPlayer vcAudioPlayer;
    @Nullable
    private VoicechatServerApi vcApi;

    // Track & Audio data
    @Nullable
    private TrackHelper.Track currentTrack;
    @Nullable
    private short[] currentTrackAudio;
    private int audioPosition = 0;
    private static final int SIMPLEVC_FRAME_SIZE = 960;

    private long trackStartTime = 0;
    private long trackDurationMs = 0;

    // Track management
    //private final Queue<TrackHelper.Track> trackQueue = new ArrayDeque<>();
    //private final ArrayList<TrackHelper.Track> trackQueue = new ArrayList<>();
    private boolean isLoadingTrack = false;

    // Overlay
    private long overlayUpdateCounter = 0;
    private static final int OVERLAY_UPDATE_RATE = 20;
    private final BossEvent.BossBarColor[] barColors = {BossEvent.BossBarColor.BLUE,
            BossEvent.BossBarColor.RED, BossEvent.BossBarColor.WHITE, BossEvent.BossBarColor.GREEN,
            BossEvent.BossBarColor.PINK, BossEvent.BossBarColor.PURPLE};
    private ServerBossEvent bossBar;
    private final Set<de.maxhenkel.voicechat.api.ServerPlayer> bossBarPlayers = new HashSet<>();

    public RadioStream(RadioData radioData, ServerLevel serverLevel, BlockPos position) {
        this.radioData = radioData;
        this.id = radioData.getId();
        this.serverLevel = serverLevel;
        this.position = position;
    }

    public void init() {
        if (radioData.isOn()) {
            start();
        }
    }

    public void start() {
        new Thread(() -> {
            try {
                this.startInternal();
            } catch (Exception e) {
                Radio.LOGGER.error("Failed to start radio stream", e);
            }
        }, "RadioStreamStarter-%s".formatted(id)).start();
    }

    private void startInternal() throws IOException {
        // Load track helper
        this.queueManager = new TrackHelper.QueueManager();

        if (queueManager.tracks_allLoaded == null || queueManager.tracks_allLoaded.isEmpty()) {
            Radio.LOGGER.error("Cannot load local mp3!");
            return;
        }

        this.vcApi = RadioVoicechatPlugin.voicechatServerApi;
        if (vcApi == null) {
            Radio.LOGGER.debug("Voice chat API is not yet loaded");
            RadioVoicechatPlugin.runWhenReady(this::start);
            return;
        }

        if (this.vcChannel != null) {
            stop();
        }

        // Create voicechat channel
        de.maxhenkel.voicechat.api.ServerLevel level = vcApi.fromServerLevel(this.serverLevel);
        Position pos = vcApi.createPosition(this.position.getX() + 0.5D, this.position.getY() + 0.5D, this.position.getZ() + 0.5D);
        this.vcChannel = vcApi.createLocationalAudioChannel(UUID.randomUUID(), level, pos);

        if (this.vcChannel == null) {
            Radio.LOGGER.error("Failed to create locational audio channel");
            return;
        }

        this.vcChannel.setDistance(this.getOutputChannelRange());
        this.vcChannel.setCategory(RadioVoicechatPlugin.RADIOS_CATEGORY);
        this.vcAudioPlayer = vcApi.createAudioPlayer(this.vcChannel, vcApi.createEncoder(OpusEncoderMode.AUDIO), this);

        if (vcAudioPlayer != null) {
            vcAudioPlayer.setOnStopped(() -> {
                Radio.LOGGER.debug("Stop radio");
                serverLevel.getServer().execute(() -> {
                    sendMessageProximity("§a[Radio] §cStop playing!");
                });
            });
        }

        // Initialize track queue and start playing
        this.queueManager.update();
        loadNextTrack();

        if (vcAudioPlayer == null) {
            Radio.LOGGER.error("Unable to start radio stream player -- audio player is null.");
            return;
        }

        this.vcAudioPlayer.startPlaying();
        Radio.LOGGER.info("Radio stream started successfully");
    }



    private void loadNextTrack() {
        if (isLoadingTrack) {
            return;
        }

        //queueManager.update();

        TrackHelper.Track nextTrack = queueManager.poll();
        Radio.LOGGER.info(nextTrack);
        if (nextTrack == null){
            return;
        }

        isLoadingTrack = true;

        // refresh boss bar
        if (this.bossBar != null){
            bossBar.removeAllPlayers();
            //§e♪ §b%s\n§7by §b%s §7[%02d:%02d/%02d:%02d]
            String displayText = String.format("§e♪ §b%s §7- §b%s §7[00:00/%02d:%02d]",
            currentTrack.track_title, currentTrack.track_artist,
            (trackDurationMs / 1000) / 60, (trackDurationMs / 1000) % 60);

            bossBar.setName(Component.literal(displayText));
            bossBar.setProgress(0.0f);
        }

        // load track in another thread
        try {
            loadTrackInternal(nextTrack);
        } catch (Exception e){
            Radio.LOGGER.error(e);
        }


    }


    private void loadTrackInternal(TrackHelper.Track track) throws IOException {
        Radio.LOGGER.info("Loading track {}", track.track_title);

        File trackFile = track.track_file;
        if (trackFile == null || !trackFile.exists()) {
            throw new IOException("Track file does not exist : " + trackFile);
        }

        if (!trackFile.canRead()) {
            throw new IOException("Cannot read track file: " + trackFile);
        }

        try (FileInputStream fileInputStream = new FileInputStream(trackFile)) {
            assert vcApi != null;
            Mp3Decoder vcMp3Decoder = vcApi.createMp3Decoder(fileInputStream);

            assert vcMp3Decoder != null;
            AudioFormat originalFormat = vcMp3Decoder.getAudioFormat();

            Radio.LOGGER.info("Original Track format - Sample Rate: {}, Channels: {}, Sample Size: {}, Bitrate : {}",
                    originalFormat.getSampleRate(), originalFormat.getChannels(), originalFormat.getSampleSizeInBits(), vcMp3Decoder.getBitrate());

            short[] rawAudioData = vcMp3Decoder.decode();

            // Convert to mono 48kHz if needed (voice chat standard)
            short[] processedAudioData = convertAudioFormat(rawAudioData, originalFormat);

            Radio.LOGGER.debug("Processed {} samples from track", processedAudioData.length);

            // Calculate duration based on processed audio (48kHz mono)
            float durationSeconds = processedAudioData.length / 48000.0f;
            trackDurationMs = (long) (durationSeconds * 1000);

            serverLevel.getServer().execute(() -> {
                currentTrack = track;
                currentTrackAudio = processedAudioData;
                audioPosition = 0;
                trackStartTime = System.currentTimeMillis();
                isLoadingTrack = false;

                broadcastRadioStatus();
            });
        } catch (Exception e) {
            isLoadingTrack = false;
            throw new IOException("Failed to decode mp3 : " + e.getMessage(), e);
        }
    }


    // convert audio to 48kHz mono format for voice chat compatibility

    private short[] convertAudioFormat(short[] audioData, AudioFormat originalFormat) {
        float originalSampleRate = originalFormat.getSampleRate();
        int originalChannels = originalFormat.getChannels();

        // Target format: 48kHz mono
        float targetSampleRate = 48000.0f;
        int targetChannels = 1;

        short[] processedData = audioData;

        // Convert stereo to mono
        if (originalChannels == 2) {
            processedData = stereoToMono(processedData);
            Radio.LOGGER.debug("Converted stereo to mono");
        }

        // Step 2: Resample if sample rate is different
        if (originalSampleRate != targetSampleRate) {
            processedData = resampleAudio(processedData, originalSampleRate, targetSampleRate);
            Radio.LOGGER.debug("Resampled from {}Hz to {}Hz", originalSampleRate, targetSampleRate);
        }

        return processedData;
    }

    // convert streo to mono
    private short[] stereoToMono(short[] stereoData) {
        short[] monoData = new short[stereoData.length / 2];

        for (int i = 0; i < monoData.length; i++) {
            int left = stereoData[i * 2];
            int right = stereoData[i * 2 + 1];
            monoData[i] = (short) ((left + right) / 2);
        }

        return monoData;
    }

    // resampling
    private short[] resampleAudio(short[] inputData, float inputSampleRate, float outputSampleRate) {
        if (inputSampleRate == outputSampleRate) {
            return inputData;
        }

        double ratio = inputSampleRate / outputSampleRate;
        int outputLength = (int) (inputData.length / ratio);
        short[] outputData = new short[outputLength];

        for (int i = 0; i < outputLength; i++) {
            double inputIndex = i * ratio;
            int index1 = (int) inputIndex;
            int index2 = Math.min(index1 + 1, inputData.length - 1);

            if (index1 >= inputData.length) {
                break;
            }

            // Linear interpolation
            double fraction = inputIndex - index1;
            double interpolated = inputData[index1] * (1.0 - fraction) + inputData[index2] * fraction;
            outputData[i] = (short) Math.round(interpolated);
        }

        return outputData;
    }

    @Override
    public short[] get() {
        if (vcChannel == null || currentTrackAudio == null) {
            return new short[SIMPLEVC_FRAME_SIZE];
        }

        checkValid();
        spawnParticle();

        try {
            if (audioPosition >= currentTrackAudio.length){
                if (!isLoadingTrack){
                    Radio.LOGGER.debug("Track finished, loading new...");
                    serverLevel.getServer().execute(this::loadNextTrack);
                }
                return new short[SIMPLEVC_FRAME_SIZE];
            }

            int sampleAvailable = currentTrackAudio.length - audioPosition;
            int samplesToRead = Math.min(SIMPLEVC_FRAME_SIZE, sampleAvailable);

            short[] frame = new short[SIMPLEVC_FRAME_SIZE];

            System.arraycopy(currentTrackAudio, audioPosition, frame, 0, samplesToRead);

            audioPosition += samplesToRead;
            return frame;
        } catch (Exception e) {
            Radio.LOGGER.warn("Error getting audio frame: {}", e.getMessage());
            return new short[SIMPLEVC_FRAME_SIZE];
        }
    }



    public void updateNowPlayingOverlay() {
        if (currentTrack != null && currentTrackAudio != null) {
            if (overlayUpdateCounter < OVERLAY_UPDATE_RATE) {
                overlayUpdateCounter++;
                return;
            }

            overlayUpdateCounter = 0;

            // Calculate playback position
            long elapsedMs = System.currentTimeMillis() - trackStartTime;
            long elapsedSeconds = elapsedMs / 1000;
            long durationSeconds = trackDurationMs / 1000;

            long elapsedMin = elapsedSeconds / 60;
            long elapsedSec = elapsedSeconds % 60;
            long durationMin = durationSeconds / 60;
            long durSec = durationSeconds % 60;



            serverLevel.getServer().execute(() -> {
                updateBossBar(elapsedMin, elapsedSec, durationMin, durSec, elapsedMs);
            });
        }
    }

    private void updateBossBar(long elapsedMin, long elapsedSec, long durMin, long durSec, long elapsedMs){
        if (currentTrack != null) {
            String displayText = String.format("§e♪ §b%s §7by §b%s §7[%02d:%02d/%02d:%02d]",
            currentTrack.track_title, currentTrack.track_artist,
            elapsedMin, elapsedSec, durMin, durSec);
            if (this.bossBar == null) {


                this.bossBar = new ServerBossEvent(
                        Component.literal(displayText),
                        barColors[new Random().nextInt(0,4)],
                        BossEvent.BossBarOverlay.PROGRESS
                );
            } else {
                // update
                this.bossBar.setName(Component.literal(displayText));
            }

            if (trackDurationMs > 0){
                float progress = Math.min(1.0f, (float) elapsedMs / trackDurationMs);
                this.bossBar.setProgress(progress);
            }

            // set to player in range
            HashSet<ServerPlayer> nearbyPlayers = new HashSet<>();
            for (ServerPlayer player : serverLevel.players()) {

                if (player.position().distanceTo(Vec3.atBottomCenterOf(position)) <= getOutputChannelRange()) {
                    nearbyPlayers.add(player);
                }
            }

            // remove if no longer near
            for (ServerPlayer player : this.bossBar.getPlayers()) {
                if (!nearbyPlayers.contains(player)){
                    this.bossBar.removePlayer(player);
                    //bossBarPlayers.remove(player);
                }
            }

            // add if near
            for (ServerPlayer player : nearbyPlayers) {
                if (!this.bossBar.getPlayers().contains(player)) {
                    bossBar.addPlayer(player);
                    //bossBarPlayers.add((de.maxhenkel.voicechat.api.ServerPlayer) player);
                }
            }
        }
    }

    private void sendMessageProximity(String msg) {
        Component message = Component.literal(msg);
        serverLevel.getServer().execute(() -> {
            for (ServerPlayer player : serverLevel.players()) {
                if (player.position().distanceTo(Vec3.atBottomCenterOf(position)) <= getOutputChannelRange()) {
                    player.sendSystemMessage(message);
                }
            }
        });
    }

    private void sendMessageLineBreak() {
        sendMessageProximity("-----------------------------------------------");
    }

    private void broadcastRadioStatus() {
        sendMessageLineBreak();
        assert currentTrack != null;
        sendMessageProximity(String.format("§a[Radio] §fNow Playing §e%s - %s",
                currentTrack.track_title, currentTrack.track_artist));
        sendMessageLineBreak();
        String nextTrackName = queueManager.peek(0).track_title;
        sendMessageProximity(String.format("§a[Radio] §fUp Next - §d%s", nextTrackName));
        sendMessageLineBreak();
    }

    public void stop() {
        vcChannel = null;
        if (vcAudioPlayer != null) {
            vcAudioPlayer.stopPlaying();
            vcAudioPlayer = null;
        }

        if (this.bossBar != null){
            this.bossBar.removeAllPlayers();
            this.bossBar = null;
        }

        currentTrack = null;
        currentTrackAudio = null;
        audioPosition = 0;
        //trackHelper.track_queue.clear();
        isLoadingTrack = false;

        Radio.LOGGER.debug("Stopped radio stream for '{}' ({})", radioData.getStationName(), radioData.getId());
    }

    private long lastParticle = 0L;

    public void spawnParticle() {
        if (!Radio.SERVER_CONFIG.showMusicParticles.get()) {
            return;
        }
        long time = System.currentTimeMillis();
        float particle_delay;
        if (currentTrack != null){
            particle_delay = (60000 / currentTrack.track_bpm) * 2;
        } else {
            particle_delay = Radio.SERVER_CONFIG.musicParticleFrequency.get();
        }

        if (time - lastParticle < particle_delay) {
            return;
        }
        lastParticle = time;
        serverLevel.getServer().execute(() -> {
            Vec3 vec3 = Vec3.atBottomCenterOf(position).add(0D, 1D, 0D);
            serverLevel.players().stream()
                    .filter(player -> player.position().distanceTo(position.getCenter()) <= 32D)
                    .forEach(player -> {
                        float random = (float) serverLevel.getRandom().nextInt(4) / 24F;
                        serverLevel.sendParticles(ParticleTypes.NOTE, vec3.x(), vec3.y(), vec3.z(), 0, random, 0D, 0D, 1D);
                    });
        });
    }

    private long lastCheck;

    private void checkValid() {
        long time = System.currentTimeMillis();
        if (time - lastCheck < 30000L) {
            return;
        }
        lastCheck = time;
        serverLevel.getServer().execute(() -> {
            if (!RadioManager.isValidRadioLocation(id, position, serverLevel)) {
                RadioManager.getInstance().stopStream(id);
                Radio.LOGGER.warn("Stopped radio stream {} as it doesn't exist anymore", id);
            }
        });
    }

    public void close() {
        stop();
    }

    // Getters
    public BlockPos getPosition() {
        return position;
    }

    public ServerLevel getServerLevel() {
        return serverLevel;
    }

    public RadioData getRadioData() {
        return radioData;
    }

    private float getOutputChannelRange() {
        float range = this.radioData.getRange();
        return range > 0 ? range : Radio.SERVER_CONFIG.radioRange.get().floatValue();
    }
}