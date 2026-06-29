package ru.vibecraft.vibeendstructures.structure;

import org.bukkit.Location;

import java.util.List;

public record PlacementResult(
        Location anchor,
        StructureFootprint footprint,
        boolean success,
        List<JigsawAssembler.PlacedPiece> jigsawPieces,
        JigsawAssembler.Bounds jigsawBounds
) {
    public PlacementResult(Location anchor, StructureFootprint footprint, boolean success) {
        this(anchor, footprint, success, List.of(), null);
    }
}
