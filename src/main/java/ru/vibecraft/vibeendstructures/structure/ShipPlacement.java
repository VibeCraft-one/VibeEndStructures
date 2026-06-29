package ru.vibecraft.vibeendstructures.structure;

import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import ru.vibecraft.vibeendstructures.model.StructureDefinition;

import java.util.Random;

public final class ShipPlacement {

    private ShipPlacement() {
    }

    public static boolean isJigsawShip(StructureDefinition definition) {
        return "mega_ship".equals(definition.category()) && definition.pieces().size() > 1;
    }

    public static StructureRotation rotationFor(StructureDefinition definition, Random random) {
        if (isJigsawShip(definition)) {
            return StructureRotation.NONE;
        }
        return StructureRegistry.randomRotation(random);
    }

    public static Mirror mirrorFor(StructureDefinition definition, Random random) {
        if (isJigsawShip(definition)) {
            return Mirror.NONE;
        }
        return StructureRegistry.randomMirror(random);
    }
}
