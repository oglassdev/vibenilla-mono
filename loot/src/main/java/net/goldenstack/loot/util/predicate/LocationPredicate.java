package net.goldenstack.loot.util.predicate;

import java.util.List;
import java.util.Map;
import net.goldenstack.loot.LootContext;
import net.goldenstack.loot.util.LootNumberRange;
import net.goldenstack.loot.util.VanillaInterface;
import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.predicate.BlockPredicate;
import net.minestom.server.registry.Registries;
import net.minestom.server.registry.RegistryTag;
import net.minestom.server.world.biome.Biome;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("UnstableApiUsage")
public interface LocationPredicate {

    @NotNull BoundingBox SMOKE_POST = new BoundingBox(0.5, 1.0, 0.5, new Vec(0.25, 0.0, 0.25));

    @NotNull
    Codec<LocationPredicate> CODEC =
            Codec.ForwardRef(
                    () -> Properties.CODEC.transform(value -> value, value -> (Properties) value));

    boolean test(@NotNull Instance instance, @NotNull Point point);

    default boolean test(
            @NotNull Instance instance, @NotNull Point point, @NotNull VanillaInterface vanilla) {
        return this.test(instance, point);
    }

    record Properties(
            @Nullable Position position,
            @Nullable RegistryTag<Biome> biomes,
            @Nullable List<Key> structures,
            @Nullable Key dimension,
            @Nullable Boolean smokey,
            @Nullable LootNumberRange light,
            @Nullable BlockPredicate block,
            @Nullable FluidPredicate fluid,
            @Nullable Boolean canSeeSky)
            implements LocationPredicate {
        public static final @NotNull StructCodec<Properties> CODEC =
                StructCodec.struct(
                        "position",
                        Position.CODEC.optional(),
                        Properties::position,
                        "biomes",
                        RegistryTag.codec(Registries::biome).optional(),
                        Properties::biomes,
                        "structures",
                        Codec.KEY.listOrSingle().optional(),
                        Properties::structures,
                        "dimension",
                        Codec.KEY.optional(),
                        Properties::dimension,
                        "smokey",
                        Codec.BOOLEAN.optional(),
                        Properties::smokey,
                        "light",
                        LootNumberRange.CODEC.optional(),
                        Properties::light,
                        "block",
                        BlockPredicate.CODEC.optional(),
                        Properties::block,
                        "fluid",
                        FluidPredicate.CODEC.optional(),
                        Properties::fluid,
                        "can_see_sky",
                        Codec.BOOLEAN.optional(),
                        Properties::canSeeSky,
                        Properties::new);

        @Override
        public boolean test(@NotNull Instance instance, @NotNull Point point) {
            return this.test(instance, point, VanillaInterface.defaults());
        }

        @Override
        public boolean test(
                @NotNull Instance instance,
                @NotNull Point point,
                @NotNull VanillaInterface vanilla) {
            if (this.position != null && !this.position.test(point, vanilla)) {

                return false;
            }

            if (this.dimension != null
                    && !this.dimension.asString().equals(instance.getDimensionName())) {

                return false;
            }

            var needsLoadedChunk =
                    this.biomes != null
                            || this.structures != null
                            || this.smokey != null
                            || this.light != null
                            || this.block != null
                            || this.fluid != null;
            if (!needsLoadedChunk
                    && this.light == null
                    && this.block == null
                    && this.fluid == null
                    && this.canSeeSky == null) {

                return true;
            }

            if (needsLoadedChunk && !instance.isChunkLoaded(point)) {

                return false;
            }

            var block = this.block == null ? null : instance.getBlock(point);
            if (this.biomes != null
                    && !this.biomes.contains(
                            instance.getBiome(point.blockX(), point.blockY(), point.blockZ()))) {

                return false;
            }

            if (this.structures != null
                    && this.structures.stream()
                            .noneMatch(
                                    structure ->
                                            Boolean.TRUE.equals(
                                                    vanilla.locationStructure(
                                                            structure, instance, point)))) {

                return false;
            }

            if (this.smokey != null) {
                var smokey = vanilla.locationSmokey(instance, point);
                if (smokey == null) {
                    smokey = isSmokey(instance, point);
                }

                if (this.smokey != smokey) {

                    return false;
                }
            }

            if (this.light != null) {
                var context = LootContext.from(VanillaInterface.defaults(), Map.of());
                var lightLevel =
                        Math.max(
                                instance.getBlockLight(
                                        point.blockX(), point.blockY(), point.blockZ()),
                                instance.getSkyLight(
                                        point.blockX(), point.blockY(), point.blockZ()));

                if (!this.light.check(context, lightLevel)) {

                    return false;
                }
            }

            if (this.block != null && !this.block.test(block)) {

                return false;
            }

            if (this.fluid != null) {
                var fluidMatches = vanilla.locationFluid(this.fluid, instance, point);
                if (fluidMatches == null) {
                    fluidMatches = this.fluid.matches(instance, point);
                }

                if (!fluidMatches) {
                    return false;
                }
            }

            if (this.canSeeSky == null) {
                return true;
            }

            var canSeeSky = vanilla.locationCanSeeSky(instance, point);
            if (canSeeSky == null) {
                canSeeSky =
                        instance.getSkyLight(point.blockX(), point.blockY(), point.blockZ()) == 15;
            }

            return this.canSeeSky == canSeeSky;
        }

        private static boolean isSmokey(@NotNull Instance instance, @NotNull Point point) {
            for (var offset = 1; offset <= 5; offset++) {
                var block =
                        instance.getBlock(point.blockX(), point.blockY() - offset, point.blockZ());
                if (isLitCampfire(block)) {

                    return true;
                }

                if (block.collisionShape().intersectBox(Vec.ZERO, SMOKE_POST)) {
                    var belowBlock =
                            instance.getBlock(
                                    point.blockX(), point.blockY() - offset - 1, point.blockZ());
                    return isLitCampfire(belowBlock);
                }
            }

            return false;
        }

        private static boolean isLitCampfire(@NotNull Block block) {
            var key = block.key().asString();
            return (key.equals("minecraft:campfire") || key.equals("minecraft:soul_campfire"))
                    && Boolean.parseBoolean(block.getProperty("lit"));
        }
    }

    record Position(
            @NotNull LootNumberRange x, @NotNull LootNumberRange y, @NotNull LootNumberRange z) {
        public static final @NotNull StructCodec<Position> CODEC =
                StructCodec.struct(
                        "x",
                        LootNumberRange.CODEC.optional(new LootNumberRange(null, null)),
                        Position::x,
                        "y",
                        LootNumberRange.CODEC.optional(new LootNumberRange(null, null)),
                        Position::y,
                        "z",
                        LootNumberRange.CODEC.optional(new LootNumberRange(null, null)),
                        Position::z,
                        Position::new);

        public boolean test(@NotNull Point point) {
            return this.test(point, VanillaInterface.defaults());
        }

        public boolean test(@NotNull Point point, @NotNull VanillaInterface vanilla) {
            var context = LootContext.from(vanilla, Map.of());
            return this.x.check(context, point.x())
                    && this.y.check(context, point.y())
                    && this.z.check(context, point.z());
        }
    }
}
