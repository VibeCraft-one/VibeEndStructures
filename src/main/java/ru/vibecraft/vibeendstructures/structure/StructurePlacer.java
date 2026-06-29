package ru.vibecraft.vibeendstructures.structure;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.structure.Structure;
import org.bukkit.util.BlockVector;
import ru.vibecraft.vibeendstructures.model.StructureDefinition;
import ru.vibecraft.vibeendstructures.model.StructureMeta;
import ru.vibecraft.vibeendstructures.model.StructurePiece;

import java.util.Random;
import java.util.function.Consumer;
import java.util.logging.Logger;

public final class StructurePlacer {

    private static final long GENERATION_SETTLE_TICKS = 5L;
    private static final long GENERATION_REFINE_TICKS = 4L;

    private final JavaPlugin plugin;
    private final StructureRegistry registry;
    private final JigsawMetadataRegistry jigsawMetadata;
    private final StructureMetaRegistry metaRegistry;
    private final StructureFinalizeService finalizeService;
    private final JigsawAssembler jigsawAssembler;
    private final Logger logger;

    public StructurePlacer(
            JavaPlugin plugin,
            StructureRegistry registry,
            JigsawMetadataRegistry jigsawMetadata,
            StructureMetaRegistry metaRegistry,
            StructureFinalizeService finalizeService,
            Logger logger
    ) {
        this.plugin = plugin;
        this.registry = registry;
        this.jigsawMetadata = jigsawMetadata;
        this.metaRegistry = metaRegistry;
        this.finalizeService = finalizeService;
        this.jigsawAssembler = new JigsawAssembler(registry, jigsawMetadata, metaRegistry, finalizeService, logger);
        this.logger = logger;
    }

    public PlacementResult place(World world, Block surface, StructureDefinition definition, Random random) {
        StructurePiece piece = registry.pickPiece(definition, random);
        StructureRotation rotation = ShipPlacement.rotationFor(definition, random);
        Mirror mirror = ShipPlacement.mirrorFor(definition, random);

        Location anchor = surface.getLocation();
        anchor.setY(surface.getY());
        return placeAt(world, anchor, definition, piece, rotation, mirror, random, false);
    }

