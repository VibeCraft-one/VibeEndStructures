package ru.vibecraft.vibeendstructures.structure;

import org.bukkit.block.structure.StructureRotation;
import org.bukkit.util.BlockVector;

public record StructureFootprint(int sizeX, int sizeY, int sizeZ) {

    public static StructureFootprint fromSize(BlockVector size, StructureRotation rotation) {
        int x = size.getBlockX();
        int z = size.getBlockZ();
        if (rotation == StructureRotation.CLOCKWISE_90 || rotation == StructureRotation.COUNTERCLOCKWISE_90) {
            return new StructureFootprint(z, size.getBlockY(), x);
        }
        return new StructureFootprint(x, size.getBlockY(), z);
    }

    public int horizontalRadius() {
        return (Math.max(sizeX, sizeZ) / 2) + 1;
    }
}
