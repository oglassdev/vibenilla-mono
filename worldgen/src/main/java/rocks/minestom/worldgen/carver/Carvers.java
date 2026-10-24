package rocks.minestom.worldgen.carver;

import java.util.Set;
import net.kyori.adventure.key.Key;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.NoiseGeneratorSettingsRuntime;
import rocks.minestom.worldgen.biome.BiomeZoomer;
import rocks.minestom.worldgen.biome.CachedBiomeSource;
import rocks.minestom.worldgen.density.ChunkContext;
import rocks.minestom.worldgen.density.DensityFunction;
import rocks.minestom.worldgen.random.LegacyRandomSource;
import rocks.minestom.worldgen.random.WorldgenRandom;
import rocks.minestom.worldgen.structure.context.BlockTagManager;
import rocks.minestom.worldgen.surface.BiomeResolver;
import rocks.minestom.worldgen.surface.SurfaceRules;
import rocks.minestom.worldgen.terrain.Aquifer;
import rocks.minestom.worldgen.terrain.TerrainData;

/**
 * Vanilla CARVERS stage (runs after the surface stage): every source chunk in a 17x17 neighborhood
 * seeds each of its biome's carvers with {@code setLargeFeatureSeed(seed + index, sourceX,
 * sourceZ)} and carves the portion of its tunnels that intersects the target chunk. The biome is
 * read at the source chunk's (0, 0) corner quart at y = 0, exactly like vanilla.
 */
public final class Carvers {
    private static final int CHUNK_RANGE = 8;

    private final NoiseGeneratorSettingsRuntime settings;
    private final CachedBiomeSource biomeSource;
    private final BiomeResolver biomeResolver;
    private final BiomeZoomer biomeZoomer;
    private final CarverLoader carverLoader;
    private final Set<Key> uncarvable;

    public Carvers(
            NoiseGeneratorSettingsRuntime settings,
            CachedBiomeSource biomeSource,
            BiomeResolver biomeResolver,
            BiomeZoomer biomeZoomer,
            CarverLoader carverLoader,
            BlockTagManager blockTags) {
        this.settings = settings;
        this.biomeSource = biomeSource;
        this.biomeResolver = biomeResolver;
        this.biomeZoomer = biomeZoomer;
        this.carverLoader = carverLoader;
        this.uncarvable = blockTags.blocks(Key.key("minecraft:uncarvable"));
    }

    /**
     * Carves the chunk's terrain buffer in place and refreshes the height arrays afterwards. The
     * aquifer must be the one the noise fill used so flooded caves resolve the same fluids as
     * vanilla's shared NoiseChunk aquifer.
     */
    public void applyCarvers(TerrainData data, int chunkX, int chunkZ, Aquifer aquifer) {
        var minY = this.settings.minY();
        var height = this.settings.height();
        var context =
                new CarvingContext(data, chunkX, chunkZ, minY, height, aquifer, this.topMaterial());
        var random = new WorldgenRandom(new LegacyRandomSource(0L));
        var seed = this.settings.randomState().seed();

        for (var offsetX = -CHUNK_RANGE; offsetX <= CHUNK_RANGE; offsetX++) {
            for (var offsetZ = -CHUNK_RANGE; offsetZ <= CHUNK_RANGE; offsetZ++) {
                var sourceChunkX = chunkX + offsetX;
                var sourceChunkZ = chunkZ + offsetZ;
                var biome = this.biomeSource.biome(sourceChunkX << 2, 0, sourceChunkZ << 2);
                var carverIds = this.carverLoader.biomeCarvers(biome);

                for (var index = 0; index < carverIds.size(); index++) {
                    var carver = this.carverLoader.configuredCarver(carverIds.get(index));
                    if (carver == null) {
                        continue;
                    }

                    random.setLargeFeatureSeed(seed + index, sourceChunkX, sourceChunkZ);
                    if (carver.isStartChunk(random)) {
                        carver.carve(context, random, sourceChunkX, sourceChunkZ);
                    }
                }
            }
        }

        this.applyMask(context);
        recomputeHeights(data, minY, height);
    }

