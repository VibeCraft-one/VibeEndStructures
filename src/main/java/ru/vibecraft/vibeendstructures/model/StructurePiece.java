package ru.vibecraft.vibeendstructures.model;

public record StructurePiece(
        String resourcePath,
        int weight,
        String pool,
        String location
) {
    public StructurePiece(String resourcePath, int weight) {
        this(resourcePath, weight, "", "");
    }
}
