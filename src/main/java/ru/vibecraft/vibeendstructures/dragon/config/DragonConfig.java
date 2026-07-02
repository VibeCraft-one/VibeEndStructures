package ru.vibecraft.vibeendstructures.dragon.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.vibecraft.vibeendstructures.dragon.model.DragonAbility;
import ru.vibecraft.vibeendstructures.dragon.model.DragonArena;
import ru.vibecraft.vibeendstructures.dragon.model.DragonDefinition;
import ru.vibecraft.vibeendstructures.dragon.model.DragonPhase;
import ru.vibecraft.vibeendstructures.dragon.model.DragonType;
import ru.vibecraft.vibeendstructures.dragon.model.GeneralDragonConfig;
import ru.vibecraft.vibeendstructures.dragon.model.RewardTier;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public final class DragonConfig {

    private final File configFile;
    private final Logger logger;

    private Map<String, DragonDefinition> dragonDefinitions = new HashMap<>();
    private Map<String, DragonArena> arenas = new HashMap<>();
    private GeneralDragonConfig generalConfig;

    public DragonConfig(File dataFolder, Logger logger) {
        this.configFile = new File(dataFolder, "dragons.yml");
        this.logger = logger;
    }

    public void load() {
        if (!configFile.exists()) {
            saveDefaultConfig();
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);

        loadDragons(yaml);
        loadArenas(yaml);
        loadGeneral(yaml);

        logger.info("Loaded " + dragonDefinitions.size() + " dragon types and " + arenas.size() + " arenas");
    }

    private void loadDragons(YamlConfiguration yaml) {
        ConfigurationSection dragonsSection = yaml.getConfigurationSection("dragons");
        if (dragonsSection == null) {
            return;
        }

        for (String key : dragonsSection.getKeys(false)) {
            ConfigurationSection ds = dragonsSection.getConfigurationSection(key);
            if (ds == null) continue;

            String id = ds.getString("id", key);
            String displayName = ds.getString("display-name", id);
            DragonType type = DragonType.valueOf(ds.getString("type", "NORMAL"));
            boolean enabled = ds.getBoolean("enabled", true);
            double health = ds.getDouble("health", 200);
            double damage = ds.getDouble("damage", 10);
            double armor = ds.getDouble("armor", 0);
            double knockbackResistance = ds.getDouble("knockback-resistance", 1);
            int followRange = ds.getInt("follow-range", 100);
            double movementSpeed = ds.getDouble("movement-speed", 0.25);
            double flyingSpeed = ds.getDouble("flying-speed", 0.4);

            List<DragonAbility> abilities = parseAbilities(ds.getStringList("abilities"));

            List<DragonPhase> phases = new ArrayList<>();
            List<?> rawPhases = ds.getList("phases");
            if (rawPhases != null) {
                for (Object obj : rawPhases) {
                    if (obj instanceof ConfigurationSection ps) {
                        double threshold = ps.getDouble("threshold", 0);
                        List<DragonAbility> phaseAbilities = parseAbilities(ps.getStringList("abilities"));
                        phases.add(new DragonPhase(threshold, phaseAbilities));
                    }
                }
            }

            double eggDropChance = ds.getDouble("egg-drop-chance", 0.1);

            List<RewardTier> rewardTiers = new ArrayList<>();
            List<?> rawTiers = ds.getList("reward-tiers");
            if (rawTiers != null) {
                for (Object obj : rawTiers) {
                    if (obj instanceof ConfigurationSection ts) {
                        double minContribution = ts.getDouble("min-contribution", 0);
                        String lootTable = ts.getString("loot-table", "");
                        List<String> commands = ts.getStringList("commands");
                        rewardTiers.add(new RewardTier(minContribution, lootTable, commands));
                    }
                }
            }

            List<String> titles = ds.getStringList("titles");
            List<String> prefixes = ds.getStringList("prefixes");

            DragonDefinition def = new DragonDefinition(
                id, displayName, type, enabled, health, damage, armor, knockbackResistance,
                followRange, movementSpeed, flyingSpeed, abilities, phases, eggDropChance,
                rewardTiers, titles, prefixes
            );

            dragonDefinitions.put(id, def);
        }
    }

    private List<DragonAbility> parseAbilities(List<String> abilityStrings) {
        List<DragonAbility> result = new ArrayList<>();
        if (abilityStrings == null) return result;

        for (String s : abilityStrings) {
            try {
                result.add(DragonAbility.valueOf(s.toUpperCase()));
            } catch (IllegalArgumentException e) {
                logger.warning("Unknown dragon ability: " + s);
            }
        }
        return result;
    }

    private void loadArenas(YamlConfiguration yaml) {
        ConfigurationSection arenasSection = yaml.getConfigurationSection("arenas");
        if (arenasSection == null) {
            return;
        }

        for (String key : arenasSection.getKeys(false)) {
            ConfigurationSection as = arenasSection.getConfigurationSection(key);
            if (as == null) continue;

            String id = as.getString("id", key);
            String name = as.getString("name", id);
            String dragonTypeId = as.getString("dragon-type", "");
            int centerX = as.getInt("center-x", 0);
            int centerZ = as.getInt("center-z", 0);
            int radius = as.getInt("radius", 100);
            int height = as.getInt("height", 80);
            long cooldownHours = as.getLong("cooldown-hours", 24);
            String spawnStructure = as.getString("spawn-structure", "");
            boolean enabled = as.getBoolean("enabled", true);

            DragonArena arena = new DragonArena(
                id, name, dragonTypeId, centerX, centerZ, radius, height,
                cooldownHours, spawnStructure, enabled, null, 0, DragonArena.ArenaState.IDLE
            );

            arenas.put(id, arena);
        }
    }

    private void loadGeneral(YamlConfiguration yaml) {
        ConfigurationSection general = yaml.getConfigurationSection("general");
        if (general == null) {
            generalConfig = new GeneralDragonConfig(
                1000, 0.001, 0.01, "PURPLE", "PROGRESS", true, true,
                "&d&lДракон %dragon% &r&7побежден! Топ-урон: %top_player% (%top_dmg%)",
                "&d&lДракон %dragon% &r&7пробудился на арене &b%arena%&7!"
            );
            return;
        }

        generalConfig = new GeneralDragonConfig(
            general.getInt("min-distance-from-origin", 1000),
            general.getDouble("contribution-decay-per-second", 0.001),
            general.getDouble("min-contribution-for-reward", 0.01),
            general.getString("boss-bar-color", "PURPLE"),
            general.getString("boss-bar-style", "PROGRESS"),
            general.getBoolean("announce-spawn", true),
            general.getBoolean("announce-death", true),
            general.getString("death-message", "&d&lДракон %dragon% &r&7побежден! Топ-урон: %top_player% (%top_dmg%)"),
            general.getString("spawn-message", "&d&lДракон %dragon% &r&7пробудился на арене &b%arena%&7!")
        );
    }

    private void saveDefaultConfig() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("dragons.yml")) {
            if (in != null) {
                Files.copy(in, configFile.toPath());
            }
        } catch (IOException e) {
            logger.severe("Failed to save default dragons.yml: " + e.getMessage());
        }
    }

    public Map<String, DragonDefinition> getDragonDefinitions() {
        return dragonDefinitions;
    }

    public DragonDefinition getDragon(String id) {
        return dragonDefinitions.get(id);
    }

    public Map<String, DragonArena> getArenas() {
        return arenas;
    }

    public DragonArena getArena(String id) {
        return arenas.get(id);
    }

    public GeneralDragonConfig getGeneralConfig() {
        return generalConfig;
    }

    public void saveArenas() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);

        for (DragonArena arena : arenas.values()) {
            String base = "arenas." + arena.id() + ".";
            yaml.set(base + "cooldown-until", arena.cooldownUntil());
            yaml.set(base + "state", arena.state().name());
            yaml.set(base + "active-dragon-uuid", arena.activeDragonUuid() != null ? arena.activeDragonUuid().toString() : null);
        }

        try {
            yaml.save(configFile);
        } catch (IOException e) {
            logger.severe("Failed to save dragons.yml: " + e.getMessage());
        }
    }

    public void updateArena(DragonArena arena) {
        arenas.put(arena.id(), arena);
        saveArenas();
    }
}