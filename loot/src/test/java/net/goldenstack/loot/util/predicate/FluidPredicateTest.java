package net.goldenstack.loot.util.predicate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.predicate.PropertiesPredicate;
import net.minestom.server.instance.fluid.Fluid;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.registry.RegistryTag;
import org.junit.jupiter.api.Test;

class FluidPredicateTest {

    @Test
    void matchesFluidIdentityAndState() {
        MinecraftServer.init();

        var instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.loadChunk(0, 0).join();
        var position = new Pos(0.0, 40.0, 0.0);
        instance.setBlock(position, Block.WATER.withProperty("level", "3"));

        var flowingWater = RegistryTag.direct(RegistryKey.<Fluid>of(Fluid.FLOWING_WATER.key()));
        var lava = RegistryTag.direct(RegistryKey.<Fluid>of(Fluid.LAVA.key()));
        var level5 = new PropertiesPredicate(Map.of("level", exact("5")));

        assertTrue(new FluidPredicate(flowingWater, null).matches(instance, position));
        assertTrue(new FluidPredicate(flowingWater, level5).matches(instance, position));
        assertFalse(new FluidPredicate(lava, null).matches(instance, position));
        assertFalse(
                new FluidPredicate(null, new PropertiesPredicate(Map.of("level", exact("1"))))
                        .matches(instance, position));
    }

    private static PropertiesPredicate.ValuePredicate exact(String value) {
        return new PropertiesPredicate.ValuePredicate.Exact(value);
    }
}
