package dev.oglass.vibenilla.containers;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.kyori.adventure.key.Key;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.loot.number.ResolvableInt;

/**
 * Burn times and cooking speeds of furnace fuels. Items carry a {@code cooking_fuel} component that
 * references the vanilla {@code context_int_provider} and {@code context_float_provider} data,
 * which this resolves for furnaces that cook fast (smokers and blast furnaces) or at normal speed.
 */
public final class CookingFuels {
    private static final Key DEFAULT_SPEED = Key.key("cooking/speed_default");

    private final Map<Key, JsonElement> providers = new HashMap<>();

    /** Loads the providers under {@code dataDirectory}, the {@code data/minecraft} directory. */
    public CookingFuels(Path dataDirectory) {
        for (var type : new String[] {"context_int_provider", "context_float_provider"}) {
            var directory = dataDirectory.resolve(type);

            if (!Files.isDirectory(directory)) {
                continue;
            }

            try (var paths = Files.walk(directory)) {
                for (var path : (Iterable<Path>) paths.filter(Files::isRegularFile)::iterator) {
                    var name = directory.relativize(path).toString().replace('\\', '/');
                    var key =
                            Key.key(
                                    "minecraft",
                                    name.substring(0, name.length() - ".json".length()));

                    try (var reader = Files.newBufferedReader(path)) {
                        providers.put(key, JsonParser.parseReader(reader));
                    }
                }
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }
    }

    /** Returns how many ticks the item burns for, or 0 if it is not a fuel. */
    public int burnTime(ItemStack item, boolean fast) {
        var fuel = item.get(DataComponents.COOKING_FUEL);

        if (fuel == null) {
            return 0;
        }

        return (int)
                switch (fuel.burnTime()) {
                    case ResolvableInt.Constant constant -> constant.value();
                    case ResolvableInt.Reference reference -> resolve(reference.key(), fast);
                    default -> throw new IllegalStateException("Unknown burn time " + fuel);
                };
    }

    public boolean isFuel(ItemStack item) {
        return item.has(DataComponents.COOKING_FUEL);
    }

    /** Returns the progress gained per tick while a fuel burns. */
    public float speed(boolean fast) {
        return (float) resolve(DEFAULT_SPEED, fast);
    }

    private double resolve(Key key, boolean fast) {
        var node = providers.get(key);

        if (node == null) {
            throw new IllegalStateException("Missing provider " + key);
        }
        return evaluate(node, fast);
    }

    private double evaluate(JsonElement node, boolean fast) {
        if (node.isJsonPrimitive()) {
            var primitive = node.getAsJsonPrimitive();
            return primitive.isNumber()
                    ? primitive.getAsDouble()
                    : resolve(Key.key(primitive.getAsString()), fast);
        }

        var object = node.getAsJsonObject();
        var type = object.get("type").getAsString();

        return switch (type) {
            case "minecraft:div" ->
                    evaluate(object.get("left"), fast) / evaluate(object.get("right"), fast);
            case "minecraft:conditional" ->
                    evaluate(object.get(fast ? "on_true" : "on_false"), fast);
            default -> throw new IllegalStateException("Unsupported provider type " + type);
        };
    }
}
