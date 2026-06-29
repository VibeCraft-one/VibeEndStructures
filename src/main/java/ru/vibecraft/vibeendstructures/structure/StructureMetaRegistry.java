package ru.vibecraft.vibeendstructures.structure;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.plugin.java.JavaPlugin;
import ru.vibecraft.vibeendstructures.model.LootContainer;
import ru.vibecraft.vibeendstructures.model.StructureEntitySpawn;
import ru.vibecraft.vibeendstructures.model.StructureItemFrame;
import ru.vibecraft.vibeendstructures.model.StructureMeta;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

public final class StructureMetaRegistry {

    private final JavaPlugin plugin;
    private final Map<String, StructureMeta> metadata = new HashMap<>();

    public StructureMetaRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        metadata.clear();
        try (InputStream in = plugin.getResource("structures-meta.json")) {
            if (in == null) {
                plugin.getLogger().warning("structures-meta.json not found");
                return;
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                metadata.put(entry.getKey(), parseMeta(entry.getValue().getAsJsonObject()));
            }
            plugin.getLogger().info("Loaded block meta for " + metadata.size() + " structure pieces");
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to load structures-meta.json", ex);
        }
    }

    public Optional<StructureMeta> get(String resourcePath) {
        StructureMeta meta = metadata.get(resourcePath);
        if (meta == null) {
            meta = metadata.get(pieceBasename(resourcePath));
        }
        return Optional.ofNullable(meta);
    }

    private static String pieceBasename(String resourcePath) {
        int slash = resourcePath.lastIndexOf('/');
        if (slash >= 0) {
            String dir = resourcePath.substring(0, slash);
            String file = resourcePath.substring(slash + 1);
            return dir + "/" + file;
        }
        return resourcePath;
    }

    private static StructureMeta parseMeta(JsonObject obj) {
        int[] size = jsonIntArray(obj.getAsJsonArray("size"), 3);
        return new StructureMeta(
                size,
                parsePositions(obj.getAsJsonArray("technical")),
                parseLoot(obj.getAsJsonArray("loot")),
                parseItemFrames(obj.getAsJsonArray("item_frames")),
                parseEntities(obj.getAsJsonArray("entities"))
        );
    }

    private static java.util.List<int[]> parsePositions(JsonArray array) {
        java.util.List<int[]> positions = new java.util.ArrayList<>();
        if (array == null) {
            return positions;
        }
        for (JsonElement element : array) {
            positions.add(jsonIntArray(element.getAsJsonArray(), 3));
        }
        return positions;
    }

    private static java.util.List<LootContainer> parseLoot(JsonArray array) {
        java.util.List<LootContainer> loot = new java.util.ArrayList<>();
        if (array == null) {
            return loot;
        }
        for (JsonElement element : array) {
            JsonObject obj = element.getAsJsonObject();
            loot.add(new LootContainer(
                    jsonIntArray(obj.getAsJsonArray("pos"), 3),
                    obj.get("loot").getAsString()
            ));
        }
        return loot;
    }

    private static java.util.List<StructureItemFrame> parseItemFrames(JsonArray array) {
        java.util.List<StructureItemFrame> frames = new java.util.ArrayList<>();
        if (array == null) {
            return frames;
        }
        for (JsonElement element : array) {
            JsonObject obj = element.getAsJsonObject();
            StructureItemFrame.LootItem item = null;
            if (obj.has("item") && obj.get("item").isJsonObject()) {
                JsonObject itemObj = obj.getAsJsonObject("item");
                item = new StructureItemFrame.LootItem(
                        itemObj.get("id").getAsString(),
                        itemObj.get("count").getAsInt()
                );
            }
            frames.add(new StructureItemFrame(
                    jsonIntArray(obj.getAsJsonArray("pos"), 3),
                    obj.get("type").getAsString(),
                    obj.get("facing").getAsString(),
                    obj.has("yaw") ? obj.get("yaw").getAsFloat() : 0f,
                    obj.has("invisible") && obj.get("invisible").getAsBoolean(),
                    item
            ));
        }
        return frames;
    }

    private static java.util.List<StructureEntitySpawn> parseEntities(JsonArray array) {
        java.util.List<StructureEntitySpawn> entities = new java.util.ArrayList<>();
        if (array == null) {
            return entities;
        }
        for (JsonElement element : array) {
            JsonObject obj = element.getAsJsonObject();
            JsonArray pos = obj.getAsJsonArray("pos");
            entities.add(new StructureEntitySpawn(
                    obj.get("type").getAsString(),
                    new double[]{pos.get(0).getAsDouble(), pos.get(1).getAsDouble(), pos.get(2).getAsDouble()},
                    obj.has("yaw") ? obj.get("yaw").getAsFloat() : 0f,
                    obj.has("pitch") ? obj.get("pitch").getAsFloat() : 0f
            ));
        }
        return entities;
    }

    private static int[] jsonIntArray(JsonArray array, int length) {
        int[] values = new int[length];
        for (int i = 0; i < length && i < array.size(); i++) {
            values[i] = array.get(i).getAsInt();
        }
        return values;
    }
}
