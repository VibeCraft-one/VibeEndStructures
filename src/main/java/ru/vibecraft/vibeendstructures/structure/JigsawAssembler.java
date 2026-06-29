package ru.vibecraft.vibeendstructures.structure;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.structure.Structure;
import org.bukkit.util.BlockVector;
import org.bukkit.util.Vector;
import ru.vibecraft.vibeendstructures.model.JigsawConnector;
import ru.vibecraft.vibeendstructures.model.ShipPieceMetadata;
import ru.vibecraft.vibeendstructures.model.StructureDefinition;
import ru.vibecraft.vibeendstructures.model.StructurePiece;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

public final class JigsawAssembler {

  private static final int MAX_PIECES = 32;

  private final StructureRegistry registry;
  private final JigsawMetadataRegistry metadata;
  private final StructureMetaRegistry metaRegistry;
  private final StructureFinalizeService finalizeService;
  private final Logger logger;

  public JigsawAssembler(
          StructureRegistry registry,
          JigsawMetadataRegistry metadata,
          StructureMetaRegistry metaRegistry,
          StructureFinalizeService finalizeService,
          Logger logger
  ) {
    this.registry = registry;
    this.metadata = metadata;
    this.metaRegistry = metaRegistry;
    this.finalizeService = finalizeService;
    this.logger = logger;
  }

  public AssemblyResult assemble(
          World world,
          Location anchor,
          StructureDefinition definition,
          StructurePiece startPiece,
          StructureRotation rotation,
          Mirror mirror,
          Random random
  ) {
    String structureId = definition.id();
    ShipPieceMetadata startMeta = metadata.getPiece(structureId, startPiece.resourcePath()).orElse(null);
    if (startMeta == null) {
      return AssemblyResult.failed("no jigsaw metadata for " + startPiece.resourcePath());
    }

    Structure startStructure = registry.getStructure(definition, startPiece).orElse(null);
    if (startStructure == null) {
      return AssemblyResult.failed("missing NBT for " + startPiece.resourcePath());
    }

    Set<String> placed = new HashSet<>();
    Map<String, Location> placedAnchors = new HashMap<>();
    List<PlacedPiece> placedPieces = new ArrayList<>();
    Queue<PendingConnection> queue = new ArrayDeque<>();
    Bounds bounds = new Bounds();

    ensureChunksLoaded(world, anchor, startMeta.size(), rotation);

    placeBlocksOnly(world, anchor, startStructure, rotation, mirror, random);
    placed.add(startPiece.resourcePath());
    placedAnchors.put(pieceBasename(startPiece.resourcePath()), anchor);
    placedPieces.add(new PlacedPiece(startPiece.resourcePath(), anchor.clone()));
    bounds.include(anchor, startMeta.size(), rotation);

    enqueueOutgoing(startMeta, anchor, rotation, mirror, queue);

    int placedCount = 1;
    int expectedPieces = metadata.getPieces(structureId).size();

    while (!queue.isEmpty() && placedCount < MAX_PIECES) {
      if (processConnection(world, definition, structureId, placed, placedAnchors, placedPieces, queue, queue.poll(), rotation, mirror, random, bounds)) {
        placedCount = placed.size();
      }
    }

    completeMissingPieces(world, definition, structureId, placed, placedAnchors, placedPieces, rotation, mirror, random, bounds);

    ensureChunksLoaded(world, bounds);
    for (PlacedPiece placedPiece : placedPieces) {
      StructurePiece piece = findPiece(definition, placedPiece.resourcePath());
      if (piece == null) {
        continue;
      }
      Structure structure = registry.getStructure(definition, piece).orElse(null);
      if (structure != null) {
        placeBlocksOnly(world, placedPiece.anchor(), structure, rotation, mirror, random);
      }
    }
    finalizePlacedPieces(world, placedPieces, rotation, mirror, random);
    finalizeService.sweepTechnicalBounds(world, bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ());
    finalizeService.removeDisallowedEntities(world, bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ());

    if (placed.size() < expectedPieces) {
      logger.warning("Ship " + structureId + " incomplete: " + placed.size() + "/" + expectedPieces + " pieces at "
              + anchor.getBlockX() + "," + anchor.getBlockY() + "," + anchor.getBlockZ());
    }

    return AssemblyResult.ok(bounds.toFootprint(rotation), List.copyOf(placedPieces), bounds);
  }

