package de.maxhenkel.radio.radio;

import de.maxhenkel.radio.Radio;
import de.maxhenkel.radio.RadioVoicechatPlugin;
import de.maxhenkel.radio.utils.TrackHelper;
import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.opus.OpusEncoderMode;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.awt.*;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;
import java.util.function.Supplier;

public class RadioStream implements Supplier<short[]> {

    private final RadioData radioData;
    private final UUID id;
    private final ServerLevel serverLevel;
    private final BlockPos position;
    private TrackHelper trackHelper;
    @Nullable
    private LocationalAudioChannel channel;
    @Nullable
    private AudioPlayer audioPlayer;
    @Nullable
    private Bitstream bitstream;
    @Nullable
    private Decoder decoder;
    @Nullable
    private StreamConverter streamConverter;

    //overlay
    private long OverlayUpdateCounter = 0;
    private static final int overlay_update_rate = 20; //update overlay every n tick
    private long song_elapsed;

    public RadioStream(RadioData radioData, ServerLevel serverLevel, BlockPos position) {
        this.radioData = radioData;
        this.id = radioData.getId();
        this.serverLevel = serverLevel;
        this.position = position;
        this.song_elapsed = 0;
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
            } catch (IOException | URISyntaxException e) {
                Radio.LOGGER.error("Failed to start radio stream", e);
            }
        }, "RadioStreamStarter-%s".formatted(id)).start();

    }

    private void startInternal() throws IOException, URISyntaxException {
        // load mp3
        this.trackHelper = new TrackHelper();

        if (trackHelper.mp3_lists == null || trackHelper.mp3_lists.length == 0){
            Radio.LOGGER.error("Cannot load local mp3!");
            return;
        }

        VoicechatServerApi api = RadioVoicechatPlugin.voicechatServerApi;
        if (api == null) {
            Radio.LOGGER.debug("Voice chat API is not yet loaded");
            RadioVoicechatPlugin.runWhenReady(this::start);
            return;
        }

        if (this.channel != null) {
            stop();
        }

        de.maxhenkel.voicechat.api.ServerLevel level = api.fromServerLevel(this.serverLevel);
        Position pos = api.createPosition(this.position.getX() + 0.5D, this.position.getY() + 0.5D, this.position.getZ() + 0.5D);
        this.channel = api.createLocationalAudioChannel(UUID.randomUUID(), level, pos);

        if(this.channel == null) {
            Radio.LOGGER.error("Failed to create locational audio channel for .");
            return;
        }

        this.channel.setDistance(this.getOutputChannelRange());
        this.channel.setCategory(RadioVoicechatPlugin.RADIOS_CATEGORY);
        this.audioPlayer = api.createAudioPlayer(this.channel, api.createEncoder(OpusEncoderMode.AUDIO), this);

        InputStream input = new URI(radioData.getUrl()).toURL().openStream();
        this.bitstream = new Bitstream(new BufferedInputStream(input));
        this.decoder = new Decoder();

        if(audioPlayer != null){
            audioPlayer.setOnStopped(() -> {
                Radio.LOGGER.debug("Stop radio");
                serverLevel.getServer().execute(() ->{
                    sendMessageProximity("§a[Radio] §cStop playing!");
                });
            });
        }

        loadNextTrack();

        if(audioPlayer == null) {
            //loadNextTrack();
            Radio.LOGGER.error("Unable to start radio stream player -- audio player is null.");
            return;
        }

        this.audioPlayer.startPlaying();
    }

    private void loadNextTrack() throws FileNotFoundException {
        if (this.trackHelper.track_queue.isEmpty()) {this.trackHelper.initialize_queue();}
        this.trackHelper.update_queue();

        // close previous stream
        if(bitstream != null){
            try {
                bitstream.close();
            } catch (Exception e){
                Radio.LOGGER.warn("Can't close bitstream",e );
            }
            bitstream = null;
        }

        // reset old
        this.decoder = new Decoder();
        this.streamConverter = null;

        // create new stream
        FileInputStream inputStream = new FileInputStream(this.trackHelper.get_track_from_queue().track_getFileObj());
        this.bitstream = new Bitstream(new BufferedInputStream(inputStream));
        this.song_elapsed = 0;
        this.broadcastRadioStatus();
    }

    private void sendMessageProximity(String msg){
        net.minecraft.network.chat.Component message = net.minecraft.network.chat.Component.literal(
                msg
        );
        serverLevel.getServer().execute(() -> {
            for (ServerPlayer player: serverLevel.players()){
                if (player.position().distanceTo(Vec3.atBottomCenterOf(position)) <= getOutputChannelRange()) {
                    player.sendSystemMessage( message);

                }
            }
        });
    }
    private void sendMessageLineBreak(){
        sendMessageProximity("-----------------------------------------------");
    }

    private void broadcastRadioStatus(){
        if (!this.trackHelper.track_queue.isEmpty()) {
            TrackHelper.Track currentTrack = this.trackHelper.track_queue.getFirst();
            TrackHelper.Track nextTrack = this.trackHelper.track_queue.getLast();
            sendMessageLineBreak();
            sendMessageProximity(String.format("§a[Radio] §fNow Playing §e%s - %s §b(%s)", currentTrack.track_getSongName(), currentTrack.track_getSongArtist(), currentTrack.track_getTimeStamp()));
            sendMessageLineBreak();
            sendMessageProximity(String.format("§a[Radio] §fUp Next - §d%s", nextTrack.track_getSongName()));
            sendMessageLineBreak();
        }
    }

    public void updateNowPlayingOverlay() {
        if (this.trackHelper != null && (this.audioPlayer != null && this.audioPlayer.isPlaying())) {
            if (OverlayUpdateCounter < overlay_update_rate) {
                OverlayUpdateCounter++;
                return; // dont update yet
            }
            // reset
            OverlayUpdateCounter = 0;
            song_elapsed +=  1;

            long elapsed_min = song_elapsed / 60;
            long elapsed_sec = song_elapsed % 60;

            TrackHelper.Track current_track = this.trackHelper.get_track_from_queue();

            net.minecraft.network.chat.Component overlay_message = net.minecraft.network.chat.Component.literal(
                    String.format("§e♪ §b%s §f- §b%s §7[%02d:%02d/%s]",
                            current_track.track_getSongArtist(),
                            current_track.track_getSongName(),
                            elapsed_min, elapsed_sec,
                            current_track.track_getTimeStamp())
            );

            serverLevel.getServer().execute(() -> {
                for (ServerPlayer player : serverLevel.players()) {
                    if (player.position().distanceTo(Vec3.atBottomCenterOf(position)) <= getOutputChannelRange()) {
                        player.displayClientMessage(overlay_message, true);

                    }
                }
            });

        }
    }

    public void stop() {
        channel = null;
        if (audioPlayer != null) {
            audioPlayer.stopPlaying();
            audioPlayer = null;
        }
        if (bitstream != null) {
            try {
                bitstream.close();
            } catch (Exception e) {
                Radio.LOGGER.warn("Failed to close bitstream", e);
            }
            bitstream = null;
        }
        decoder = null;
        streamConverter = null;
        Radio.LOGGER.debug("Stopped radio stream for '{}' ({})", radioData.getStationName(), radioData.getId());
    }

    public BlockPos getPosition() {
        return position;
    }

    public ServerLevel getServerLevel() {
        return serverLevel;
    }

    public RadioData getRadioData() {
        return radioData;
    }

    private int lastSampleCount;

    @Override
    public short[] get() {
        if (channel == null) {
            return null;
        }
        if (bitstream == null || decoder == null) {
            throw new IllegalStateException("Radio stream not started");
        }
        checkValid();
        spawnParticle();
        try {
            if (streamConverter != null) {
                if (!streamConverter.canAdd(lastSampleCount)) {
                    return streamConverter.getFrame();
                }
            }

            Header frameHeader = bitstream.readFrame();
            if (frameHeader == null) {
                Radio.LOGGER.error(276);


                frameHeader = bitstream.readFrame();
                loadNextTrack();
                if (frameHeader == null) { throw new IOException("End of stream"); }
                //return new short[0];
                //;
            }

            SampleBuffer output = (SampleBuffer) decoder.decodeFrame(frameHeader, bitstream);
            short[] samples = output.getBuffer();
            lastSampleCount = output.getBufferLength();
            bitstream.closeFrame();

            if (streamConverter == null) {
                streamConverter = new StreamConverter(decoder.getOutputFrequency(), decoder.getOutputChannels());
            }

            streamConverter.add(samples, 0, output.getBufferLength());
            return streamConverter.getFrame();
        } catch (Exception e) {
            //Radio.LOGGER.warn("Failed to stream audio from {}", radioData.getUrl(), e);
            Radio.LOGGER.warn("Failed to stream audio: {}", e.getMessage());
            //stop();
            return new short[960];
            //return null;
        }
    }

    private long lastParticle = 0L;

    public void spawnParticle() {
        if (!Radio.SERVER_CONFIG.showMusicParticles.get()) {
            return;
        }
        long time = System.currentTimeMillis();
        if (time - lastParticle < Radio.SERVER_CONFIG.musicParticleFrequency.get()) {
            return;
        }
        lastParticle = time;
        serverLevel.getServer().execute(() -> {
            Vec3 vec3 = Vec3.atBottomCenterOf(position).add(0D, 1D, 0D);
            serverLevel.players().stream().filter(player -> player.position().distanceTo(position.getCenter()) <= 32D).forEach(player -> {
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


    private float getOutputChannelRange() {
        float range = this.radioData.getRange();
        return range > 0
                ? range
                : Radio.SERVER_CONFIG.radioRange.get().floatValue();
    }
}
