package net.goldenstack.loot.util.predicate;

import net.goldenstack.loot.util.VanillaInterface;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.registry.RegistryTag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LocationPredicateTest {

    @Test
    void skyVisibilityDoesNotRequireLoadedChunk() {
        MinecraftServer.init();

        var instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        var vanilla = (VanillaInterface) Proxy.newProxyInstance(
                VanillaInterface.class.getClassLoader(),
                new Class<?>[]{VanillaInterface.class},
                (proxy, method, arguments) -> method.getName().equals("locationCanSeeSky") ? true : null
        );
        var predicate = new LocationPredicate.Properties(null, null, null, null, null, null, null, null, true);

        assertTrue(predicate.test(instance, new Pos(0.0, 0.0, 0.0), vanilla));
    }

    @Test
    void fluidPredicatesUseVanillaFluidState() {
        MinecraftServer.init();

        var instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        var observedPredicate = new AtomicReference<FluidPredicate>();
        var vanilla = (VanillaInterface) Proxy.newProxyInstance(
                VanillaInterface.class.getClassLoader(),
                new Class<?>[]{VanillaInterface.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("locationFluid")) {
                        observedPredicate.set((FluidPredicate) arguments[0]);
                        return true;
                    }

                    return method.getName().equals("locationCanSeeSky") ? true : null;
                }
        );
        var fluid = new FluidPredicate(
                RegistryTag.direct(RegistryKey.unsafeOf("minecraft:water")),
                null
        );
        var predicate = new LocationPredicate.Properties(null, null, null, null, null, null, null, fluid, true);

        assertTrue(predicate.test(instance, new Pos(0.0, 0.0, 0.0), vanilla));
        assertTrue(observedPredicate.get() == fluid);
    }
}
