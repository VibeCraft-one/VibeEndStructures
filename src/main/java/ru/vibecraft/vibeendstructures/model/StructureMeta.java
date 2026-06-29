package ru.vibecraft.vibeendstructures.model;

import java.util.List;

public record StructureMeta(
        int[] size,
        List<int[]> technicalBlocks,
        List<LootContainer> lootContainers,
        List<StructureItemFrame> itemFrames,
        List<StructureEntitySpawn> entities
) {
    public static StructureMeta empty() {
        return new StructureMeta(new int[]{0, 0, 0}, List.of(), List.of(), List.of(), List.of());
    }
}
