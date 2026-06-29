package ru.vibecraft.vibeendstructures.structure;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.structure.Structure;
import org.bukkit.structure.StructureManager;
import org.bukkit.plugin.java.JavaPlugin;
import ru.vibecraft.vibeendstructures.model.StructureDefinition;
import ru.vibecraft.vibeendstructures.model.StructurePiece;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.logging.Level;

public final class StructureRegistry {

    private final JavaPlugin plugin;
    private final Map<String, StructureDefinition> definitions = new HashMap<>();
    private final Map<String, Structure> loadedStructures = new HashMap<>();
    private final Map<String, Map<String, List<StructurePiece>>> poolIndex = new HashMap<>();

    public StructureRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        definitions.clear();
        loadedStructures.clear();
        poolIndex.clear();

        try (InputStream in = plugin.getResource("manifest.json")) {
            if (in == null) {
                throw new IllegalStateException("manifest.json not found in plugin jar");
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray structures = root.getAsJsonArray("structures");

            StructureManager manager = Bukkit.getStructureManager();
            int loaded = 0;

            for (JsonElement element : structures) {
                JsonObject obj = element.getAsJsonObject();
                String id = obj.get("id").getAsString();
                String category = obj.get("category").getAsString();

                JsonObject placement = obj.getAsJsonObject("placement");
                int spacing = intOrDefault(placement, "spacing", 32);
                int separation = intOrDefault(placement, "separation", 8);
                int salt = intOrDefault(placement, "salt", id.hashCode());
                Integer minDist = placement.has("min_distance_from_world_origin") && !placement.get("min_distance_from_world_origin").isJsonNull()
                        ? placement.get("min_distance_from_world_origin").getAsInt()
                        : null;

                int minY = 45;
                if (obj.has("y_allowance") && obj.get("y_allowance").isJsonObject()) {
                    minY = intOrDefault(obj.getAsJsonObject("y_allowance"), "min_y_allowed", 45);
                }

                int jigsawSize = intOrDefault(obj, "jigsaw_size", 1);

                List<StructurePiece> pieces = new ArrayList<>();
                for (JsonElement pieceEl : obj.getAsJsonArray("pieces")) {
                    JsonObject piece = pieceEl.getAsJsonObject();
                    String file = piece.get("file").getAsString();
                    String resourcePath = toResourcePath(file);
                    int weight = intOrDefault(piece, "weight", 1);
                    String pool = piece.has("pool") ? piece.get("pool").getAsString() : "";
                    String location = piece.has("location") ? piece.get("location").getAsString() : "";
                    pieces.add(new StructurePiece(resourcePath, weight, pool, location));
                }

                List<Map<String, String>> jigsawLinks = new ArrayList<>();
                if (obj.has("jigsaw_links")) {
                    for (JsonElement linkEl : obj.getAsJsonArray("jigsaw_links")) {
                        JsonObject link = linkEl.getAsJsonObject();
                        jigsawLinks.add(Map.of(
                                "from", link.get("from").getAsString(),
                                "to", link.get("to").getAsString()
                        ));
                    }
                }

                StructureDefinition definition = new StructureDefinition(
                        id, category, spacing, separation, salt, minDist, minY, jigsawSize, pieces, jigsawLinks
                );
                definitions.put(id, definition);

                Map<String, List<StructurePiece>> pools = new HashMap<>();
                for (StructurePiece piece : pieces) {
                    if (!piece.pool().isEmpty()) {
                        pools.computeIfAbsent(piece.pool(), ignored -> new ArrayList<>()).add(piece);
                    }
                }
                poolIndex.put(id, pools);

                for (StructurePiece piece : pieces) {
                    String key = id + "/" + piece.resourcePath();
                    if (loadedStructures.containsKey(key)) {
                        continue;
                    }
                    try (InputStream nbt = plugin.getResource("structures/" + piece.resourcePath())) {
                        if (nbt == null) {
                            plugin.getLogger().warning("Missing NBT: structures/" + piece.resourcePath());
                            continue;
                        }
                        Structure structure = manager.loadStructure(nbt);
                        NamespacedKey nsKey = new NamespacedKey(plugin, sanitizeKey(key));
                        manager.registerStructure(nsKey, structure);
                        loadedStructures.put(key, structure);
                        loaded++;
                    } catch (Exception ex) {
                        plugin.getLogger().log(Level.WARNING, "Failed to load structure piece " + key, ex);
                    }
                }
            }

            plugin.getLogger().info("Loaded " + definitions.size() + " structure types (" + loaded + " NBT pieces)");
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load structure manifest", ex);
        }
    }

    public Collection<StructureDefinition> getDefinitions() {
        return Collections.unmodifiableCollection(definitions.values());
    }

    public Optional<StructureDefinition> getDefinition(String id) {
        return Optional.ofNullable(definitions.get(id));
    }

    public Optional<Structure> getStructure(StructureDefinition definition, StructurePiece piece) {
        return Optional.ofNullable(loadedStructures.get(definition.id() + "/" + piece.resourcePath()));
    }

    public List<StructurePiece> getPiecesForPool(StructureDefinition definition, String pool) {
        Map<String, List<StructurePiece>> pools = poolIndex.get(definition.id());
        if (pools == null) {
            return List.of();
        }
        return pools.getOrDefault(pool, List.of());
    }

    public StructurePiece pickPiece(StructureDefinition definition, Random random) {
        List<StructurePiece> pieces = definition.pieces();
        if (pieces.isEmpty()) {
            throw new IllegalStateException("No pieces for " + definition.id());
        }
        if (pieces.size() == 1) {
            return pieces.getFirst();
        }

        if (definition.category().equals("mega_ship")) {
            for (StructurePiece piece : pieces) {
                String name = piece.resourcePath();
                if (name.equals(definition.id() + ".nbt")) {
                    return piece;
                }
            }
            for (StructurePiece piece : pieces) {
                String name = piece.resourcePath();
                if (!name.contains("_end") && !name.contains("_side") && !name.contains("_top") && !name.contains("_middle")) {
                    return piece;
                }
            }
        }

        int total = pieces.stream().mapToInt(StructurePiece::weight).sum();
        int roll = random.nextInt(total);
        int current = 0;
        for (StructurePiece piece : pieces) {
            current += piece.weight();
            if (roll < current) {
                return piece;
            }
        }
        return pieces.getLast();
    }

    private static String toResourcePath(String manifestFile) {
        if (manifestFile.startsWith("nbt/")) {
            return manifestFile.substring(4);
        }
        return manifestFile;
    }

    private static int intOrDefault(JsonObject obj, String key, int defaultValue) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsInt() : defaultValue;
    }

    private static String sanitizeKey(String key) {
        return key.toLowerCase().replace('/', '_').replace('.', '_');
    }

    public static StructureRotation randomRotation(Random random) {
        StructureRotation[] values = StructureRotation.values();
        return values[random.nextInt(values.length)];
    }

    public static Mirror randomMirror(Random random) {
        return random.nextBoolean() ? Mirror.NONE : Mirror.LEFT_RIGHT;
    }
}
