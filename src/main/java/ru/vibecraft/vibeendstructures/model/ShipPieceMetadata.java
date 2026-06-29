package ru.vibecraft.vibeendstructures.model;

import java.util.List;

public record ShipPieceMetadata(
        int[] size,
        List<JigsawConnector> jigsaws,
        List<int[]> technicalBlocks,
        List<LootContainer> lootContainers
) {
    public ShipPieceMetadata(int[] size, List<JigsawConnector> jigsaws) {
        this(size, jigsaws, List.of(), List.of());
    }
}
