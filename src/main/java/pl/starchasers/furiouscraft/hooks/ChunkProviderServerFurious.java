package pl.starchasers.furiouscraft.hooks;

import com.google.common.collect.Lists;
import cpw.mods.fml.common.registry.GameRegistry;
import gnu.trove.map.hash.TLongIntHashMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.set.hash.TLongHashSet;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.util.ReportedException;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.MinecraftException;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.chunkio.ChunkIOExecutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Date;
import java.util.List;

public class ChunkProviderServerFurious extends ChunkProviderServer {
	private static final Logger logger = LogManager.getLogger();

	private enum State {
		CLEANING, WAITING
	};

	private Chunk defaultEmptyChunk;
	public TLongHashSet chunksToUnload = new TLongHashSet();
	public TLongObjectHashMap<Chunk> chunksToSave = new TLongObjectHashMap<Chunk>();
	public TLongIntHashMap chunkGracePeriod = new TLongIntHashMap();
	public TLongIntHashMap chunkMaxGracePeriod = new TLongIntHashMap();
	private TLongHashSet loadingChunks = new TLongHashSet();
	private boolean isSaving = false;
	private long time;
	private State state = State.WAITING;

	public ChunkProviderServerFurious(WorldServer world, IChunkLoader loader, IChunkProvider provider) {
		super(world, loader, provider);
		this.defaultEmptyChunk = new EmptyChunk(world, 0, 0);
	}

	@Override
	public boolean chunkExists(int x, int z) {
		return this.loadedChunkHashMap.containsItem(ChunkCoordIntPair.chunkXZ2Int(x, z));
	}

	@Override
	public List func_152380_a() {
		return this.loadedChunks;
	}

	@Override
	public void unloadChunksIfNotNearSpawn(int x, int z) {
		if (this.worldObj.provider.canRespawnHere() && DimensionManager.shouldLoadSpawn(this.worldObj.provider.dimensionId)) {
			// Make sure the spawn is force-loaded
			ChunkCoordinates chunkcoordinates = this.worldObj.getSpawnPoint();
			int xOffset = x * 16 + 8 - chunkcoordinates.posX;
			int zOffset = z * 16 + 8 - chunkcoordinates.posZ;
			short distance = 128;

			if (xOffset < -distance || xOffset > distance || zOffset < -distance || zOffset > distance) {
				this.chunksToUnload.add(ChunkCoordIntPair.chunkXZ2Int(x, z));
			}
		} else {
			// Unload any chunk
			this.chunksToUnload.add(ChunkCoordIntPair.chunkXZ2Int(x, z));
		}
	}

	/**
	 * Misleading name - actually unloads all chunks not near spawn.
	 */
	@Override
	public void unloadAllChunks() {
		for (Chunk chunk : (List<Chunk>) this.loadedChunks) {
			this.unloadChunksIfNotNearSpawn(chunk.xPosition, chunk.zPosition);
		}
	}

	public Chunk loadChunk(int x, int z) {
		return loadChunk(x, z, null);
	}

	public Chunk loadChunk(int x, int z, Runnable callback) {
		long k = ChunkCoordIntPair.chunkXZ2Int(x, z);
		synchronized (this.chunksToSave) {
			// Since we're already saving at this point (!isSaving was checked
			// earlier), we must force-remove the chunk. This should be rare.
			if (this.chunksToSave.containsKey(k)) {
				finishSavingChunk(k, chunksToSave.get(k));
			}
		}
		Chunk chunk = (Chunk) loadedChunkHashMap.getValueByKey(k);
		AnvilChunkLoader loader = null;

		if (this.currentChunkLoader instanceof AnvilChunkLoader) {
			loader = (AnvilChunkLoader) this.currentChunkLoader;
		}

		// We can only use the queue for already generated chunks
		if (chunk == null && loader != null && loader.chunkExists(this.worldObj, x, z)) {
			if (callback != null) {
				ChunkIOExecutor.queueChunkLoad(this.worldObj, loader, this, x, z, callback);
				return null;
			} else {
				chunk = ChunkIOExecutor.syncChunkLoad(this.worldObj, loader, this, x, z);
			}
		}
		else if (chunk == null)
		{
			chunk = this.originalLoadChunk(x, z);
		}

		// If we didn't load the chunk asynchronously and still have a callback run it now.
		if (callback != null) {
			callback.run();
		}

		return chunk;
	}

