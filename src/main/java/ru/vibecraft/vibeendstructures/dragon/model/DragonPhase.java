package ru.vibecraft.vibeendstructures.dragon.model;

import java.util.List;

public record DragonPhase(
    double threshold,
    List<DragonAbility> abilities
) {
}