package ru.vibecraft.vibeendstructures.dragon.model;

public record GeneralDragonConfig(
    int minDistanceFromOrigin,
    double contributionDecayPerSecond,
    double minContributionForReward,
    double scheduledEggDropChance,
    String bossBarColor,
    String bossBarStyle,
    boolean announceSpawn,
    boolean announceDeath,
    String deathMessage,
    String spawnMessage
) {
}