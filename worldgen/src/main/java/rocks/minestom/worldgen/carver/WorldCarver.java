package rocks.minestom.worldgen.carver;

import rocks.minestom.worldgen.VMath;
import rocks.minestom.worldgen.random.RandomSource;

/**
 * Vanilla {@code WorldCarver}: a carver marks the blocks it cuts out through a {@link
 * CarverOutput}. Exact port including the ellipsoid carving loop; random call order is preserved
 * throughout.
 */
public sealed interface WorldCarver permits CaveWorldCarver, CanyonWorldCarver {
    default int getRange() {
        return 4;
    }

    boolean isStartChunk(RandomSource random);

    /**
     * Carves the portion of this carver's tunnels (started in the source chunk) that intersects the
     * target chunk.
     */
    boolean carve(
            GenerationRange range,
            RandomSource random,
            int chunkX,
            int chunkZ,
            int sourceChunkX,
            int sourceChunkZ,
            CarverOutput output);

    /**
     * Carves into the context's carving mask. The caller applies the mask afterwards (replacing
     * carved blocks with air, water or lava).
     */
    default boolean carve(
            CarvingContext context, RandomSource random, int sourceChunkX, int sourceChunkZ) {
        var output =
                new CarverOutput() {
                    @Override
                    public int minY() {
                        return context.minGenY() + 1;
                    }

                    @Override
                    public int maxY() {
                        // Vanilla protects the top 7 blocks of the generation range.
                        return context.maxGenYInclusive() - 7;
                    }

                    @Override
                    public void carve(int xIndex, int worldY, int zIndex) {
                        context.maskSet(xIndex, worldY, zIndex);
                    }
                };
        return this.carve(
                new GenerationRange(context.minGenY(), context.genDepth()),
                random,
                context.chunkX(),
                context.chunkZ(),
                sourceChunkX,
                sourceChunkZ,
                output);
    }

    static void carveEllipsoid(
            int chunkX,
            int chunkZ,
            double x,
            double y,
            double z,
            double horizontalRadius,
            double verticalRadius,
            CarverOutput output,
            CarveSkipChecker skipChecker) {
        double centerX = chunkX * 16 + 8;
        double centerZ = chunkZ * 16 + 8;
        var maxDelta = 16.0 + horizontalRadius * 2.0;
        if (Math.abs(x - centerX) > maxDelta || Math.abs(z - centerZ) > maxDelta) {
            return;
        }

        var chunkMinX = chunkX * 16;
        var chunkMinZ = chunkZ * 16;
        var minXIndex = Math.max(VMath.floor(x - horizontalRadius) - chunkMinX - 1, 0);
        var maxXIndex = Math.min(VMath.floor(x + horizontalRadius) - chunkMinX, 15);
        var minY = Math.max(VMath.floor(y - verticalRadius) - 1, output.minY());
        var maxY = Math.min(VMath.floor(y + verticalRadius) + 1, output.maxY());
        var minZIndex = Math.max(VMath.floor(z - horizontalRadius) - chunkMinZ - 1, 0);
        var maxZIndex = Math.min(VMath.floor(z + horizontalRadius) - chunkMinZ, 15);

        for (var xIndex = minXIndex; xIndex <= maxXIndex; xIndex++) {
            var worldX = chunkMinX + xIndex;
            var xd = (worldX + 0.5 - x) / horizontalRadius;

            for (var zIndex = minZIndex; zIndex <= maxZIndex; zIndex++) {
                var worldZ = chunkMinZ + zIndex;
                var zd = (worldZ + 0.5 - z) / horizontalRadius;
                if (xd * xd + zd * zd >= 1.0) {
                    continue;
                }

                for (var worldY = maxY; worldY > minY; worldY--) {
                    var yd = (worldY - 0.5 - y) / verticalRadius;
                    if (!skipChecker.shouldSkip(xd, yd, zd, worldY)) {
                        output.carve(xIndex, worldY, zIndex);
                    }
                }
            }
        }
    }

    static boolean canReach(
            int chunkX,
            int chunkZ,
            double x,
            double z,
            int currentStep,
            int totalSteps,
            float thickness) {
        double xMid = chunkX * 16 + 8;
        double zMid = chunkZ * 16 + 8;
        var xd = x - xMid;
        var zd = z - zMid;
        double remaining = totalSteps - currentStep;
        double rr = thickness + 2.0F + 16.0F;
        return xd * xd + zd * zd - remaining * remaining <= rr * rr;
    }

    interface CarveSkipChecker {
        boolean shouldSkip(double xd, double yd, double zd, int blockY);
    }
}