  public void refine(
          World world,
          StructureDefinition definition,
          List<PlacedPiece> placedPieces,
          StructureRotation rotation,
          Mirror mirror,
          Random random,
          Bounds bounds
  ) {
    ensureChunksLoaded(world, bounds);
    StructureChunkLoader.withLoadedChunks(
            world,
            bounds.minX(), bounds.minY(), bounds.minZ(),
            bounds.maxX(), bounds.maxY(), bounds.maxZ(),
            () -> {
              for (PlacedPiece placedPiece : placedPieces) {
                StructurePiece piece = findPiece(definition, placedPiece.resourcePath());
                if (piece == null) {
                  continue;
                }
                Structure structure = registry.getStructure(definition, piece).orElse(null);
                if (structure == null) {
                  continue;
                }
                placeBlocksOnly(world, placedPiece.anchor(), structure, rotation, mirror, random);
              }
              refinePlacedPieces(world, placedPieces, rotation, mirror, random);
              finalizeService.sweepTechnicalBounds(
                      world,
                      bounds.minX(), bounds.minY(), bounds.minZ(),
                      bounds.maxX(), bounds.maxY(), bounds.maxZ()
              );
              placeShipLimeShulkerBox(world, placedPieces, rotation, mirror, random);
            }
    );
  }

  private void placeShipLimeShulkerBox(
          World world,
          List<PlacedPiece> placedPieces,
          StructureRotation rotation,
          Mirror mirror,
          Random random
  ) {
    List<ShipPieceRef> shipPieces = placedPieces.stream()
            .map(piece -> new ShipPieceRef(piece.anchor(), piece.resourcePath()))
            .toList();
    finalizeService.maybePlaceShipLimeShulkerBox(world, shipPieces, metaRegistry, rotation, mirror, random);
  }

  private StructurePiece findPiece(StructureDefinition definition, String resourcePath) {
    for (StructurePiece piece : definition.pieces()) {
      if (piece.resourcePath().equals(resourcePath)) {
        return piece;
      }
    }
    return null;
  }

  private void completeMissingPieces(
          World world,
          StructureDefinition definition,
          String structureId,
          Set<String> placed,
          Map<String, Location> placedAnchors,
          List<PlacedPiece> placedPieces,
          StructureRotation rotation,
          Mirror mirror,
          Random random,
          Bounds bounds
  ) {
    boolean progress = true;
    int expectedPieces = metadata.getPieces(structureId).size();
    Queue<PendingConnection> queue = new ArrayDeque<>();

    while (progress && placed.size() < expectedPieces && placed.size() < MAX_PIECES) {
      progress = false;
      queue.clear();

      for (Map.Entry<String, Location> entry : new ArrayList<>(placedAnchors.entrySet())) {
        ShipPieceMetadata placedMeta = metadata.getPiece(structureId, entry.getKey()).orElse(null);
        if (placedMeta == null) {
          continue;
        }
        enqueueOutgoing(placedMeta, entry.getValue(), rotation, mirror, queue);
      }

      while (!queue.isEmpty() && placed.size() < expectedPieces) {
        if (processConnection(world, definition, structureId, placed, placedAnchors, placedPieces, queue, queue.poll(), rotation, mirror, random, bounds)) {
          progress = true;
        }
      }
    }
  }

  private boolean processConnection(
          World world,
          StructureDefinition definition,
          String structureId,
          Set<String> placed,
          Map<String, Location> placedAnchors,
          List<PlacedPiece> placedPieces,
          Queue<PendingConnection> outgoingQueue,
          PendingConnection pending,
          StructureRotation rotation,
          Mirror mirror,
          Random random,
          Bounds bounds
  ) {
    if (pending == null || !pending.connector().hasOutgoingPool()) {
      return false;
    }

    List<StructurePiece> poolPieces = registry.getPiecesForPool(definition, pending.connector().pool());
    StructurePiece chosen = pickMatchingPiece(poolPieces, structureId, pending.connector().target(), placed, random);
    if (chosen == null) {
      return false;
    }

    ShipPieceMetadata pieceMeta = metadata.getPiece(structureId, chosen.resourcePath()).orElse(null);
    Structure structure = registry.getStructure(definition, chosen).orElse(null);
    if (pieceMeta == null || structure == null) {
      return false;
    }

    JigsawConnector targetJigsaw = findJigsawByName(pieceMeta, pending.connector().target());
    if (targetJigsaw == null) {
      return false;
    }

    BlockVector pieceSize = JigsawMath.toSizeVector(pieceMeta.size());
    BlockVector transformedTarget = JigsawMath.transformRelativePos(
            JigsawMath.toBlockVector(targetJigsaw.pos()),
            pieceSize,
            mirror,
            rotation
    );
    Location pieceAnchor = new Location(
            world,
            pending.connectingPos().getX(),
            pending.connectingPos().getY(),
            pending.connectingPos().getZ()
    );
    pieceAnchor.subtract(transformedTarget);

    ensureChunksLoaded(world, pieceAnchor, pieceMeta.size(), rotation);

    placeBlocksOnly(world, pieceAnchor, structure, rotation, mirror, random);
    placed.add(chosen.resourcePath());
    placedAnchors.put(pieceBasename(chosen.resourcePath()), pieceAnchor);
    placedPieces.add(new PlacedPiece(chosen.resourcePath(), pieceAnchor.clone()));
    bounds.include(pieceAnchor, pieceMeta.size(), rotation);

    enqueueOutgoing(pieceMeta, pieceAnchor, rotation, mirror, outgoingQueue);

    return true;
  }

