package ru.vibecraft.vibeendstructures.structure;

import org.bukkit.Chunk;
import org.bukkit.World;

import java.util.HashSet;
import java.util.Set;

public final class StructureChunkLoader {

    private StructureChunkLoader() {
    }

    public static void withLoadedChunks(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, Runnable action) {
        int minCx = minX >> 4;
        int maxCx = maxX >> 4;
        int minCz = minZ >> 4;
        int maxCz = maxZ >> 4;
        Set<Chunk> forced = new HashSet<>();

        try {
            for (int cx = minCx; cx <= maxCx; cx++) {
                for (int cz = minCz; cz <= maxCz; cz++) {
                    Chunk chunk = world.getChunkAt(cx, cz);
                    chunk.load(true);
                    chunk.setForceLoaded(true);
                    forced.add(chunk);
                }
            }
            action.run();
        } finally {
            for (Chunk chunk : forced) {
                chunk.setForceLoaded(false);
            }
        }
    }
}