	/**
	 * Synchronous chunk loader. Used for generating new chunks and as a
	 * fallback.
	 */
	public Chunk originalLoadChunk(int x, int z) {
		long k = ChunkCoordIntPair.chunkXZ2Int(x, z);
		this.chunksToUnload.remove(k);
		synchronized (this.chunksToSave) {
			if (this.chunksToSave.containsKey(k)) {
				finishSavingChunk(k, this.chunksToSave.get(k));
			}
		}
		Chunk chunk = (Chunk) this.loadedChunkHashMap.getValueByKey(k);

		if (chunk == null)
		{
			boolean added = loadingChunks.add(k);
			if (!added) {
				cpw.mods.fml.common.FMLLog.bigWarning("There is an attempt to load a chunk (%d,%d) in dimension %d that is already being loaded. This will cause weird chunk breakages.", x, z, worldObj.provider.dimensionId);
			}
			chunk = ForgeChunkManager.fetchDormantChunk(k, this.worldObj);
			if (chunk == null) {
				chunk = this.safeLoadChunk(x, z);
			}

			if (chunk == null) {
				if (this.currentChunkProvider == null) {
					chunk = this.defaultEmptyChunk;
				} else {
					try {
						chunk = this.currentChunkProvider.provideChunk(x, z);
					} catch (Throwable throwable) {
						CrashReport crashreport = CrashReport.makeCrashReport(throwable, "Exception generating new chunk");
						CrashReportCategory crashreportcategory = crashreport.makeCategory("Chunk to be generated");
						crashreportcategory.addCrashSection("Location", String.format("%d,%d", new Object[] {Integer.valueOf(x), Integer.valueOf(z)}));
						crashreportcategory.addCrashSection("Position hash", Long.valueOf(k));
						crashreportcategory.addCrashSection("Generator", this.currentChunkProvider.makeString());
						throw new ReportedException(crashreport);
					}
				}
			}

			synchronized (this.loadedChunkHashMap) {
				beginLoadingChunk(k, chunk);
				loadingChunks.remove(k);
				chunk.onChunkLoad();
				chunk.populateChunk(this, this, x, z);
			}
		}

		return chunk;
	}

	/**
q	 * Will return back a chunk, if it doesn't exist and its not a MP client it will generates all the blocks for the
	 * specified chunk from the map seed and chunk seed
	 */
	public Chunk provideChunk(int p_73154_1_, int p_73154_2_)
	{
		long l = ChunkCoordIntPair.chunkXZ2Int(p_73154_1_, p_73154_2_);

		Chunk chunk = (Chunk) this.loadedChunkHashMap.getValueByKey(l);
		if (chunk != null) {
			return chunk;
		} else {
			if (!isSaving) {
				synchronized (chunksToSave) {
					if (chunksToSave.containsKey(l)) {
						chunk = this.chunksToSave.get(l);
						beginLoadingChunk(l, chunk);
						this.chunksToSave.remove(l);

						int gracePeriod = this.chunkMaxGracePeriod.containsKey(l) ? this.chunkMaxGracePeriod.get(l) : 0;
						gracePeriod = Math.max(31, ((gracePeriod + 1) * 2) - 1);
						this.chunkGracePeriod.put(l, gracePeriod);
						this.chunkMaxGracePeriod.put(l, gracePeriod);

						loadChunkPartial(chunk);
						return chunk;
					}
				}
			}

			synchronized (this.chunksToSave) {
				return !this.worldObj.findingSpawnPoint && !this.loadChunkOnProvideRequest ? this.defaultEmptyChunk : this.loadChunk(p_73154_1_, p_73154_2_);
			}
		}
	}

	/**
	 * used by loadChunk, but catches any exceptions if the load fails.
	 */
	private Chunk safeLoadChunk(int p_73239_1_, int p_73239_2_)
	{
		if (this.currentChunkLoader == null)
		{
			return null;
		}
		else
		{
			try
			{
				Chunk chunk = this.currentChunkLoader.loadChunk(this.worldObj, p_73239_1_, p_73239_2_);

				if (chunk != null)
				{
					chunk.lastSaveTime = this.worldObj.getTotalWorldTime();

					if (this.currentChunkProvider != null)
					{
						this.currentChunkProvider.recreateStructures(p_73239_1_, p_73239_2_);
					}
				}

				return chunk;
			}
			catch (Exception exception)
			{
				logger.error("Couldn\'t load chunk", exception);
				return null;
			}
		}
	}

	/**
	 * used by saveChunks, but catches any exceptions if the save fails.
	 */
	private void safeSaveExtraChunkData(Chunk p_73243_1_)
	{
		if (this.currentChunkLoader != null)
		{
			try
			{
				this.currentChunkLoader.saveExtraChunkData(this.worldObj, p_73243_1_);
			}
			catch (Exception exception)
			{
				logger.error("Couldn\'t save entities", exception);
			}
		}
	}