    public void placeForGeneration(
            World world,
            Block surface,
            StructureDefinition definition,
            StructurePiece piece,
            StructureRotation rotation,
            Mirror mirror,
            Random random,
            Consumer<PlacementResult> onComplete
    ) {
        Location anchor = surface.getLocation();
        anchor.setY(surface.getY());

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            PlacementResult result = placeAt(world, anchor.clone(), definition, piece, rotation, mirror, random, true);
            if (!result.success()) {
                onComplete.accept(result);
                return;
            }

            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                refinePlacement(world, anchor.clone(), definition, piece, rotation, mirror, random, result);
                onComplete.accept(result);
            }, GENERATION_REFINE_TICKS);
        }, GENERATION_SETTLE_TICKS);
    }

    public PlacementResult placeAt(Location location, StructureDefinition definition, StructureRotation rotation, Mirror mirror) {
        return placeAt(worldFrom(location), location, definition, registry.pickPiece(definition, new Random()), rotation, mirror, new Random(), false);
    }

    private PlacementResult placeAt(
            World world,
            Location anchor,
            StructureDefinition definition,
            StructurePiece piece,
            StructureRotation rotation,
            Mirror mirror,
            Random random,
            boolean deferRefine
    ) {
        if (isMegaShip(definition)) {
            try {
                JigsawAssembler.AssemblyResult assembled = jigsawAssembler.assemble(
                        world, anchor, definition, piece, rotation, mirror, random
                );
                if (!assembled.success()) {
                    logger.warning("Jigsaw assembly failed for " + definition.id() + ": " + assembled.failureReason());
                    return new PlacementResult(null, null, false);
                }
                if (!deferRefine) {
                    jigsawAssembler.refine(
                            world,
                            definition,
                            assembled.placedPieces(),
                            rotation,
                            mirror,
                            random,
                            assembled.bounds()
                    );
                }
                return new PlacementResult(
                        anchor,
                        assembled.footprint(),
                        true,
                        assembled.placedPieces(),
                        assembled.bounds()
                );
            } catch (Exception ex) {
                logger.warning("Jigsaw assembly failed for " + definition.id() + ": " + ex.getMessage());
                return new PlacementResult(null, null, false);
            }
        }

        Structure structure = registry.getStructure(definition, piece).orElse(null);
        if (structure == null) {
            logger.warning("Missing structure NBT for " + definition.id() + "/" + piece.resourcePath());
            return new PlacementResult(null, null, false);
        }

        try {
            StructureBounds bounds = StructureBounds.fromAnchor(anchor, structure.getSize(), rotation, mirror);
            StructureFootprint footprint = bounds.footprint();
            StructureMeta meta = metaRegistry.get(piece.resourcePath()).orElse(StructureMeta.empty());

            StructureChunkLoader.withLoadedChunks(
                    world,
                    bounds.minX(), bounds.minY(), bounds.minZ(),
                    bounds.maxX(), bounds.maxY(), bounds.maxZ(),
                    () -> {
                        placeBlocksPass(structure, anchor, rotation, mirror, random);
                        placeBlocksPass(structure, anchor, rotation, mirror, random);
                        finalizeService.finalizePiece(world, anchor, rotation, mirror, meta, random);
                        finalizeService.sweepTechnicalBounds(
                                world,
                                bounds.minX(), bounds.minY(), bounds.minZ(),
                                bounds.maxX(), bounds.maxY(), bounds.maxZ()
                        );
                        finalizeService.removeDisallowedEntities(
                                world,
                                bounds.minX(), bounds.minY(), bounds.minZ(),
                                bounds.maxX(), bounds.maxY(), bounds.maxZ()
                        );
                    }
            );
            return new PlacementResult(anchor, footprint, true);
        } catch (Exception ex) {
            logger.warning("Structure place failed for " + definition.id() + ": " + ex.getMessage());
            return new PlacementResult(null, null, false);
        }
    }

    private void refinePlacement(
            World world,
            Location anchor,
            StructureDefinition definition,
            StructurePiece piece,
            StructureRotation rotation,
            Mirror mirror,
            Random random,
            PlacementResult result
    ) {
        if (isMegaShip(definition)) {
            if (result.jigsawBounds() != null && !result.jigsawPieces().isEmpty()) {
                jigsawAssembler.refine(
                        world,
                        definition,
                        result.jigsawPieces(),
                        rotation,
                        mirror,
                        random,
                        result.jigsawBounds()
                );
            }
            return;
        }

        Structure structure = registry.getStructure(definition, piece).orElse(null);
        if (structure == null) {
            return;
        }

        StructureBounds bounds = StructureBounds.fromAnchor(anchor, structure.getSize(), rotation, mirror);
        StructureMeta meta = metaRegistry.get(piece.resourcePath()).orElse(StructureMeta.empty());
        StructureChunkLoader.withLoadedChunks(
                world,
                bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ(),
                () -> {
                    placeBlocksPass(structure, anchor, rotation, mirror, random);
                    finalizeService.refinePiece(world, anchor, rotation, mirror, meta, random);
                    finalizeService.removeDisallowedEntities(
                            world,
                            bounds.minX(), bounds.minY(), bounds.minZ(),
                            bounds.maxX(), bounds.maxY(), bounds.maxZ()
                    );
                }
        );
    }

    public StructureFootprint previewFootprint(StructureDefinition definition, Random random) {
        if (isMegaShip(definition) && jigsawMetadata.hasMetadata(definition.id())) {
            return jigsawMetadata.estimateFootprint(definition.id());
        }

        StructurePiece piece = registry.pickPiece(definition, random);
        Structure structure = registry.getStructure(definition, piece).orElse(null);
        if (structure == null) {
            return new StructureFootprint(8, 8, 8);
        }
        BlockVector size = structure.getSize();
        return new StructureFootprint(size.getBlockX(), size.getBlockY(), size.getBlockZ());
    }

    private static void placeBlocksPass(
            Structure structure,
            Location anchor,
            StructureRotation rotation,
            Mirror mirror,
            Random random
    ) {
        StructurePlacementUtil.placeBlocks(structure, anchor, rotation, mirror, random);
    }

    private static boolean isMegaShip(StructureDefinition definition) {
        return ShipPlacement.isJigsawShip(definition);
    }

    private static World worldFrom(Location location) {
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalStateException("Location has no world");
        }
        return world;
    }
}
