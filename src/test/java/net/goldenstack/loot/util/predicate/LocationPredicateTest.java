package net.goldenstack.loot.util.predicate;

import net.goldenstack.loot.util.VanillaInterface;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

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
}
