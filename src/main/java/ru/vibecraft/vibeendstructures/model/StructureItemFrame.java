package ru.vibecraft.vibeendstructures.model;

import java.util.List;

public record StructureItemFrame(
        int[] pos,
        String type,
        String facing,
        float yaw,
        boolean invisible,
        LootItem item
) {
    public record LootItem(String id, int count) {
    }
}
