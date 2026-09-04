package net.goldenstack.loot.util.predicate;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.predicate.PropertiesPredicate;
import net.minestom.server.instance.fluid.Fluid;
import net.minestom.server.registry.Registries;
import net.minestom.server.registry.RegistryTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record FluidPredicate(@Nullable RegistryTag<Fluid> fluids,
                             @Nullable PropertiesPredicate state) {
    public static final @NotNull StructCodec<FluidPredicate> CODEC = StructCodec.struct(
            "fluids", RegistryTag.codec(Registries::fluid).optional(), FluidPredicate::fluids,
            "state", PropertiesPredicate.CODEC.optional(), FluidPredicate::state,
            FluidPredicate::new
    );

    public boolean matches(@NotNull Instance instance, @NotNull Point point) {
        if (!instance.getBlock(point).fluid()) {
            return false;
        }

        return this.fluids == null && this.state == null;
    }
}
