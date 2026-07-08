package ru.vibecraft.vibeendstructures.dragon.runtime;

import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.HologramManager;
import de.oliver.fancyholograms.api.data.BlockHologramData;
import de.oliver.fancyholograms.api.hologram.Hologram;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import ru.vibecraft.vibeendstructures.VibeEndStructuresPlugin;

import java.util.UUID;

/**
 * Temporary 3x3 meteor visuals. Prefers FancyHolograms block holograms; falls back to one scaled BlockDisplay.
 */
public final class DragonMeteorHolograms {

    private static final Vector3f CUBE_SCALE = new Vector3f(3f, 3f, 3f);
    /** Centers a scaled 1x1 block model into the hologram location. */
    private static final Vector3f CUBE_TRANSLATION = new Vector3f(-1.5f, -1.5f, -1.5f);

    private DragonMeteorHolograms() {
    }

    public static boolean isFancyAvailable() {
        return FancyHologramsPlugin.isEnabled();
    }

    public static MeteorVisual spawnCube(VibeEndStructuresPlugin plugin, Location center, Material block) {
        if (center == null || center.getWorld() == null) {
            return null;
        }
        if (isFancyAvailable()) {
            return spawnFancyCube(center, block);
        }
        return spawnDisplayFallback(plugin, center, block);
    }

    public static void move(MeteorVisual visual, Location center) {
        if (visual == null || center == null) {
            return;
        }
        visual.move(center);
    }

    public static void remove(MeteorVisual visual) {
        if (visual == null) {
            return;
        }
        visual.remove();
    }

    private static MeteorVisual spawnFancyCube(Location center, Material block) {
        String name = "vibedragon_meteor_" + UUID.randomUUID().toString().replace("-", "");
        BlockHologramData data = new BlockHologramData(name, center.clone());
        data.setBlock(block);
        data.setBillboard(Display.Billboard.FIXED);
        data.setScale(CUBE_SCALE);
        data.setTranslation(CUBE_TRANSLATION);
        data.setPersistent(false);
        data.setInterpolationDuration(1);
        data.setVisibilityDistance(96);

        HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
        Hologram hologram = manager.create(data);
        hologram.getData().setPersistent(false);
        manager.addHologram(hologram);
        hologram.createHologram();
        World world = center.getWorld();
        if (world != null) {
            for (Player player : world.getPlayers()) {
                hologram.showHologram(player);
            }
        }
        return new FancyMeteorVisual(hologram, manager, name);
    }

    private static MeteorVisual spawnDisplayFallback(VibeEndStructuresPlugin plugin, Location center, Material block) {
        World world = center.getWorld();
        if (world == null) {
            return null;
        }
        BlockDisplay display = world.spawn(center, BlockDisplay.class, entity -> {
            entity.setBlock(block.createBlockData());
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setInterpolationDelay(0);
            entity.setInterpolationDuration(1);
            entity.setTransformation(new Transformation(
                    CUBE_TRANSLATION,
                    new AxisAngle4f(0f, 0f, 0f, 1f),
                    CUBE_SCALE,
                    new AxisAngle4f(0f, 0f, 0f, 1f)
            ));
            entity.addScoreboardTag("vibedragon:meteor");
        });
        return new DisplayMeteorVisual(display);
    }

    public sealed interface MeteorVisual permits FancyMeteorVisual, DisplayMeteorVisual {
        void move(Location center);

        void remove();
    }

    private static final class FancyMeteorVisual implements MeteorVisual {
        private final Hologram hologram;
        private final HologramManager manager;
        private final String name;
        private boolean removed;

        private FancyMeteorVisual(Hologram hologram, HologramManager manager, String name) {
            this.hologram = hologram;
            this.manager = manager;
            this.name = name;
        }

        @Override
        public void move(Location center) {
            if (removed) {
                return;
            }
            hologram.getData().setLocation(center.clone());
            Display display = hologram.getDisplayEntity();
            if (display != null && display.isValid()) {
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(1);
                display.teleport(center);
                return;
            }
            hologram.queueUpdate();
        }

        @Override
        public void remove() {
            if (removed) {
                return;
            }
            removed = true;
            World world = hologram.getData().getLocation().getWorld();
            if (world != null) {
                for (Player player : world.getPlayers()) {
                    hologram.forceHideHologram(player);
                }
            }
            try {
                hologram.deleteHologram();
            } catch (Exception ignored) {
            }
            try {
                manager.removeHologram(hologram);
            } catch (Exception ignored) {
            }
            manager.getHologram(name).ifPresent(leftover -> {
                try {
                    leftover.deleteHologram();
                    manager.removeHologram(leftover);
                } catch (Exception ignored) {
                }
            });
        }
    }

    private static final class DisplayMeteorVisual implements MeteorVisual {
        private final BlockDisplay display;

        private DisplayMeteorVisual(BlockDisplay display) {
            this.display = display;
        }

        @Override
        public void move(Location center) {
            if (display.isValid()) {
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(1);
                display.teleport(center);
            }
        }

        @Override
        public void remove() {
            if (display.isValid()) {
                display.remove();
            }
        }
    }
}
