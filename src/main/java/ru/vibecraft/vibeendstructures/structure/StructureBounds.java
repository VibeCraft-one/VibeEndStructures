package ru.vibecraft.vibeendstructures.structure;

import org.bukkit.Location;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.util.BlockVector;
import org.bukkit.util.Vector;

public record StructureBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public static StructureBounds fromAnchor(Location anchor, BlockVector size, StructureRotation rotation, Mirror mirror) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        BlockVector anchorVec = new BlockVector(anchor.getBlockX(), anchor.getBlockY(), anchor.getBlockZ());
        int sizeX = size.getBlockX();
        int sizeY = size.getBlockY();
        int sizeZ = size.getBlockZ();

        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    Vector world = anchorVec.clone().add(JigsawMath.transformRelativePos(
                            new BlockVector(x, y, z),
                            size,
                            mirror,
                            rotation
                    ));
                    int wx = world.getBlockX();
                    int wy = world.getBlockY();
                    int wz = world.getBlockZ();
                    minX = Math.min(minX, wx);
                    minY = Math.min(minY, wy);
                    minZ = Math.min(minZ, wz);
                    maxX = Math.max(maxX, wx);
                    maxY = Math.max(maxY, wy);
                    maxZ = Math.max(maxZ, wz);
                }
            }
        }

        if (minX == Integer.MAX_VALUE) {
            int ax = anchor.getBlockX();
            int ay = anchor.getBlockY();
            int az = anchor.getBlockZ();
            return new StructureBounds(ax, ay, az, ax, ay, az);
        }
        return new StructureBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public StructureFootprint footprint() {
        return new StructureFootprint(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
    }
}
