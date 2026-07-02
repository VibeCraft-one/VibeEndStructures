package ru.vibecraft.vibeendstructures.dragon.model;

import java.util.List;

public record RewardTier(
    double minContribution,
    String lootTable,
    List<String> commands
) {
}