	/**
	 * used by saveChunks, but catches any exceptions if the save fails.
	 */
	private void safeSaveChunk(Chunk p_73242_1_)
	{
		if (this.currentChunkLoader != null)
		{
			try
			{
				p_73242_1_.lastSaveTime = this.worldObj.getTotalWorldTime();
				this.currentChunkLoader.saveChunk(this.worldObj, p_73242_1_);
			}
			catch (IOException ioexception)
			{
				logger.error("Couldn\'t save chunk", ioexception);
			}
			catch (MinecraftException minecraftexception)
			{
				logger.error("Couldn\'t save chunk; already in use by another instance of Minecraft?", minecraftexception);
			}
		}
	}

	/**
	 * Populates chunk with ores etc etc
	 */
	public void populate(IChunkProvider p_73153_1_, int p_73153_2_, int p_73153_3_)
	{
		Chunk chunk = this.provideChunk(p_73153_2_, p_73153_3_);

		if (!chunk.isTerrainPopulated)
		{
			chunk.func_150809_p();

			if (this.currentChunkProvider != null)
			{
				this.currentChunkProvider.populate(p_73153_1_, p_73153_2_, p_73153_3_);
				GameRegistry.generateWorld(p_73153_2_, p_73153_3_, worldObj, currentChunkProvider, p_73153_1_);
				chunk.setChunkModified();
			}
		}
	}

	/**
	 * Two modes of operation: if passed true, save all Chunks in one go.  If passed false, save up to two chunks.
	 * Return true if all chunks have been saved.
	 */
	public boolean saveChunks(boolean p_73151_1_, IProgressUpdate p_73151_2_)
	{
		int i = 0;

		List<Chunk> loadedChunksCopy = Lists.newArrayList(this.loadedChunks);

		for (Chunk chunk : loadedChunksCopy)
		{
			if (p_73151_1_)
			{
				this.safeSaveExtraChunkData(chunk);
			}

			if (chunk.needsSaving(p_73151_1_))
			{
				this.safeSaveChunk(chunk);
				chunk.isModified = false;
				++i;

				if (i == 24 && !p_73151_1_)
				{
					return false;
				}
			}
		}

		long[] chunks = this.chunksToSave.keys();

		for (long l : chunks)
		{
			Chunk chunk = this.chunksToSave.get(l);
			if (chunk == null) {
				continue;
			}

			if (p_73151_1_)
			{
				this.safeSaveExtraChunkData(chunk);
			}

			if (chunk.needsSaving(p_73151_1_))
			{
				this.safeSaveChunk(chunk);
				chunk.isModified = false;
				++i;

				if (i == 24 && !p_73151_1_)
				{
					return false;
				}
			}
		}

		return true;
	}

	/**
	 * Save extra data not associated with any Chunk.  Not saved during autosave, only during world unload.  Currently
	 * unimplemented.
	 */
	public void saveExtraData()
	{
		if (this.currentChunkLoader != null)
		{
			this.currentChunkLoader.saveExtraData();
		}
	}

	/**
	 * Unloads chunks that are marked to be unloaded. This is not guaranteed to unload every such chunk.
	 */
	public boolean unloadQueuedChunks()
	{
		if (!this.worldObj.levelSaving) {
			long currTime = (new Date()).getTime();
			isSaving = true;
			if (state == State.CLEANING) {
				if (chunksToSave.size() > 0) {
					saveUngracedChunks(20);
				}

				if (chunksToSave.size() == 0) {
					if (this.loadedChunks.size() == 0 && ForgeChunkManager.getPersistentChunksFor(this.worldObj).size() == 0 && !DimensionManager.shouldLoadSpawn(this.worldObj.provider.dimensionId)) {
						DimensionManager.unloadWorld(this.worldObj.provider.dimensionId);
						return currentChunkProvider.unloadQueuedChunks();
					}
					state = State.WAITING;
				}
			} else if (state == State.WAITING && currTime >= time + 15000) {
				int queued = 0;
				int graced = 0;
				state = State.CLEANING;

				saveUngracedChunks(20);

				if (this.loadedChunks.size() == 0 && this.chunksToSave.size() == 0 && ForgeChunkManager.getPersistentChunksFor(this.worldObj).size() == 0 && !DimensionManager.shouldLoadSpawn(this.worldObj.provider.dimensionId)) {
					DimensionManager.unloadWorld(this.worldObj.provider.dimensionId);
					return currentChunkProvider.unloadQueuedChunks();
				}

				long[] keys = chunksToUnload.toArray();
				for (long l : keys) {
					Chunk chunk = (Chunk) loadedChunkHashMap.getValueByKey(l);

					if (this.loadChunkOnProvideRequest && chunkGracePeriod.get(l) > 0) {
						graced++;
						chunkGracePeriod.put(l, chunkGracePeriod.get(l) - 1);
						continue;
					}

					if (chunk != null) {
						synchronized (chunksToSave) {
							synchronized (this.loadedChunkHashMap) {
								unloadChunkPartial(chunk);
								this.loadedChunkHashMap.remove(l);
								this.loadedChunks.remove(chunk);
							}
							this.chunksToSave.put(l, chunk);
							queued++;
						}
					}
				}

				if (this.currentChunkLoader != null) {
					this.currentChunkLoader.chunkTick();
				}

				System.out.println(String.format("GC TICK: %d[%d] chunks loaded (%d queued, %d graced)", this.loadedChunks.size(), this.loadedChunks.size() - this.chunksToUnload.size(), queued, graced));
				time = currTime;
				if (chunksToSave.size() == 0) {
					state = State.WAITING;
				}
			}
			isSaving = false;
		}

		return this.currentChunkProvider.unloadQueuedChunks();
	}

