package ru.vibecraft.vibeendstructures.dragon.model;

import java.util.List;

public record DragonDefinition(
    String id,
    String displayName,
    DragonType type,
    boolean enabled,
    double health,
    double damage,
    double armor,
    double knockbackResistance,
    int followRange,
    double movementSpeed,
    double flyingSpeed,
    List<DragonAbility> abilities,
    List<DragonPhase> phases,
    double eggDropChance,
    List<RewardTier> rewardTiers,
    List<String> titles,
    List<String> prefixes
) {
}