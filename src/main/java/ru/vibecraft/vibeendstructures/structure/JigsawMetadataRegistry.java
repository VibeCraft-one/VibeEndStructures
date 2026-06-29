package ru.vibecraft.vibeendstructures.structure;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.plugin.java.JavaPlugin;
import ru.vibecraft.vibeendstructures.model.JigsawConnector;
import ru.vibecraft.vibeendstructures.model.LootContainer;
import ru.vibecraft.vibeendstructures.model.ShipPieceMetadata;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

public final class JigsawMetadataRegistry {

  private final JavaPlugin plugin;
  private final Map<String, Map<String, ShipPieceMetadata>> shipMetadata = new HashMap<>();

  public JigsawMetadataRegistry(JavaPlugin plugin) {
    this.plugin = plugin;
  }

  public void load() {
    shipMetadata.clear();
    Map<String, Map<String, PieceExtras>> extras = loadShipExtras();
    try (InputStream in = plugin.getResource("jigsaw-ships.json")) {
      if (in == null) {
        plugin.getLogger().warning("jigsaw-ships.json not found — mega ships will spawn incomplete");
        return;
      }
      JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
      for (Map.Entry<String, JsonElement> structureEntry : root.entrySet()) {
        String structureId = structureEntry.getKey();
        JsonObject pieces = structureEntry.getValue().getAsJsonObject();
        Map<String, ShipPieceMetadata> pieceMap = new HashMap<>();
        for (Map.Entry<String, JsonElement> pieceEntry : pieces.entrySet()) {
          JsonObject pieceObj = pieceEntry.getValue().getAsJsonObject();
          int[] size = jsonIntArray(pieceObj.getAsJsonArray("size"), 3);
          List<JigsawConnector> jigsaws = new ArrayList<>();
          if (pieceObj.has("jigsaws")) {
            for (JsonElement jigEl : pieceObj.getAsJsonArray("jigsaws")) {
              JsonObject jig = jigEl.getAsJsonObject();
              jigsaws.add(new JigsawConnector(
                      jsonIntArray(jig.getAsJsonArray("pos"), 3),
                      jig.get("orientation").getAsString(),
                      jig.get("name").getAsString(),
                      jig.get("target").getAsString(),
                      jig.get("pool").getAsString(),
                      jig.get("joint").getAsString()
              ));
            }
          }
          PieceExtras pieceExtras = extras
                  .getOrDefault(structureId, Collections.emptyMap())
                  .getOrDefault(pieceEntry.getKey(), PieceExtras.EMPTY);
          pieceMap.put(pieceEntry.getKey(), new ShipPieceMetadata(
                  size,
                  jigsaws,
                  pieceExtras.technicalBlocks(),
                  pieceExtras.lootContainers()
          ));
        }
        shipMetadata.put(structureId, pieceMap);
      }
      plugin.getLogger().info("Loaded jigsaw metadata for " + shipMetadata.size() + " mega ship types");
    } catch (Exception ex) {
      plugin.getLogger().log(Level.WARNING, "Failed to load jigsaw-ships.json", ex);
    }
  }

  private Map<String, Map<String, PieceExtras>> loadShipExtras() {
    Map<String, Map<String, PieceExtras>> result = new HashMap<>();
    try (InputStream in = plugin.getResource("ships-meta.json")) {
      if (in == null) {
        return result;
      }
      JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
      for (Map.Entry<String, JsonElement> structureEntry : root.entrySet()) {
        JsonObject pieces = structureEntry.getValue().getAsJsonObject();
        Map<String, PieceExtras> pieceMap = new HashMap<>();
        for (Map.Entry<String, JsonElement> pieceEntry : pieces.entrySet()) {
          JsonObject pieceObj = pieceEntry.getValue().getAsJsonObject();
          List<int[]> technical = new ArrayList<>();
          if (pieceObj.has("technical")) {
            for (JsonElement posEl : pieceObj.getAsJsonArray("technical")) {
              technical.add(jsonIntArray(posEl.getAsJsonArray(), 3));
            }
          }
          List<LootContainer> loot = new ArrayList<>();
          if (pieceObj.has("loot")) {
            for (JsonElement lootEl : pieceObj.getAsJsonArray("loot")) {
              JsonObject obj = lootEl.getAsJsonObject();
              loot.add(new LootContainer(
                      jsonIntArray(obj.getAsJsonArray("pos"), 3),
                      obj.get("loot").getAsString()
              ));
            }
          }
          pieceMap.put(pieceEntry.getKey(), new PieceExtras(technical, loot));
        }
        result.put(structureEntry.getKey(), pieceMap);
      }
    } catch (Exception ex) {
      plugin.getLogger().log(Level.WARNING, "Failed to load ships-meta.json", ex);
    }
    return result;
  }

  public boolean hasMetadata(String structureId) {
    return shipMetadata.containsKey(structureId);
  }

  public Optional<ShipPieceMetadata> getPiece(String structureId, String pieceFile) {
    Map<String, ShipPieceMetadata> pieces = shipMetadata.get(structureId);
    if (pieces == null) {
      return Optional.empty();
    }
    ShipPieceMetadata meta = pieces.get(pieceFile);
    if (meta == null) {
      meta = pieces.get(pieceBasename(pieceFile));
    }
    return Optional.ofNullable(meta);
  }

  private static String pieceBasename(String pieceFile) {
    int slash = pieceFile.lastIndexOf('/');
    return slash >= 0 ? pieceFile.substring(slash + 1) : pieceFile;
  }

  public Map<String, ShipPieceMetadata> getPieces(String structureId) {
    return shipMetadata.getOrDefault(structureId, Collections.emptyMap());
  }

  public StructureFootprint estimateFootprint(String structureId) {
    Map<String, ShipPieceMetadata> pieces = shipMetadata.get(structureId);
    if (pieces == null || pieces.isEmpty()) {
      return new StructureFootprint(96, 48, 96);
    }

    int maxX = 0;
    int maxY = 0;
    int maxZ = 0;
    for (ShipPieceMetadata piece : pieces.values()) {
      maxX = Math.max(maxX, piece.size()[0]);
      maxY = Math.max(maxY, piece.size()[1]);
      maxZ = Math.max(maxZ, piece.size()[2]);
    }

    int length = maxZ * 3 + 40;
    int width = maxX + 40;
    return new StructureFootprint(width, maxY + 24, length);
  }

  private static int[] jsonIntArray(JsonArray array, int length) {
    int[] values = new int[length];
    for (int i = 0; i < length && i < array.size(); i++) {
      values[i] = array.get(i).getAsInt();
    }
    return values;
  }

  private record PieceExtras(List<int[]> technicalBlocks, List<LootContainer> lootContainers) {
    private static final PieceExtras EMPTY = new PieceExtras(List.of(), List.of());
  }
}
