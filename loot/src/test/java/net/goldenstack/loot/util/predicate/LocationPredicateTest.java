package net.goldenstack.loot.util.predicate;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;
import net.goldenstack.loot.util.VanillaInterface;
import net.kyori.adventure.key.Key;
import net.minestom.server.MinecraftServer;
import net.minestom.server.adventure.MinestomAdventure;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.block.Block;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.registry.RegistryTag;
import org.junit.jupiter.api.Test;

class LocationPredicateTest {

    @Test
    void skyVisibilityDoesNotRequireLoadedChunk() {
        MinecraftServer.init();

        var instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        var vanilla =
                (VanillaInterface)
                        Proxy.newProxyInstance(
                                VanillaInterface.class.getClassLoader(),
                                new Class<?>[] {VanillaInterface.class},
                                (proxy, method, arguments) ->
                                        method.getName().equals("locationCanSeeSky") ? true : null);
        var predicate =
                new LocationPredicate.Properties(
                        null, null, null, null, null, null, null, null, true);

        assertTrue(predicate.test(instance, new Pos(0.0, 0.0, 0.0), vanilla));
    }

    @Test
    void fluidPredicatesUseVanillaFluidState() {
        MinecraftServer.init();

        var instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.loadChunk(0, 0).join();
        var observedPredicate = new AtomicReference<FluidPredicate>();
        var vanilla =
                (VanillaInterface)
                        Proxy.newProxyInstance(
                                VanillaInterface.class.getClassLoader(),
                                new Class<?>[] {VanillaInterface.class},
                                (proxy, method, arguments) -> {
                                    if (method.getName().equals("locationFluid")) {
                                        observedPredicate.set((FluidPredicate) arguments[0]);
                                        return true;
                                    }

                                    return method.getName().equals("locationCanSeeSky")
                                            ? true
                                            : null;
                                });
        var fluid = new FluidPredicate(RegistryTag.direct(RegistryKey.of("minecraft:water")), null);
        var predicate =
                new LocationPredicate.Properties(
                        null, null, null, null, null, null, null, fluid, true);

        assertTrue(predicate.test(instance, new Pos(0.0, 0.0, 0.0), vanilla));
        assertTrue(observedPredicate.get() == fluid);
    }

    @Test
    void decodesSingleVanillaStructureSelectors() throws Exception {
        MinecraftServer.init();

        var input = MinestomAdventure.tagStringIO().asTag("{structures: \"minecraft:village\"}");
        var predicate =
                LocationPredicate.Properties.CODEC.decode(Transcoder.NBT, input).orElseThrow();

        assertTrue(predicate.structures().contains(Key.key("minecraft:village")));
    }

    @Test
    void smokeyFallbackRecognizesLitCampfires() {
        MinecraftServer.init();

        var instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.loadChunk(0, 0).join();
        instance.setBlock(
                0, -1, 0, Block.fromKey(Key.key("minecraft:campfire")).withProperty("lit", "true"));
        var predicate =
                new LocationPredicate.Properties(
                        null, null, null, null, true, null, null, null, null);

        assertTrue(predicate.test(instance, new Pos(0.0, 0.0, 0.0)));
    }
}
