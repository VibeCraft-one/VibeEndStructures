package ru.vibecraft.vibeendstructures.generation;

import ru.vibecraft.vibeendstructures.model.StructureDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class PlacementEngine {

    private PlacementEngine() {
    }

    public static boolean isSpawnChunk(long worldSeed, int chunkX, int chunkZ, int spacing, int separation, int salt) {
        int grid = Math.max(spacing, 1);
        int gap = Math.min(separation, grid - 1);

        int regionX = Math.floorDiv(chunkX, grid);
        int regionZ = Math.floorDiv(chunkZ, grid);

        long seed = worldSeed + salt;
        Random random = new Random(seed + regionX * 341873128712L + regionZ * 132897987541L);

        int offsetX = random.nextInt(Math.max(1, grid - gap));
        int offsetZ = random.nextInt(Math.max(1, grid - gap));

        return chunkX == regionX * grid + offsetX && chunkZ == regionZ * grid + offsetZ;
    }

    public static StructureDefinition pickStructure(List<StructureDefinition> enabled, long worldSeed, int chunkX, int chunkZ, int salt) {
        if (enabled.isEmpty()) {
            return null;
        }
        Random random = new Random(worldSeed + salt + chunkX * 92837111L + chunkZ * 192883731L);
        return enabled.get(random.nextInt(enabled.size()));
    }

    public static List<StructureDefinition> filterEnabled(Iterable<StructureDefinition> definitions, StructureFilter filter) {
        List<StructureDefinition> enabled = new ArrayList<>();
        for (StructureDefinition definition : definitions) {
            if (filter.isEnabled(definition)) {
                enabled.add(definition);
            }
        }
        return enabled;
    }

    @FunctionalInterface
    public interface StructureFilter {
        boolean isEnabled(StructureDefinition definition);
    }
}
