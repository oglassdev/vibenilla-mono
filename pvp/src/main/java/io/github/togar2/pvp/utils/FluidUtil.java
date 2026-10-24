package io.github.togar2.pvp.utils;

import net.minestom.server.MinecraftServer;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.CoordConversion;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;

import java.util.ArrayList;
import java.util.List;

public class FluidUtil {
	public static boolean isWater(Block block) {
		return block.compare(Block.WATER)
				|| block.compare(Block.BUBBLE_COLUMN)
				|| block.compare(Block.KELP)
				|| block.compare(Block.KELP_PLANT)
				|| block.compare(Block.SEAGRASS)
				|| block.compare(Block.TALL_SEAGRASS)
				|| "true".equals(block.getProperty("waterlogged"));
	}

	public static boolean isLava(Block block) {
		return block.compare(Block.LAVA);
	}

	public static int getLevel(Block block) {
		String levelStr = block.getProperty("level");
		if (levelStr == null) return 8;
		int level = Integer.parseInt(levelStr);
		if (level >= 8) return 8; // Falling water
		return 8 - level;
	}

	public static double getHeight(Block block) {
		int level = getLevel(block);
		return switch (level) {
			case 1 -> 0.25;
			case 2 -> 0.375;
			case 3 -> 0.5;
			case 4 -> 0.625;
			case 5 -> 0.75;
			default -> 1;
		};
	}

	public static double getOwnHeight(Block block) {
		return getLevel(block) / 9.0;
	}

	public record FluidHeights(double water, double lava) {}

	public static FluidHeights getFluidHeights(Block.Getter blockGetter, Pos position, BoundingBox boundingBox) {
		var minimumY = position.y() + boundingBox.minY() + 0.001;
		var startX = (int) Math.floor(position.x() + boundingBox.minX() + 0.001);
		var startY = (int) Math.floor(minimumY);
		var startZ = (int) Math.floor(position.z() + boundingBox.minZ() + 0.001);
		var endX = (int) Math.ceil(position.x() + boundingBox.maxX() - 0.001) - 1;
		var endY = (int) Math.ceil(position.y() + boundingBox.maxY() - 0.001) - 1;
		var endZ = (int) Math.ceil(position.z() + boundingBox.maxZ() - 0.001) - 1;
		var entityY = position.y() + boundingBox.minY();
		var waterHeight = 0.0;
		var lavaHeight = 0.0;

		for (var blockX = startX; blockX <= endX; blockX++) {
			for (var blockY = startY; blockY <= endY; blockY++) {
				for (var blockZ = startZ; blockZ <= endZ; blockZ++) {
					var block = blockGetter.getBlock(blockX, blockY, blockZ);
					var lava = isLava(block);

					if (!lava && !isWater(block)) continue;

					var above = blockGetter.getBlock(blockX, blockY + 1, blockZ);
					var sameAbove = lava ? isLava(above) : isWater(above);
					var fluidTop = blockY + (sameAbove ? 1.0 : getOwnHeight(block));

					if (fluidTop < minimumY) continue;

					if (lava) {
						lavaHeight = Math.max(fluidTop - entityY, lavaHeight);
					} else {
						waterHeight = Math.max(fluidTop - entityY, waterHeight);
					}
				}
			}
		}

		return new FluidHeights(waterHeight, lavaHeight);
	}

	public static boolean isTouchingWater(Entity entity, Block block, int blockY) {
		return isTouchingWater(entity, entity.getPosition(), block, blockY);
	}

	public static boolean isTouchingWater(Entity entity, Pos position, Block block, int blockY) {
		if (!block.compare(Block.WATER) && !block.compare(Block.BUBBLE_COLUMN)
				&& !"true".equals(block.getProperty("waterlogged"))) return false;
		if (position.y() + entity.getBoundingBox().height() < blockY) return false;
		if (position.y() > (blockY + getHeight(block))) return false;
		return true;
	}

	record PairXZ(int x, int z) {}

	public static boolean isTouchingWater(Entity entity) {
		return isTouchingWater(entity, entity.getPosition());
	}

	public static boolean isTouchingWater(Entity entity, Pos position) {
		double x = position.x();
		int blockX = position.blockX();
		double z = position.z();
		int blockZ = position.blockZ();
		double y = position.y();
		int blockY = position.blockY();

		List<PairXZ> points = new ArrayList<>();
		points.add(new PairXZ(blockX, blockZ));

		if (x - blockX > 0.7) {
			if (z - blockZ > 0.7) {
				points.add(new PairXZ(blockX + 1, blockZ + 1));
			}
			points.add(new PairXZ(blockX + 1, blockZ));
		} else if (x - blockX < 0.2) {
			if (z - blockZ < 0.2) {
				points.add(new PairXZ(blockX - 1, blockZ - 1));
			}
			points.add(new PairXZ(blockX - 1, blockZ));
		}
		if (z - blockZ > 0.7) {
			if (x - blockX < 0.2) {
				points.add(new PairXZ(blockX - 1, blockZ + 1));
			}
			points.add(new PairXZ(blockX, blockZ + 1));
		} else if (z - blockZ < 0.2) {
			if (x - blockX > 0.7) {
				points.add(new PairXZ(blockX + 1, blockZ - 1));
			}
			points.add(new PairXZ(blockX, blockZ - 1));
		}

		Instance instance = entity.getInstance();
		assert instance != null;
		var blockGetter = new ChunkBlockGetter(instance, instance.getChunkAt(position), Block.AIR);

		for (PairXZ pair : points) {
			var block = blockGetter.getBlock(pair.x(), blockY, pair.z());
			if (isTouchingWater(entity, position, block, blockY)) return true;
			block = blockGetter.getBlock(pair.x(), blockY + 1, pair.z());
			if (isTouchingWater(entity, position, block, blockY + 1)) return true;

			if (y - blockY >= 2 - entity.getBoundingBox().height()) {
				block = blockGetter.getBlock(pair.x(), blockY + 2, pair.z());
				if (isTouchingWater(entity, position, block, blockY + 2)) return true;
			}
		}

		return false;
	}

	public static boolean isTouchingWater(Player player) {
		return isTouchingWater((Entity) player);
	}

	public static boolean hasOpenSky(Instance instance) {
		var dimensionType = MinecraftServer.getDimensionTypeRegistry().get(instance.getDimensionType());

		return dimensionType != null && dimensionType.hasSkylight() && !dimensionType.hasCeiling();
	}

	public static boolean isInRain(Entity entity) {
		var instance = entity.getInstance();

		if (instance == null) return false;
		if (!instance.getWeather().isRaining()) return false;
		if (!hasOpenSky(instance)) return false;

		var position = entity.getPosition();

		if (isRainingAt(instance, position.blockX(), position.blockY(), position.blockZ())) return true;

		var topBlockY = CoordConversion.globalToBlock(position.y() + entity.getBoundingBox().maxY());

		return isRainingAt(instance, position.blockX(), topBlockY, position.blockZ());
	}

	public static boolean isRainingAt(Instance instance, int blockX, int blockY, int blockZ) {
		var chunk = instance.getChunkAt(blockX, blockZ);

		if (chunk == null) return false;

		var localBlockX = CoordConversion.globalToSectionRelative(blockX);
		var localBlockZ = CoordConversion.globalToSectionRelative(blockZ);
		var highestBlockY = chunk.motionBlockingHeightmap().getHeight(localBlockX, localBlockZ);

		return highestBlockY < blockY;
	}
}
