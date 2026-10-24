package net.goldenstack.loot.util.predicate;

import java.util.Map;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.predicate.PropertiesPredicate;
import net.minestom.server.instance.fluid.Fluid;
import net.minestom.server.registry.Registries;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.registry.RegistryTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record FluidPredicate(
        @Nullable RegistryTag<Fluid> fluids, @Nullable PropertiesPredicate state) {
    public static final @NotNull StructCodec<FluidPredicate> CODEC =
            StructCodec.struct(
                    "fluids",
                    RegistryTag.codec(Registries::fluid).optional(),
                    FluidPredicate::fluids,
                    "state",
                    PropertiesPredicate.CODEC.optional(),
                    FluidPredicate::state,
                    FluidPredicate::new);

    public boolean matches(@NotNull Instance instance, @NotNull Point point) {
        if (!instance.isChunkLoaded(point)) {
            return false;
        }

        var block = instance.getBlock(point);
        var fluid = fluidOf(block);
        if (this.fluids != null && !this.fluids.contains(RegistryKey.of(fluid.key()))) {
            return false;
        }

        if (this.state == null) {
            return true;
        }

        var properties = propertiesOf(block, fluid);
        for (var entry : this.state.properties().entrySet()) {
            var value = properties.get(entry.getKey());
            if (value == null || !entry.getValue().test(value)) {
                return false;
            }
        }
        return true;
    }

    private static @NotNull Fluid fluidOf(@NotNull Block block) {
        var key = block.key().asString();
        return switch (key) {
            case "minecraft:water" -> isSource(block) ? Fluid.WATER : Fluid.FLOWING_WATER;
            case "minecraft:lava" -> isSource(block) ? Fluid.LAVA : Fluid.FLOWING_LAVA;
            case "minecraft:bubble_column",
                    "minecraft:kelp",
                    "minecraft:kelp_plant",
                    "minecraft:seagrass",
                    "minecraft:tall_seagrass" ->
                    Fluid.WATER;
            default -> "true".equals(block.getProperty("waterlogged")) ? Fluid.WATER : Fluid.EMPTY;
        };
    }

    private static boolean isSource(@NotNull Block block) {
        return "0".equals(block.getProperty("level"));
    }

    private static @NotNull Map<String, String> propertiesOf(
            @NotNull Block block, @NotNull Fluid fluid) {
        if (fluid == Fluid.EMPTY) {
            return Map.of();
        }

        if (fluid == Fluid.WATER || fluid == Fluid.LAVA) {
            return Map.of("falling", "false");
        }

        var blockLevel = Integer.parseInt(block.getProperty("level"));
        var falling = blockLevel >= 8;
        return Map.of(
                "falling", String.valueOf(falling),
                "level", String.valueOf(falling ? 8 : 8 - blockLevel));
    }
}