	/**
	 * Returns if the IChunkProvider supports saving.
	 */
	public boolean canSave()
	{
		return !this.worldObj.levelSaving;
	}

	/**
	 * Converts the instance data to a readable string.
	 */
	public String makeString()
	{
		return "ServerChunkCache: " + this.loadedChunks.size() + " Drop: " + this.chunksToSave.size();
	}

	/**
	 * Returns a list of creatures of the specified type that can spawn at the given location.
	 */
	public List getPossibleCreatures(EnumCreatureType p_73155_1_, int p_73155_2_, int p_73155_3_, int p_73155_4_)
	{
		return this.currentChunkProvider.getPossibleCreatures(p_73155_1_, p_73155_2_, p_73155_3_, p_73155_4_);
	}

	public ChunkPosition func_147416_a(World p_147416_1_, String p_147416_2_, int p_147416_3_, int p_147416_4_, int p_147416_5_)
	{
		return this.currentChunkProvider.func_147416_a(p_147416_1_, p_147416_2_, p_147416_3_, p_147416_4_, p_147416_5_);
	}

	public int getLoadedChunkCount()
	{
		return this.loadedChunks.size();
	}

	public void recreateStructures(int p_82695_1_, int p_82695_2_) {}

	private void unloadChunkPartial(Chunk c) {
		c.isChunkLoaded = false;
		synchronized (c) {
			c.worldObj.loadedTileEntityList.removeAll(c.chunkTileEntityMap.values());
			for (List l : c.entityLists) {
				c.worldObj.loadedEntityList.removeAll(l);
				for (Entity e : (List<Entity>) l) {
					c.worldObj.onEntityRemoved(e);
				}
			}
		}
	}

	private void unloadChunkFinish(Chunk chunk) {
		chunk.onChunkUnload();
	}

	private void loadChunkPartial(Chunk chunk) {
		chunk.isChunkLoaded = true;
		synchronized (chunk) {
			this.worldObj.func_147448_a(chunk.chunkTileEntityMap.values());

			for (int i = 0; i < chunk.entityLists.length; ++i) {
				this.worldObj.loadedEntityList.addAll(chunk.entityLists[i]);
				for (Entity e : (List<Entity>) chunk.entityLists[i]) {
					if (worldObj.getEntityByID(e.getEntityId()) == null) {
						this.worldObj.loadedEntityList.add(e);
						this.worldObj.onEntityAdded(e);
					}
				}
			}
		}
	}

	private void beginLoadingChunk(long k, Chunk chunk) {
		this.loadedChunkHashMap.add(k, chunk);
		this.loadedChunks.add(chunk);
	}

	private void finishSavingChunk(long l, Chunk chunk) {
		synchronized (chunksToSave) {
			if (chunk != null) {
				unloadChunkFinish(chunk);
				this.safeSaveChunk(chunk);
				this.safeSaveExtraChunkData(chunk);
				this.chunksToUnload.remove(l);
				this.chunkGracePeriod.remove(l);
				this.chunkMaxGracePeriod.remove(l);
				ForgeChunkManager.putDormantChunk(ChunkCoordIntPair.chunkXZ2Int(chunk.xPosition, chunk.zPosition), chunk);
			}
			this.chunksToSave.remove(l);
		}
	}

	private void saveUngracedChunks(int limit) {
		long[] keys = this.chunksToSave.keys();
		int saved = 0;
		for (long l : keys) {
			Chunk chunk = this.chunksToSave.get(l);
			saved++;
			finishSavingChunk(l, chunk);
			if (saved >= limit) {
				return;
			}
		}
	}
}