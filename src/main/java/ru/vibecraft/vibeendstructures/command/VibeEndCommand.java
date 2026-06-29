package ru.vibecraft.vibeendstructures.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.vibecraft.vibeendstructures.VibeEndStructuresPlugin;
import ru.vibecraft.vibeendstructures.generation.EndSurfaceFinder;
import ru.vibecraft.vibeendstructures.model.StructureDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class VibeEndCommand implements CommandExecutor, TabCompleter {

    private final VibeEndStructuresPlugin plugin;

    public VibeEndCommand(VibeEndStructuresPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("vibeend.admin")) {
            sender.sendMessage(Component.text("Нет прав.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reload();
                sender.sendMessage(Component.text("VibeEndStructures перезагружен.", NamedTextColor.GREEN));
            }
            case "list" -> {
                List<String> ids = plugin.getRegistry().getDefinitions().stream()
                        .map(StructureDefinition::id)
                        .sorted()
                        .toList();
                sender.sendMessage(Component.text("Структуры (" + ids.size() + "): " + String.join(", ", ids), NamedTextColor.AQUA));
            }
            case "paste" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Только для игроков.", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Использование: /vibeend paste <structure>", NamedTextColor.YELLOW));
                    return true;
                }
                String id = args[1].toLowerCase();
                StructureDefinition definition = plugin.getRegistry().getDefinition(id).orElse(null);
                if (definition == null) {
                    sender.sendMessage(Component.text("Структура не найдена: " + id, NamedTextColor.RED));
                    return true;
                }

                int minY = Math.max(definition.minY(), plugin.getPluginConfig().getMinY());
                Block surface = EndSurfaceFinder.findSurfaceAt(
                        player.getWorld(),
                        player.getLocation().getBlockX(),
                        player.getLocation().getBlockZ(),
                        minY
                ).orElseGet(() -> {
                    Random random = new Random();
                    return EndSurfaceFinder.findSurface(
                            player.getWorld(),
                            player.getLocation().getBlockX() >> 4,
                            player.getLocation().getBlockZ() >> 4,
                            minY,
                            random
                    ).orElse(player.getLocation().subtract(0, 1, 0).getBlock());
                });

                Location anchor = surface.getLocation();
                anchor.setY(surface.getY());

                var result = plugin.getPlacer().placeAt(
                        anchor,
                        definition,
                        StructureRotation.NONE,
                        Mirror.NONE
                );

                if (result.success()) {
                    sender.sendMessage(Component.text("Структура " + id + " установлена.", NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("Не удалось установить структуру. Смотри консоль сервера.", NamedTextColor.RED));
                    if (plugin.getPluginConfig().isDebug()) {
                        plugin.getLogger().warning("Paste failed for " + id + " at " + anchor.getBlockX() + ","
                                + anchor.getBlockY() + "," + anchor.getBlockZ());
                    }
                }
            }
            default -> sendUsage(sender);
        }

        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("Использование:", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/vibeend list", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/vibeend paste <structure>", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/vibeend reload", NamedTextColor.GRAY));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("vibeend.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(List.of("list", "paste", "reload"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("paste")) {
            return filter(plugin.getRegistry().getDefinitions().stream().map(StructureDefinition::id).sorted().toList(), args[1]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase().startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }
}