  private void placeBlocksOnly(
          World world,
          Location anchor,
          Structure structure,
          StructureRotation rotation,
          Mirror mirror,
          Random random
  ) {
    try {
      StructurePlacementUtil.placeBlocks(structure, anchor, rotation, mirror, random);
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to place piece at " + anchor.getBlockX() + ","
              + anchor.getBlockY() + "," + anchor.getBlockZ() + ": " + ex.getMessage(), ex);
    }
  }

  private void refinePlacedPieces(
          World world,
          List<PlacedPiece> placedPieces,
          StructureRotation rotation,
          Mirror mirror,
          Random random
  ) {
    for (PlacedPiece placedPiece : placedPieces) {
      metaRegistry.get(placedPiece.resourcePath()).ifPresent(meta ->
              finalizeService.refinePiece(world, placedPiece.anchor(), rotation, mirror, meta, random)
      );
    }
  }

  private void finalizePlacedPieces(
          World world,
          List<PlacedPiece> placedPieces,
          StructureRotation rotation,
          Mirror mirror,
          Random random
  ) {
    for (PlacedPiece placedPiece : placedPieces) {
      metaRegistry.get(placedPiece.resourcePath()).ifPresent(meta ->
              finalizeService.finalizePiece(world, placedPiece.anchor(), rotation, mirror, meta, random)
      );
    }
  }

  private void ensureChunksLoaded(World world, Location anchor, int[] size, StructureRotation rotation) {
    StructureFootprint footprint = StructureFootprint.fromSize(JigsawMath.toSizeVector(size), rotation);
    loadChunks(world, anchor.getBlockX(), anchor.getBlockZ(), footprint.sizeX(), footprint.sizeZ());
  }

  private void ensureChunksLoaded(World world, Bounds bounds) {
    loadChunks(world, bounds.minX(), bounds.minZ(), bounds.maxX() - bounds.minX() + 1, bounds.maxZ() - bounds.minZ() + 1);
  }

  private void loadChunks(World world, int minX, int minZ, int sizeX, int sizeZ) {
    int maxX = minX + sizeX + 4;
    int maxZ = minZ + sizeZ + 4;
    int minCx = minX >> 4;
    int maxCx = maxX >> 4;
    int minCz = minZ >> 4;
    int maxCz = maxZ >> 4;
    for (int cx = minCx; cx <= maxCx; cx++) {
      for (int cz = minCz; cz <= maxCz; cz++) {
        world.getChunkAt(cx, cz).load(true);
      }
    }
  }

  private void enqueueOutgoing(
          ShipPieceMetadata pieceMeta,
          Location pieceAnchor,
          StructureRotation rotation,
          Mirror mirror,
          Queue<PendingConnection> queue
  ) {
    BlockVector pieceSize = JigsawMath.toSizeVector(pieceMeta.size());
    BlockVector anchorVec = new BlockVector(pieceAnchor.getBlockX(), pieceAnchor.getBlockY(), pieceAnchor.getBlockZ());
    for (JigsawConnector connector : pieceMeta.jigsaws()) {
      if (!connector.hasOutgoingPool()) {
        continue;
      }
      Vector sourceWorld = anchorVec.clone().add(JigsawMath.transformRelativePos(
              JigsawMath.toBlockVector(connector.pos()),
              pieceSize,
              mirror,
              rotation
      ));
      Vector facing = JigsawMath.rotateFacing(JigsawMath.orientationFacing(connector.orientation()), rotation, mirror);
      Vector connectingPos = sourceWorld.clone().add(facing);
      queue.add(new PendingConnection(connector, connectingPos));
    }
  }

  private StructurePiece pickMatchingPiece(
          List<StructurePiece> poolPieces,
          String structureId,
          String targetName,
          Set<String> placed,
          Random random
  ) {
    List<StructurePiece> candidates = new ArrayList<>();
    for (StructurePiece piece : poolPieces) {
      if (placed.contains(piece.resourcePath())) {
        continue;
      }
      ShipPieceMetadata pieceMeta = metadata.getPiece(structureId, piece.resourcePath()).orElse(null);
      if (pieceMeta == null) {
        continue;
      }
      if (findJigsawByName(pieceMeta, targetName) != null) {
        candidates.add(piece);
      }
    }
    if (candidates.isEmpty()) {
      return null;
    }
    return candidates.get(random.nextInt(candidates.size()));
  }

  private static JigsawConnector findJigsawByName(ShipPieceMetadata pieceMeta, String targetName) {
    if (targetName == null || targetName.isEmpty() || "minecraft:empty".equals(targetName)) {
      return null;
    }
    for (JigsawConnector connector : pieceMeta.jigsaws()) {
      if (targetName.equals(connector.name())) {
        return connector;
      }
    }
    return null;
  }

  private static String pieceBasename(String pieceFile) {
    int slash = pieceFile.lastIndexOf('/');
    return slash >= 0 ? pieceFile.substring(slash + 1) : pieceFile;
  }

  public record PlacedPiece(String resourcePath, Location anchor) {
  }

  private record PendingConnection(JigsawConnector connector, Vector connectingPos) {
  }

  public record AssemblyResult(
          boolean success,
          StructureFootprint footprint,
          String failureReason,
          List<PlacedPiece> placedPieces,
          Bounds bounds
  ) {
    public static AssemblyResult failed(String reason) {
      return new AssemblyResult(false, new StructureFootprint(8, 8, 8), reason, List.of(), new Bounds());
    }

    public static AssemblyResult ok(StructureFootprint footprint, List<PlacedPiece> placedPieces, Bounds bounds) {
      return new AssemblyResult(true, footprint, null, placedPieces, bounds);
    }
  }

  public static final class Bounds {
    private int minX = Integer.MAX_VALUE;
    private int minY = Integer.MAX_VALUE;
    private int minZ = Integer.MAX_VALUE;
    private int maxX = Integer.MIN_VALUE;
    private int maxY = Integer.MIN_VALUE;
    private int maxZ = Integer.MIN_VALUE;

    void include(Location anchor, int[] size, StructureRotation rotation) {
      StructureFootprint rotatedSize = StructureFootprint.fromSize(JigsawMath.toSizeVector(size), rotation);
      int x1 = anchor.getBlockX();
      int y1 = anchor.getBlockY();
      int z1 = anchor.getBlockZ();
      int x2 = x1 + rotatedSize.sizeX() - 1;
      int y2 = y1 + rotatedSize.sizeY() - 1;
      int z2 = z1 + rotatedSize.sizeZ() - 1;
      minX = Math.min(minX, Math.min(x1, x2));
      minY = Math.min(minY, Math.min(y1, y2));
      minZ = Math.min(minZ, Math.min(z1, z2));
      maxX = Math.max(maxX, Math.max(x1, x2));
      maxY = Math.max(maxY, Math.max(y1, y2));
      maxZ = Math.max(maxZ, Math.max(z1, z2));
    }

    int minX() {
      return minX;
    }

    int minY() {
      return minY;
    }

    int minZ() {
      return minZ;
    }

    int maxX() {
      return maxX;
    }

    int maxY() {
      return maxY;
    }

    int maxZ() {
      return maxZ;
    }

    StructureFootprint toFootprint(StructureRotation rotation) {
      if (minX == Integer.MAX_VALUE) {
        return new StructureFootprint(8, 8, 8);
      }
      int sizeX = maxX - minX + 1;
      int sizeY = maxY - minY + 1;
      int sizeZ = maxZ - minZ + 1;
      if (rotation == StructureRotation.CLOCKWISE_90 || rotation == StructureRotation.COUNTERCLOCKWISE_90) {
        return new StructureFootprint(sizeZ, sizeY, sizeX);
      }
      return new StructureFootprint(sizeX, sizeY, sizeZ);
    }
  }
}
