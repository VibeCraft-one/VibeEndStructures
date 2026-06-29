package ru.vibecraft.vibeendstructures.generation;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

public final class ChunkStructureListener implements Listener {

    private final StructureGenerator generator;

    public ChunkStructureListener(StructureGenerator generator) {
        this.generator = generator;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        generator.tryGenerate(event.getChunk());
    }
}
