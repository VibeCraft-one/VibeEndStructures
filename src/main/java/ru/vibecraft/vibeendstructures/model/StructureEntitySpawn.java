package ru.vibecraft.vibeendstructures.model;

public record StructureEntitySpawn(
        String type,
        double[] pos,
        float yaw,
        float pitch
) {
}
