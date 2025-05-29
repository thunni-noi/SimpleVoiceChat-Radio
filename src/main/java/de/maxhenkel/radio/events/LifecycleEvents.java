package de.maxhenkel.radio.events;

import de.maxhenkel.radio.radio.RadioManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.chunk.WorldChunk;

public class LifecycleEvents {

    public static void onServerStopping(MinecraftServer server) {
        RadioManager.getInstance().clear();
    }

    public static void onChunkUnload(ServerWorld serverWorld, WorldChunk worldChunk) {
        RadioManager.getInstance().onChunkUnload(serverWorld, worldChunk);
    }
}
