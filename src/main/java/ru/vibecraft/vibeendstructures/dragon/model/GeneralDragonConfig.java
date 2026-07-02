package ru.vibecraft.vibeendstructures.dragon.model;

public record GeneralDragonConfig(
    int minDistanceFromOrigin,
    double contributionDecayPerSecond,
    double minContributionForReward,
    String bossBarColor,
    String bossBarStyle,
    boolean announceSpawn,
    boolean announceDeath,
    String deathMessage,
    String spawnMessage
) {
}