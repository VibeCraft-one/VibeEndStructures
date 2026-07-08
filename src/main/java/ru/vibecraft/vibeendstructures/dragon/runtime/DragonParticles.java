package ru.vibecraft.vibeendstructures.dragon.runtime;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Safe particle spawning for Paper 1.21+.
 * Typed particles (FLASH needs {@link Color}, DRAGON_BREATH needs {@link Float}) throw if the
 * void/null overload is used. Prefer {@link Particle#builder()} so data is always valid.
 */
public final class DragonParticles {

    private static final Color DEFAULT_FLASH = Color.fromRGB(200, 170, 255);
    private static final float DEFAULT_DRAGON_BREATH_POWER = 1.0f;

    private DragonParticles() {
    }

    public static void spawn(JavaPlugin plugin, World world, Particle particle, Location location, int count,
                             double offsetX, double offsetY, double offsetZ, double extra) {
        if (world == null || location == null || location.getWorld() == null || particle == null || count < 0) {
            return;
        }
        try {
            Object data = resolveData(particle);
            if (data == SKIP) {
                if (plugin != null && plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().info("Skipped particle " + particle + " (unsupported data type "
                            + particle.getDataType().getSimpleName() + ")");
                }
                return;
            }
            particle.builder()
                    .location(location)
                    .count(count)
                    .offset(offsetX, offsetY, offsetZ)
                    .extra(extra)
                    .data(data)
                    .force(true)
                    .spawn();
        } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException ex) {
            if (plugin != null) {
                plugin.getLogger().warning("Failed to spawn particle " + particle + " at "
                        + format(location) + ": " + ex.getMessage());
            }
        }
    }

    public static void spawn(JavaPlugin plugin, World world, Particle particle, Location location, int count) {
        spawn(plugin, world, particle, location, count, 0.0, 0.0, 0.0, 0.0);
    }

    private static final Object SKIP = new Object();

    private static Object resolveData(Particle particle) {
        Class<?> dataType = particle.getDataType();
        if (dataType == Void.class) {
            return null;
        }
        if (dataType == Float.class) {
            return DEFAULT_DRAGON_BREATH_POWER;
        }
        if (dataType == Color.class) {
            return particle == Particle.FLASH ? DEFAULT_FLASH : Color.WHITE;
        }
        return SKIP;
    }

    private static String format(Location location) {
        return location.getWorld().getName()
                + " " + location.getBlockX()
                + " " + location.getBlockY()
                + " " + location.getBlockZ();
    }
}
