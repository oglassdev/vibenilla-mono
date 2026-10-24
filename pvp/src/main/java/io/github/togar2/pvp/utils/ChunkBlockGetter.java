package io.github.togar2.pvp.utils;

import net.minestom.server.coordinate.CoordConversion;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.Nullable;

public final class ChunkBlockGetter implements Block.Getter {
	private final Instance instance;
	private final Block defaultBlock;
	private @Nullable Chunk chunk;

	public ChunkBlockGetter(Instance instance, @Nullable Chunk chunk, Block defaultBlock) {
		this.instance = instance;
		this.chunk = chunk;
		this.defaultBlock = defaultBlock;
	}

	@Override
	public Block getBlock(int blockX, int blockY, int blockZ, Condition condition) {
		var chunk = this.chunk;
		var chunkX = CoordConversion.globalToChunk(blockX);
		var chunkZ = CoordConversion.globalToChunk(blockZ);

		if (chunk == null || !chunk.isLoaded()
				|| chunk.getChunkX() != chunkX || chunk.getChunkZ() != chunkZ) {
			this.chunk = chunk = this.instance.getChunk(chunkX, chunkZ);
		}

		if (chunk == null) return this.defaultBlock;

		chunk.lockReadLock();

		try {
			return chunk.getBlock(blockX, blockY, blockZ, condition);
		} finally {
			chunk.unlockReadLock();
		}
	}
}
