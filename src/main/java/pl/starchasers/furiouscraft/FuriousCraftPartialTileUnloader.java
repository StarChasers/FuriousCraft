package pl.starchasers.furiouscraft;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.Entity;
import net.minecraft.world.chunk.Chunk;

import java.util.HashSet;
import java.util.List;

public class FuriousCraftPartialTileUnloader {
	public static final HashSet<Chunk> chunks = new HashSet<Chunk>();

	@SubscribeEvent
	public void serverTickEnd(TickEvent.ServerTickEvent event) {
		if (chunks.size() > 0) {
			synchronized (chunks) {
				for (Chunk c : chunks) {
					c.worldObj.loadedTileEntityList.removeAll(c.chunkTileEntityMap.values());
					for (List l : c.entityLists) {
						c.worldObj.loadedEntityList.removeAll(l);
						for (Entity e : (List<Entity>) l) {
							c.worldObj.onEntityRemoved(e);
						}
					}
				}
				System.out.println("PTU: Partially unloaded " + chunks.size() + " chunks");
				chunks.clear();
			}
		}
	}
}
