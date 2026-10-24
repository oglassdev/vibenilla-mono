package dev.oglass.vibenilla.ticking;

import net.kyori.adventure.key.Key;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;

/** World access shared by the handlers. Unloaded and out of world positions read as barriers. */
final class Level {
    private static final Block OUT_OF_WORLD = Block.BARRIER;
    private static final float COLD_ENOUGH_FOR_SNOW = 0.15F;

    private Level() {}

    static Block block(Instance instance, int x, int y, int z) {
        var dimension = instance.getCachedDimensionType();
        if (y < dimension.minY()
                || y >= dimension.maxY()
                || !instance.isChunkLoaded(x >> 4, z >> 4)) {
            return OUT_OF_WORLD;
        }
        return instance.getBlock(x, y, z);
    }

    static Block block(Instance instance, Point position) {
        return block(instance, position.blockX(), position.blockY(), position.blockZ());
    }

    static boolean isLoaded(Instance instance, Point position) {
        var dimension = instance.getCachedDimensionType();
        return position.blockY() >= dimension.minY()
                && position.blockY() < dimension.maxY()
                && instance.isChunkLoaded(position.blockX() >> 4, position.blockZ() >> 4);
    }

    /** Sets the block when its chunk is loaded, without notifying neighbors. */
    static void set(Instance instance, Point position, Block block) {
        if (isLoaded(instance, position)) {
            instance.setBlock(position, block, false);
        }
    }

    /** Sets the block when its chunk is loaded and updates the neighbors. */
    static void setAndUpdate(Instance instance, Point position, Block block) {
        if (isLoaded(instance, position)) {
            instance.setBlock(position, block, true);
        }
    }

    static boolean hasTag(Block block, String key) {
        var tag = MinecraftServer.process().blocks().getTag(Key.key(key));
        return tag != null && tag.contains(block);
    }

    static int property(Block block, String name) {
        return Integer.parseInt(block.getProperty(name));
    }

    static Block withProperty(Block block, String name, int value) {
        return block.withProperty(name, Integer.toString(value));
    }

    static boolean containsWater(Block block) {
        return block.fluid() && !block.compare(Block.LAVA);
    }

    /** Whether the block holds a full fluid: a source or a falling flow. */
    static boolean isFullFluid(Block block) {
        if (!block.liquid()) {
            return containsWater(block) && !block.compare(Block.WATER);
        }
        var level = property(block, "level");
        return level == 0 || level >= 8;
    }

    /** The light at the position as vanilla's {@code getRawBrightness(pos, 0)}. */
    static int rawBrightness(Instance instance, int x, int y, int z) {
        return brightness(instance, x, y, z, 0);
    }

    /** The light at the position, dimmed by the time of day and the weather. */
    static int brightness(Instance instance, int x, int y, int z) {
        return brightness(instance, x, y, z, skyDarkness(instance));
    }

    private static int brightness(Instance instance, int x, int y, int z, int skyDarkness) {
        var dimension = instance.getCachedDimensionType();
        if (y >= dimension.maxY()) {
            return dimension.hasSkylight() ? 15 - skyDarkness : 0;
        }
        if (y < dimension.minY() || !instance.isChunkLoaded(x >> 4, z >> 4)) {
            return 0;
        }
        return Math.max(
                instance.getBlockLight(x, y, z), instance.getSkyLight(x, y, z) - skyDarkness);
    }

    /** How much the sky light is dimmed, from 0 at noon to 11 at midnight. */
    static int skyDarkness(Instance instance) {
        var day = instance.getTime() / 24000.0 - 0.25;
        var fraction = day - Math.floor(day);
        var smoothed = 0.5 - Math.cos(fraction * Math.PI) / 2.0;
        var timeOfDay = (float) (fraction * 2.0 + smoothed) / 3.0F;
        var brightness = 1.0F - (float) (Math.cos(timeOfDay * (float) (Math.PI * 2)) * 2.0F + 0.5F);
        brightness = 1.0F - Math.clamp(brightness, 0.0F, 1.0F);
        var weather = instance.getWeather();
        brightness *= 1.0F - weather.rainLevel() * 5.0F / 16.0F;
        brightness *= 1.0F - weather.thunderLevel() * 5.0F / 16.0F;
        return (int) ((1.0F - brightness) * 11.0F);
    }

    static boolean isRainingAt(Instance instance, Point position) {
        if (instance.getWeather().rainLevel() <= 0.2F) {
            return false;
        }

        var x = position.blockX();
        var z = position.blockZ();
        var chunk = instance.getChunk(x >> 4, z >> 4);
        if (chunk == null
                || chunk.motionBlockingHeightmap().getHeight(x & 15, z & 15) >= position.blockY()) {
            return false;
        }

        var biome =
                instance.getBiome(x, position.blockY(), z).resolve(instance.registries().biome());
        return biome != null
                && biome.hasPrecipitation()
                && biome.temperature() >= COLD_ENOUGH_FOR_SNOW;
    }
}
