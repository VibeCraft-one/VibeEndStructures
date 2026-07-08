package ru.vibecraft.vibeendstructures.dragon.runtime;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.DragonBattle;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.vibecraft.vibeendstructures.dragon.model.DragonArena;
import ru.vibecraft.vibeendstructures.dragon.model.DragonDefinition;
import ru.vibecraft.vibeendstructures.dragon.model.DragonType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class DragonSpawner {

    private static final int RITUAL_TICKS = 140;

    private final JavaPlugin plugin;
    private final DragonKeys keys;

    public DragonSpawner(JavaPlugin plugin, DragonKeys keys) {
        this.plugin = plugin;
        this.keys = keys;
    }

    public DragonSpawnRitual spawnWithRitual(World world, DragonArena arena, DragonDefinition definition, boolean eggDropEligible, boolean scheduledSpawn, Consumer<EnderDragon> callback) {
        suppressVanillaBattle(world);
        Location center = endPortalCenter(world, arena);
        List<EnderCrystal> crystals = spawnRitualCrystals(world, center, arena);
        Particle primaryParticle = ritualParticle(definition);

        BukkitTask particles = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            DragonParticles.spawn(plugin, world, Particle.PORTAL, center.clone().add(0, 2.5, 0), 90, 5.0, 2.5, 5.0, 0.2);
            DragonParticles.spawn(plugin, world, primaryParticle, center.clone().add(0, 3.5, 0), 45, 4.0, 2.2, 4.0, 0.04);
            DragonParticles.spawn(plugin, world, Particle.END_ROD, center.clone().add(0, 5.0, 0), 25, 2.0, 2.0, 2.0, 0.04);
            spawnRitualRing(world, center, primaryParticle, 5.5, 1.2);
            for (EnderCrystal crystal : crystals) {
                if (crystal.isValid()) {
                    crystal.setBeamTarget(center.clone().add(0, 7.0, 0));
                }
            }
        }, 0L, 10L);

        world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.HOSTILE, 3.0f, 0.7f);
        world.playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 4.0f, 0.65f);

        BukkitTask crescendoTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (int i = 0; i < 3; i++) {
                double pulse = 4.0 + i * 1.5;
                spawnRitualRing(world, center, primaryParticle, pulse, 1.6 + i * 0.8);
            }
            DragonParticles.spawn(plugin, world, Particle.FLASH, center.clone().add(0, 3.0, 0), 3);
            world.playSound(center, Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.HOSTILE, 3.0f, 0.55f);
            world.playSound(center, Sound.ENTITY_ENDER_DRAGON_FLAP, SoundCategory.HOSTILE, 3.5f, 0.55f);
        }, Math.max(20L, RITUAL_TICKS - 40L));

        BukkitTask spawnTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            particles.cancel();
            removeCrystals(crystals);
            suppressVanillaBattle(world);
            playSummonBurst(world, center, primaryParticle);
            EnderDragon dragon = spawn(world, arena, definition, eggDropEligible, scheduledSpawn);
            callback.accept(dragon);
        }, RITUAL_TICKS);

        return new DragonSpawnRitual(crystals, List.of(particles, crescendoTask, spawnTask));
    }

    public EnderDragon spawn(World world, DragonArena arena, DragonDefinition definition, boolean eggDropEligible, boolean scheduledSpawn) {
        suppressVanillaBattle(world);
        Location spawn = spawnLocation(world, arena);
        return world.spawn(spawn, EnderDragon.class, CreatureSpawnEvent.SpawnReason.CUSTOM, dragon -> {
            configureDragon(dragon, arena, definition, eggDropEligible, scheduledSpawn);
        });
    }

    private void playSummonBurst(World world, Location center, Particle primaryParticle) {
        DragonParticles.spawn(plugin, world, Particle.EXPLOSION_EMITTER, center.clone().add(0, 2.5, 0), 1);
        DragonParticles.spawn(plugin, world, Particle.END_ROD, center.clone().add(0, 4.0, 0), 120, 3.5, 3.0, 3.5, 0.12);
        DragonParticles.spawn(plugin, world, primaryParticle, center.clone().add(0, 3.0, 0), 80, 4.0, 2.5, 4.0, 0.08);
        DragonParticles.spawn(plugin, world, Particle.PORTAL, center.clone().add(0, 2.0, 0), 140, 6.0, 3.0, 6.0, 0.6);
        spawnRitualRing(world, center, primaryParticle, 7.0, 2.0);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 3.0f, 0.75f);
        world.playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 5.0f, 0.8f);
        world.playSound(center, Sound.ENTITY_ENDER_DRAGON_FLAP, SoundCategory.HOSTILE, 4.0f, 0.65f);
    }

    private List<EnderCrystal> spawnRitualCrystals(World world, Location center, DragonArena arena) {
        List<EnderCrystal> crystals = new ArrayList<>();
        double[][] offsets = {
                {3, 0},
                {-3, 0},
                {0, 3},
                {0, -3}
        };
        for (double[] offset : offsets) {
            Location location = crystalLocation(world, center, offset[0], offset[1]);
            EnderCrystal crystal = world.spawn(location, EnderCrystal.class, entity -> {
                entity.setShowingBottom(false);
                entity.setInvulnerable(true);
                entity.setBeamTarget(center.clone().add(0, 7.0, 0));
                entity.addScoreboardTag("vibedragon:ritual");
                entity.addScoreboardTag("vibedragon:ritual:" + arena.id());
            });
            crystals.add(crystal);
        }
        return crystals;
    }

    private Location spawnLocation(World world, DragonArena arena) {
        return new Location(world, arena.centerX() + 0.5, arena.height(), arena.centerZ() + 0.5);
    }

    private Location endPortalCenter(World world, DragonArena arena) {
        DragonBattle battle = world.getEnderDragonBattle();
        if (battle != null && battle.getEndPortalLocation() != null) {
            Location portal = battle.getEndPortalLocation();
            return new Location(world, portal.getBlockX() + 0.5, portal.getBlockY() + 1.0, portal.getBlockZ() + 0.5);
        }
        int y = findPortalTopY(world, arena.centerX(), arena.centerZ());
        return new Location(world, arena.centerX() + 0.5, y + 1.0, arena.centerZ() + 0.5);
    }

    private Location crystalLocation(World world, Location center, double offsetX, double offsetZ) {
        int x = center.getBlockX() + (int) offsetX;
        int z = center.getBlockZ() + (int) offsetZ;
        int y = findPortalTopY(world, x, z) + 1;
        return new Location(world, x + 0.5, y, z + 0.5);
    }

    private int findPortalTopY(World world, int x, int z) {
        int max = Math.min(world.getMaxHeight() - 1, 128);
        for (int y = max; y >= world.getMinHeight(); y--) {
            Material type = world.getBlockAt(x, y, z).getType();
            if (type == Material.BEDROCK || type == Material.END_PORTAL || type == Material.OBSIDIAN) {
                return y;
            }
        }
        return 64;
    }

    private void configureDragon(EnderDragon dragon, DragonArena arena, DragonDefinition definition, boolean eggDropEligible, boolean scheduledSpawn) {
        Location spawn = spawnLocation(dragon.getWorld(), arena);
        dragon.customName(Component.text(definition.displayName()));
        dragon.setCustomNameVisible(true);
        dragon.setPersistent(true);
        dragon.setRemoveWhenFarAway(false);
        dragon.setPodium(spawn);
        dragon.setPhase(EnderDragon.Phase.CIRCLING);

        setAttribute(dragon, Attribute.MAX_HEALTH, definition.health());
        setAttribute(dragon, Attribute.ATTACK_DAMAGE, definition.damage());
        setAttribute(dragon, Attribute.ARMOR, definition.armor());
        setAttribute(dragon, Attribute.KNOCKBACK_RESISTANCE, definition.knockbackResistance());
        setAttribute(dragon, Attribute.FOLLOW_RANGE, definition.followRange());
        setAttribute(dragon, Attribute.MOVEMENT_SPEED, definition.movementSpeed());
        setAttribute(dragon, Attribute.FLYING_SPEED, definition.flyingSpeed());
        AttributeInstance maxHealth = dragon.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            dragon.setHealth(Math.min(definition.health(), maxHealth.getValue()));
        }

        var data = dragon.getPersistentDataContainer();
        data.set(keys.dragonId(), PersistentDataType.STRING, definition.id());
        data.set(keys.dragonType(), PersistentDataType.STRING, definition.type().name());
        data.set(keys.arenaId(), PersistentDataType.STRING, arena.id());
        data.set(keys.eggDropEligible(), PersistentDataType.BYTE, (byte) (eggDropEligible ? 1 : 0));

        dragon.addScoreboardTag("vibedragon");
        dragon.addScoreboardTag("vibedragon:type:" + definition.id());
        dragon.addScoreboardTag("vibedragon:arena:" + arena.id());
        if (!eggDropEligible) {
            dragon.addScoreboardTag("vibedragon:summoned_by_egg");
        }
        if (scheduledSpawn) {
            dragon.addScoreboardTag("vibedragon:scheduled_spawn");
        }

        suppressVanillaBattle(dragon.getWorld());
    }

    /**
     * Keeps the End DragonBattle from owning the fight, regenerating portal/egg or
     * starting a vanilla respawn after our custom dragon dies.
     */
    public void suppressVanillaBattle(World world) {
        if (world == null) {
            return;
        }
        DragonBattle battle = world.getEnderDragonBattle();
        if (battle == null) {
            return;
        }
        battle.setPreviouslyKilled(true);
        if (battle.getRespawnPhase() != DragonBattle.RespawnPhase.NONE) {
            battle.setRespawnPhase(DragonBattle.RespawnPhase.NONE);
        }
        if (battle.getBossBar() != null) {
            battle.getBossBar().removeAll();
            battle.getBossBar().setVisible(false);
        }
    }

    private void removeCrystals(List<EnderCrystal> crystals) {
        for (EnderCrystal crystal : crystals) {
            if (crystal.isValid()) {
                crystal.remove();
            }
        }
    }

    private Particle ritualParticle(DragonDefinition definition) {
        if (definition.type() == DragonType.FIRE) {
            return Particle.FLAME;
        }
        if (definition.type() == DragonType.ICE) {
            return Particle.SNOWFLAKE;
        }
        return Particle.DRAGON_BREATH;
    }

    private void spawnRitualRing(World world, Location center, Particle particle, double radius, double yOffset) {
        for (int i = 0; i < 28; i++) {
            double angle = Math.PI * 2 * i / 28.0;
            Location point = center.clone().add(Math.cos(angle) * radius, yOffset, Math.sin(angle) * radius);
            DragonParticles.spawn(plugin, world, particle, point, 2, 0.08, 0.08, 0.08, 0.01);
        }
    }

    private void setAttribute(EnderDragon dragon, Attribute attribute, double value) {
        AttributeInstance instance = dragon.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }
}
