package ru.vibecraft.vibeendstructures.structure;

import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.util.BlockVector;

public final class JigsawMath {

  private JigsawMath() {
  }

  public static BlockVector orientationFacing(String orientation) {
    String dir = orientation.contains("_") ? orientation.substring(0, orientation.indexOf('_')) : orientation;
    return switch (dir.toLowerCase()) {
      case "north" -> new BlockVector(0, 0, -1);
      case "south" -> new BlockVector(0, 0, 1);
      case "east" -> new BlockVector(1, 0, 0);
      case "west" -> new BlockVector(-1, 0, 0);
      case "up" -> new BlockVector(0, 1, 0);
      case "down" -> new BlockVector(0, -1, 0);
      default -> new BlockVector(0, 0, 1);
    };
  }

  public static BlockVector transformRelativePos(
          BlockVector pos,
          BlockVector size,
          Mirror mirror,
          StructureRotation rotation
  ) {
    int x = pos.getBlockX();
    int y = pos.getBlockY();
    int z = pos.getBlockZ();
    int sizeX = size.getBlockX();
    int sizeZ = size.getBlockZ();

    if (mirror == Mirror.LEFT_RIGHT) {
      x = sizeX - 1 - x;
    }

    return switch (rotation) {
      case NONE -> new BlockVector(x, y, z);
      case CLOCKWISE_90 -> new BlockVector(sizeZ - 1 - z, y, x);
      case CLOCKWISE_180 -> new BlockVector(sizeX - 1 - x, y, sizeZ - 1 - z);
      case COUNTERCLOCKWISE_90 -> new BlockVector(z, y, sizeX - 1 - x);
    };
  }

  public static BlockVector rotateFacing(BlockVector facing, StructureRotation rotation, Mirror mirror) {
    int x = facing.getBlockX();
    int y = facing.getBlockY();
    int z = facing.getBlockZ();

    if (mirror == Mirror.LEFT_RIGHT) {
      x = -x;
    }

    return switch (rotation) {
      case NONE -> new BlockVector(x, y, z);
      case CLOCKWISE_90 -> new BlockVector(-z, y, x);
      case CLOCKWISE_180 -> new BlockVector(-x, y, -z);
      case COUNTERCLOCKWISE_90 -> new BlockVector(z, y, -x);
    };
  }

  public static BlockVector toBlockVector(int[] pos) {
    return new BlockVector(pos[0], pos[1], pos[2]);
  }

  public static BlockVector toSizeVector(int[] size) {
    return new BlockVector(size[0], size[1], size[2]);
  }
}
