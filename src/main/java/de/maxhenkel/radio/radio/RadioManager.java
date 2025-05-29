package de.maxhenkel.radio.radio;

import com.mojang.authlib.GameProfile;
import de.maxhenkel.radio.Radio;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.world.chunk.WorldChunk;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RadioManager {

    private final Map<UUID, RadioStream> radioStreams;

    public RadioManager() {
        radioStreams = new HashMap<>();
    }

    public void registerTickHandler(){
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (RadioStream stream : radioStreams.values()){
                stream.updateNowPlayingOverlay();
            }
        });
    }





    public void onLoadHead(SkullBlockEntity skullBlockEntity) {
        if (!(skullBlockEntity.getWorld() instanceof ServerWorld serverWorld))
            return;

        ProfileComponent resolvableProfile = skullBlockEntity.getOwner();
        if(resolvableProfile == null) return;

        GameProfile ownerProfile = resolvableProfile.gameProfile();
        RadioData radioData = RadioData.fromGameProfile(ownerProfile);
        if (radioData == null) return;

        this.updateStoredRadioData(skullBlockEntity, serverWorld, radioData, ownerProfile);
    }

    private void updateStoredRadioData(SkullBlockEntity skullBlockEntity, ServerWorld serverLevel, RadioData radioData, GameProfile ownerProfile) {
        // Set the UUID if none was present (block was just placed)
        radioData.updateProfile(ownerProfile);
        RadioStream radioStream = new RadioStream(radioData, serverLevel, skullBlockEntity.getPos());
        Radio.LOGGER.debug("Loaded radio stream for '{}' ({})", radioData.getStationName(), radioData.getId());
        radioStream.init();
        RadioStream oldStream = radioStreams.put(radioData.getId(), radioStream);

        if (oldStream != null) {
            oldStream.close();
            Radio.LOGGER.warn("Replacing radio stream for '{}' ({})", radioData.getStationName(), radioData.getId());
        }
    }

    public static boolean isValidRadioLocation(UUID id, BlockPos pos, ServerWorld world) {
        if (!world.isPosLoaded(pos))
            return false;

        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof SkullBlockEntity skullBlockEntity))
            return false;

        ProfileComponent resolvableProfile = skullBlockEntity.getOwner();
        if(resolvableProfile == null) return false;

        GameProfile ownerProfile = resolvableProfile.gameProfile();
        RadioData radioData = RadioData.fromGameProfile(ownerProfile);
        return radioData != null && radioData.getId().equals(id);
    }

    public void onRemoveHead(UUID id) {
        RadioStream radioStream = radioStreams.remove(id);
        if (radioStream != null) {
            radioStream.close();
            Radio.LOGGER.debug("Removed radio stream for '{}' ({})", radioStream.getRadioData().getStationName(), radioStream.getRadioData().getId());
        } else {
            Radio.LOGGER.debug("Removed radio stream {}", id);
        }
    }

    public void stopStream(UUID id) {
        RadioStream radioStream = radioStreams.get(id);
        if (radioStream != null) {
            radioStream.stop();
        }
    }

    public void updateHeadOnState(UUID id, boolean on) {
        RadioStream radioStream = radioStreams.get(id);
        if (radioStream == null) {
            return;
        }
        if (on) {
            radioStream.start();
        } else {
            radioStream.stop();
        }
    }

    public void onChunkUnload(ServerWorld serverWorld, WorldChunk worldChunk) {
        radioStreams.values().removeIf(radioStream -> {
            boolean remove = radioStream.getServerWorld().getDimension().equals(serverWorld.getDimension()) && isInChunk(radioStream.getPosition(), worldChunk.getPos());
            if (remove) {
                radioStream.close();
            }
            return remove;
        });
    }

    private static boolean isInChunk(BlockPos pos, ChunkPos chunkPos) {
        int chunkX = ChunkSectionPos.getSectionCoord(pos.getX());
        int chunkZ = ChunkSectionPos.getSectionCoord(pos.getZ());
        return chunkX == chunkPos.x && chunkZ == chunkPos.z;
    }

    public void clear() {
        radioStreams.values().forEach(RadioStream::close);
        radioStreams.clear();
    }

    private static RadioManager instance;

    public static RadioManager getInstance() {
        if (instance == null) {
            instance = new RadioManager();
        }
        return instance;
    }
}
