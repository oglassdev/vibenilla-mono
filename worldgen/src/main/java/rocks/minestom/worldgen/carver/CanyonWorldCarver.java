package rocks.minestom.worldgen.carver;

import com.google.gson.JsonObject;
import rocks.minestom.worldgen.feature.valueproviders.FloatProvider;
import rocks.minestom.worldgen.random.LegacyRandomSource;
import rocks.minestom.worldgen.random.RandomSource;

/**
 * Exact port of vanilla {@code CanyonWorldCarver} (ravines), including the per-height width factors
 * and per-step radius jitter.
 */
public record CanyonWorldCarver(
        float probability, CarverHeightProvider y, FloatProvider verticalRotation, Shape shape)
        implements WorldCarver {

    public record Shape(
            FloatProvider distanceFactor,
            FloatProvider thickness,
            int widthSmoothness,
            FloatProvider horizontalRadiusFactor,
            float verticalRadiusDefaultFactor,
            float verticalRadiusCenterFactor,
            FloatProvider yScale) {
        static Shape fromJson(JsonObject json) {
            var widthSmoothness = json.get("width_smoothness").getAsInt();
            if (widthSmoothness < 1) {
                throw new IllegalArgumentException(
                        "width_smoothness must be positive: " + widthSmoothness);
            }
            return new Shape(
                    FloatProvider.fromJson(json.get("distance_factor")),
                    FloatProvider.fromJson(json.get("thickness")),
                    widthSmoothness,
                    FloatProvider.fromJson(json.get("horizontal_radius_factor")),
                    json.get("vertical_radius_default_factor").getAsFloat(),
                    json.get("vertical_radius_center_factor").getAsFloat(),
                    FloatProvider.fromJson(json.get("y_scale")));
        }
    }

    static CanyonWorldCarver fromJson(JsonObject json) {
        return new CanyonWorldCarver(
                json.get("probability").getAsFloat(),
                CarverHeightProvider.fromJson(json.get("y")),
                FloatProvider.fromJson(json.get("vertical_rotation")),
                Shape.fromJson(json.getAsJsonObject("shape")));
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
        double x = sourceChunkX * 16 + random.nextInt(16);
        var y = this.y.sample(random, range.minGenY(), range.maxGenYInclusive());
        double z = sourceChunkZ * 16 + random.nextInt(16);
        var horizontalRotation = random.nextFloat() * (float) (Math.PI * 2);
        var verticalRotation = this.verticalRotation.sample(random);
        double yScale = this.shape.yScale().sample(random);
        var thickness = this.shape.thickness().sample(random);
        var distance = (int) (maxDistance * this.shape.distanceFactor().sample(random));
        this.doCarve(
                range,
                chunkX,
                chunkZ,
                random.nextLong(),
                x,
                y,
                z,
                thickness,
                horizontalRotation,
                verticalRotation,
                0,
                distance,
                yScale,
                output);
        return true;
    }

    private void doCarve(
            GenerationRange range,
            int chunkX,
            int chunkZ,
            long tunnelSeed,
            double x,
            double y,
            double z,
            float thickness,
            float horizontalRotation,
            float verticalRotation,
            int step,
            int distance,
            double yScale,
            CarverOutput output) {
        var random = new LegacyRandomSource(tunnelSeed);
        var widthFactorPerHeight = this.initWidthFactors(range, random);
        var yRota = 0.0F;
        var xRota = 0.0F;

        for (var currentStep = step; currentStep < distance; currentStep++) {
            var horizontalRadius =
                    1.5 + CarverMath.sin(currentStep * (float) Math.PI / distance) * thickness;
            var verticalRadius = horizontalRadius * yScale;
            horizontalRadius *= this.shape.horizontalRadiusFactor().sample(random);
            verticalRadius =
                    this.updateVerticalRadius(random, verticalRadius, distance, currentStep);
            var xc = CarverMath.cos(verticalRotation);
            var xs = CarverMath.sin(verticalRotation);
            x += CarverMath.cos(horizontalRotation) * xc;
            y += xs;
            z += CarverMath.sin(horizontalRotation) * xc;
            verticalRotation *= 0.7F;
            verticalRotation += xRota * 0.05F;
            horizontalRotation += yRota * 0.05F;
            xRota *= 0.8F;
            yRota *= 0.5F;
            xRota += (random.nextFloat() - random.nextFloat()) * random.nextFloat() * 2.0F;
            yRota += (random.nextFloat() - random.nextFloat()) * random.nextFloat() * 4.0F;
            if (random.nextInt(4) != 0) {
                if (!WorldCarver.canReach(chunkX, chunkZ, x, z, currentStep, distance, thickness)) {
                    return;
                }

                WorldCarver.carveEllipsoid(
                        chunkX,
                        chunkZ,
                        x,
                        y,
                        z,
                        horizontalRadius,
                        verticalRadius,
                        output,
                        (xd, yd, zd, blockY) ->
                                this.shouldSkip(range, widthFactorPerHeight, xd, yd, zd, blockY));
            }
        }
    }

    private float[] initWidthFactors(GenerationRange range, RandomSource random) {
        var depth = range.genDepth();
        var widthFactorPerHeight = new float[depth];
        var widthFactor = 1.0F;

        for (var yIndex = 0; yIndex < depth; yIndex++) {
            if (yIndex == 0 || random.nextInt(this.shape.widthSmoothness()) == 0) {
                widthFactor = 1.0F + random.nextFloat() * random.nextFloat();
            }

            widthFactorPerHeight[yIndex] = widthFactor * widthFactor;
        }

        return widthFactorPerHeight;
    }

    private double updateVerticalRadius(
            RandomSource random, double verticalRadius, float distance, float currentStep) {
        var verticalMultiplier = 1.0F - Math.abs(0.5F - currentStep / distance) * 2.0F;
        var factor =
                this.shape.verticalRadiusDefaultFactor()
                        + this.shape.verticalRadiusCenterFactor() * verticalMultiplier;
        return factor * verticalRadius * (random.nextFloat() * (1.0F - 0.75F) + 0.75F);
    }

    private boolean shouldSkip(
            GenerationRange range,
            float[] widthFactorPerHeight,
            double xd,
            double yd,
            double zd,
            int blockY) {
        var yIndex = blockY - range.minGenY();
        return (xd * xd + zd * zd) * widthFactorPerHeight[yIndex - 1] + yd * yd / 6.0 >= 1.0;
    }
}
