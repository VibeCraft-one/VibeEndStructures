package ru.vibecraft.vibeendstructures.generation;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Optional;
import java.util.Random;
import java.util.Set;

public final class EndSurfaceFinder {

    private static final Set<Material> SURFACE_BLOCKS = Set.of(
            Material.END_STONE,
            Material.END_STONE_BRICKS,
            Material.PURPUR_BLOCK,
            Material.PURPUR_PILLAR,
            Material.PURPUR_STAIRS,
            Material.PURPUR_SLAB
    );

    private EndSurfaceFinder() {
    }

    public static Optional<Block> findSurface(World world, int chunkX, int chunkZ, int minY, Random random) {
        int baseX = chunkX * 16;
        int baseZ = chunkZ * 16;
        int offsetX = 4 + random.nextInt(8);
        int offsetZ = 4 + random.nextInt(8);

        for (int attempt = 0; attempt < 6; attempt++) {
            int x = baseX + offsetX;
            int z = baseZ + offsetZ;

            for (int y = world.getMaxHeight() - 1; y >= minY; y--) {
                Block block = world.getBlockAt(x, y, z);
                if (!SURFACE_BLOCKS.contains(block.getType())) {
                    continue;
                }
                Block above = block.getRelative(0, 1, 0);
                if (above.getType().isAir() || above.getType() == Material.CHORUS_PLANT || above.getType() == Material.CHORUS_FLOWER) {
                    return Optional.of(block);
                }
            }

            offsetX = 2 + random.nextInt(12);
            offsetZ = 2 + random.nextInt(12);
        }

        return Optional.empty();
    }

    public static Optional<Block> findSurfaceAt(World world, int x, int z, int minY) {
        for (int y = world.getMaxHeight() - 1; y >= minY; y--) {
            Block block = world.getBlockAt(x, y, z);
            if (!SURFACE_BLOCKS.contains(block.getType())) {
                continue;
            }
            Block above = block.getRelative(0, 1, 0);
            if (above.getType().isAir() || above.getType() == Material.CHORUS_PLANT || above.getType() == Material.CHORUS_FLOWER) {
                return Optional.of(block);
            }
        }
        return Optional.empty();
    }
}
