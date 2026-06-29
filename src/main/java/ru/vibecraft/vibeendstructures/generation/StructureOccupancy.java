package ru.vibecraft.vibeendstructures.generation;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.vibecraft.vibeendstructures.structure.StructureFootprint;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class StructureOccupancy {

    private static final Set<Material> NATURAL_END_BLOCKS = EnumSet.of(
            Material.AIR,
            Material.CAVE_AIR,
            Material.VOID_AIR,
            Material.END_STONE,
            Material.END_STONE_BRICKS,
            Material.PURPUR_BLOCK,
            Material.PURPUR_PILLAR,
            Material.PURPUR_STAIRS,
            Material.PURPUR_SLAB,
            Material.CHORUS_PLANT,
            Material.CHORUS_FLOWER,
            Material.BEDROCK
    );

    private final JavaPlugin plugin;
    private final File file;
    private final List<PlacedStructure> placed = new ArrayList<>();

    public StructureOccupancy(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "placements.yml");
        load();
    }

    public boolean canPlace(World world, int anchorX, int anchorY, int anchorZ, StructureFootprint footprint, int minDistance) {
        int radius = Math.max(footprint.horizontalRadius(), minDistance / 2);

        for (PlacedStructure existing : placed) {
            if (!existing.world().equals(world.getName())) {
                continue;
            }
            double dx = existing.x() - anchorX;
            double dz = existing.z() - anchorZ;
            double required = existing.radius() + radius + 4;
            if (dx * dx + dz * dz < required * required) {
                return false;
            }
        }

        return isAreaNatural(world, anchorX, anchorY, anchorZ, footprint);
    }

    public void record(World world, int anchorX, int anchorY, int anchorZ, StructureFootprint footprint) {
        placed.add(new PlacedStructure(
                world.getName(),
                anchorX,
                anchorY,
                anchorZ,
                footprint.horizontalRadius()
        ));
        saveAsync();
    }

    private boolean isAreaNatural(World world, int anchorX, int anchorY, int anchorZ, StructureFootprint footprint) {
        int minX = anchorX;
        int maxX = anchorX + footprint.sizeX() - 1;
        int minZ = anchorZ;
        int maxZ = anchorZ + footprint.sizeZ() - 1;
        int minY = anchorY;
        int maxY = anchorY + footprint.sizeY() - 1;

        for (int x = minX; x <= maxX; x += 2) {
            for (int z = minZ; z <= maxZ; z += 2) {
                for (int y = minY; y <= maxY; y += 3) {
                    Block block = world.getBlockAt(x, y, z);
                    if (!NATURAL_END_BLOCKS.contains(block.getType())) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private void load() {
        placed.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        List<?> entries = yaml.getList("placements", List.of());
        for (Object entry : entries) {
            if (!(entry instanceof java.util.Map<?, ?> map)) {
                continue;
            }
            placed.add(new PlacedStructure(
                    String.valueOf(map.get("world")),
                    ((Number) map.get("x")).intValue(),
                    ((Number) map.get("y")).intValue(),
                    ((Number) map.get("z")).intValue(),
                    ((Number) map.get("radius")).intValue()
            ));
        }
    }

    private void saveAsync() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::save);
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        List<java.util.Map<String, Object>> entries = new ArrayList<>();
        for (PlacedStructure structure : placed) {
            entries.add(java.util.Map.of(
                    "world", structure.world(),
                    "x", structure.x(),
                    "y", structure.y(),
                    "z", structure.z(),
                    "radius", structure.radius()
            ));
        }
        yaml.set("placements", entries);
        try {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save placements: " + ex.getMessage());
        }
    }

    private record PlacedStructure(String world, int x, int y, int z, int radius) {
    }
}