    /**
     * Vanilla {@code applyCarvingMask}: every carved column run is re-resolved through the aquifer
     * at density 0. Dirt under a carved-away grass or mycelium block gets the surface rule's top
     * material back.
     */
    private void applyMask(CarvingContext context) {
        var aquifer = context.aquifer();
        var bottomY = context.minGenY() + 1;
        var topY = context.maxGenYInclusive() - 7;
        for (var localX = 0; localX < 16; localX++) {
            for (var localZ = 0; localZ < 16; localZ++) {
                var worldX = context.minBlockX() + localX;
                var worldZ = context.minBlockZ() + localZ;
                var hasGrass = false;
                for (var worldY = topY; worldY >= bottomY; worldY--) {
                    if (!context.maskGet(localX, worldY, localZ)) {
                        hasGrass = false;
                        continue;
                    }
                    var block = context.getBlock(worldX, worldY, worldZ);
                    if (this.uncarvable.contains(block.key())) {
                        continue;
                    }
                    if (block.compare(Block.GRASS_BLOCK) || block.compare(Block.MYCELIUM)) {
                        hasGrass = true;
                    }
                    var state =
                            aquifer.computeSubstance(
                                    new DensityFunction.SinglePointContext(worldX, worldY, worldZ),
                                    0.0);
                    if (state == null) {
                        continue;
                    }
                    context.setCarved(worldX, worldY, worldZ, state);
                    var fluid = !state.air();
                    if (aquifer.shouldScheduleFluidUpdate() && fluid) {
                        context.recordFluidTick(worldX, worldY, worldZ);
                    }
                    if (hasGrass
                            && context.getBlock(worldX, worldY - 1, worldZ).compare(Block.DIRT)) {
                        var topMaterial = context.topMaterial(worldX, worldY - 1, worldZ, fluid);
                        if (topMaterial != null) {
                            context.setSolid(worldX, worldY - 1, worldZ, topMaterial);
                        }
                    }
                }
            }
        }
    }

    /**
     * Surface-rule evaluation for a single exposed floor block, mirroring vanilla {@code
     * SurfaceSystem.topMaterial} (stone depths of 1, water at the block above when the carved state
     * is a fluid).
     */
    private CarvingContext.TopMaterial topMaterial() {
        var surfaceRule = this.settings.surfaceRule();
        if (surfaceRule == null) {
            return null;
        }

        var surfaceContext =
                new SurfaceRules.Context(
                        this.settings.surfaceSystem(),
                        this.settings.randomState(),
                        this.biomeResolver,
                        this.biomeZoomer,
                        this.settings.chunkSurfaceLevel(),
                        new ChunkContext(),
                        this.settings.minY(),
                        this.settings.maxYInclusive());
        return (blockX, blockY, blockZ, underFluid) -> {
            surfaceContext.updateXZ(blockX, blockZ, false);
            surfaceContext.updateY(blockY, 1, 1, underFluid ? blockY + 1 : Integer.MIN_VALUE);
            return surfaceRule.tryApply(surfaceContext);
        };
    }

    /**
     * Re-derives the per-column height arrays (first solid / first fluid from the top) after
     * carving, since features and heightmap consumers read them.
     */
    private static void recomputeHeights(TerrainData data, int minY, int height) {
        var surfaceHeights = data.surfaceHeights();
        var waterHeights = data.waterHeights();
        var stoneMask = data.stoneMask();

        for (var surfaceIndex = 0; surfaceIndex < surfaceHeights.length; surfaceIndex++) {
            var maskIndex = surfaceIndex * height;
            var surfaceHeight = Integer.MIN_VALUE;
            var waterHeight = Integer.MIN_VALUE;
            for (var yIndex = height - 1; yIndex >= 0; yIndex--) {
                var state = stoneMask[maskIndex + yIndex];
                if (surfaceHeight == Integer.MIN_VALUE
                        && (state == TerrainData.SOLID || state == TerrainData.SOLID_OTHER)) {
                    surfaceHeight = minY + yIndex;
                }
                if (waterHeight == Integer.MIN_VALUE && state == TerrainData.FLUID) {
                    waterHeight = minY + yIndex + 1;
                }
                if (surfaceHeight != Integer.MIN_VALUE && waterHeight != Integer.MIN_VALUE) {
                    break;
                }
            }
            surfaceHeights[surfaceIndex] = surfaceHeight;
            waterHeights[surfaceIndex] = waterHeight;
        }
    }
}
