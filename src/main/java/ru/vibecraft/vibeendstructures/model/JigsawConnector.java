package ru.vibecraft.vibeendstructures.model;

public record JigsawConnector(
        int[] pos,
        String orientation,
        String name,
        String target,
        String pool,
        String joint
) {
    public boolean hasOutgoingPool() {
        return pool != null && !pool.isEmpty() && !"minecraft:empty".equals(pool);
    }
}
