package rocks.minestom.worldgen.carver;

import com.google.gson.JsonObject;
import rocks.minestom.worldgen.feature.valueproviders.ConstantFloatProvider;
import rocks.minestom.worldgen.feature.valueproviders.FloatProvider;
import rocks.minestom.worldgen.feature.valueproviders.IntProvider;
import rocks.minestom.worldgen.random.LegacyRandomSource;
import rocks.minestom.worldgen.random.RandomSource;

/**
 * Exact port of vanilla {@code CaveWorldCarver}. All random draws happen in the vanilla order;
 * tunnels get their own legacy-LCG random seeded from the carve random so their shapes are stable
 * regardless of the target chunk. The nether cave is this carver with wider, taller tunnels.
 */
public record CaveWorldCarver(
        float probability,
        CarverHeightProvider y,
        IntProvider count,
        FloatProvider thickness,
        boolean weirdThicknessBias,
        FloatProvider roomVerticalRadiusMultiplier,
        FloatProvider horizontalRadiusMultiplier,
        FloatProvider verticalRadiusMultiplier,
        FloatProvider startVerticalRadiusMultiplier,
        FloatProvider floorLevel)
        implements WorldCarver {
    private static final FloatProvider DEFAULT_START_VERTICAL_RADIUS_MULTIPLIER =
            new ConstantFloatProvider(1.0F);

    static CaveWorldCarver fromJson(JsonObject json) {
        return new CaveWorldCarver(
                json.get("probability").getAsFloat(),
                CarverHeightProvider.fromJson(json.get("y")),
                CarverLoader.parseCount(json.get("count")),
                FloatProvider.fromJson(json.get("thickness")),
                json.has("weird_thickness_bias") && json.get("weird_thickness_bias").getAsBoolean(),
                FloatProvider.fromJson(json.get("room_vertical_radius_multiplier")),
                FloatProvider.fromJson(json.get("horizontal_radius_multiplier")),
                FloatProvider.fromJson(json.get("vertical_radius_multiplier")),
                json.has("start_vertical_radius_multiplier")
                        ? FloatProvider.fromJson(json.get("start_vertical_radius_multiplier"))
                        : DEFAULT_START_VERTICAL_RADIUS_MULTIPLIER,
                FloatProvider.fromJson(json.get("floor_level")));
    }

    @Override
    public boolean isStartChunk(RandomSource random) {
        return random.nextFloat() <= this.probability;
    }

    @Override
    public boolean carve(
            GenerationRange range,
            RandomSource random,
            int chunkX,
            int chunkZ,
            int sourceChunkX,
            int sourceChunkZ,
            CarverOutput output) {
        var maxDistance = (this.getRange() * 2 - 1) * 16;
        var caveCount = this.count.sample(random);

        for (var cave = 0; cave < caveCount; cave++) {
            double x = sourceChunkX * 16 + random.nextInt(16);
            double y = this.y.sample(random, range.minGenY(), range.maxGenYInclusive());
            double z = sourceChunkZ * 16 + random.nextInt(16);
            double horizontalRadiusMultiplier = this.horizontalRadiusMultiplier.sample(random);
            double verticalRadiusMultiplier = this.verticalRadiusMultiplier.sample(random);
            double startVerticalRadiusMultiplier =
                    this.startVerticalRadiusMultiplier.sample(random);
            double floorLevel = this.floorLevel.sample(random);
            WorldCarver.CarveSkipChecker skipChecker =
                    (xd, yd, zd, blockY) -> shouldSkip(xd, yd, zd, floorLevel);
            var tunnels = 1;
            if (random.nextInt(4) == 0) {
                double yScale = this.roomVerticalRadiusMultiplier.sample(random);
                var thickness = 1.0F + random.nextFloat() * 6.0F;
                this.createRoom(chunkX, chunkZ, x, y, z, thickness, yScale, output, skipChecker);
                tunnels += random.nextInt(4);
            }

            for (var tunnel = 0; tunnel < tunnels; tunnel++) {
                var horizontalRotation = random.nextFloat() * (float) (Math.PI * 2);
                var verticalRotation = (random.nextFloat() - 0.5F) / 4.0F;
                var thickness = this.getThickness(random);
                var distance = maxDistance - random.nextInt(maxDistance / 4);
                this.createTunnel(
                        chunkX,
                        chunkZ,
                        random.nextLong(),
                        x,
                        y,
                        z,
                        horizontalRadiusMultiplier,
                        verticalRadiusMultiplier,
                        thickness,
                        horizontalRotation,
                        verticalRotation,
                        0,
                        distance,
                        startVerticalRadiusMultiplier,
                        output,
                        skipChecker);
            }
        }

        return true;
    }

    private float getThickness(RandomSource random) {
        var thickness = this.thickness.sample(random);
        if (this.weirdThicknessBias && random.nextInt(10) == 0) {
            thickness *= random.nextFloat() * random.nextFloat() * 3.0F + 1.0F;
        }

        return thickness;
    }

    private void createRoom(
            int chunkX,
            int chunkZ,
            double x,
            double y,
            double z,
            float thickness,
            double yScale,
            CarverOutput output,
            WorldCarver.CarveSkipChecker skipChecker) {
        var horizontalRadius = 1.5 + CarverMath.sin((float) (Math.PI / 2)) * thickness;
        var verticalRadius = horizontalRadius * yScale;
        WorldCarver.carveEllipsoid(
                chunkX,
                chunkZ,
                x + 1.0,
                y,
                z,
                horizontalRadius,
                verticalRadius,
                output,
                skipChecker);
    }

    private void createTunnel(
            int chunkX,
            int chunkZ,
            long tunnelSeed,
            double x,
            double y,
            double z,
            double horizontalRadiusMultiplier,
            double verticalRadiusMultiplier,
            float thickness,
            float horizontalRotation,
            float verticalRotation,
            int step,
            int dist,
            double yScale,
            CarverOutput output,
            WorldCarver.CarveSkipChecker skipChecker) {
        var random = new LegacyRandomSource(tunnelSeed);
        var splitPoint = random.nextInt(dist / 2) + dist / 4;
        var steep = random.nextInt(6) == 0;
        var yRota = 0.0F;
        var xRota = 0.0F;

        for (var currentStep = step; currentStep < dist; currentStep++) {
            var horizontalRadius =
                    1.5 + CarverMath.sin((float) Math.PI * currentStep / dist) * thickness;
            var verticalRadius = horizontalRadius * yScale;
            var cosX = CarverMath.cos(verticalRotation);
            x += CarverMath.cos(horizontalRotation) * cosX;
            y += CarverMath.sin(verticalRotation);
            z += CarverMath.sin(horizontalRotation) * cosX;
            verticalRotation *= steep ? 0.92F : 0.7F;
            verticalRotation += xRota * 0.1F;
            horizontalRotation += yRota * 0.1F;
            xRota *= 0.9F;
            yRota *= 0.75F;
            xRota += (random.nextFloat() - random.nextFloat()) * random.nextFloat() * 2.0F;
            yRota += (random.nextFloat() - random.nextFloat()) * random.nextFloat() * 4.0F;
            if (currentStep == splitPoint && thickness > 1.0F) {
                this.createTunnel(
                        chunkX,
                        chunkZ,
                        random.nextLong(),
                        x,
                        y,
                        z,
                        horizontalRadiusMultiplier,
                        verticalRadiusMultiplier,
                        random.nextFloat() * 0.5F + 0.5F,
                        horizontalRotation - (float) (Math.PI / 2),
                        verticalRotation / 3.0F,
                        currentStep,
                        dist,
                        1.0,
                        output,
                        skipChecker);
                this.createTunnel(
                        chunkX,
                        chunkZ,
                        random.nextLong(),
                        x,
                        y,
                        z,
                        horizontalRadiusMultiplier,
                        verticalRadiusMultiplier,
                        random.nextFloat() * 0.5F + 0.5F,
                        horizontalRotation + (float) (Math.PI / 2),
                        verticalRotation / 3.0F,
                        currentStep,
                        dist,
                        1.0,
                        output,
                        skipChecker);
                return;
            }

            if (random.nextInt(4) != 0) {
                if (!WorldCarver.canReach(chunkX, chunkZ, x, z, currentStep, dist, thickness)) {
                    return;
                }

                WorldCarver.carveEllipsoid(
                        chunkX,
                        chunkZ,
                        x,
                        y,
                        z,
                        horizontalRadius * horizontalRadiusMultiplier,
                        verticalRadius * verticalRadiusMultiplier,
                        output,
                        skipChecker);
            }
        }
    }

    private static boolean shouldSkip(double xd, double yd, double zd, double floorLevel) {
        return yd <= floorLevel || xd * xd + yd * yd + zd * zd >= 1.0;
    }
}
