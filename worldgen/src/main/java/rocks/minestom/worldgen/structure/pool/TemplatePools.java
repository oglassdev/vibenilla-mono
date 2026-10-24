package rocks.minestom.worldgen.structure.pool;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.key.Key;
import rocks.minestom.worldgen.structure.loader.StructureLoader;
import rocks.minestom.worldgen.structure.template.LiquidSettings;

public final class TemplatePools {
    private TemplatePools() {}

    /** Parses a template pool, failing on unknown element types and unresolvable processors. */
    public static TemplatePool parseTemplatePool(JsonElement json, StructureLoader loader) {
        var obj = json.getAsJsonObject();
        var fallback = Key.key(obj.get("fallback").getAsString());
        var elements = new ArrayList<TemplatePool.PoolElementEntry>();

        for (var entryTag : obj.getAsJsonArray("elements")) {
            var entry = entryTag.getAsJsonObject();
            elements.add(
                    new TemplatePool.PoolElementEntry(
                            parseElement(entry.getAsJsonObject("element"), loader),
                            entry.get("weight").getAsInt()));
        }

        return new TemplatePool(List.copyOf(elements), fallback);
    }

    private static PoolElement parseElement(JsonObject obj, StructureLoader loader) {
        var type = Key.key(obj.get("element_type").getAsString()).asString();
        if (type.equals("minecraft:empty_pool_element")) {
            return EmptyPoolElement.INSTANCE;
        }

        var projection = Projection.fromName(obj.get("projection").getAsString());
        return switch (type) {
            case "minecraft:legacy_single_pool_element", "minecraft:single_pool_element" -> {
                var overrideLiquidSettings =
                        obj.has("override_liquid_settings")
                                ? LiquidSettings.fromName(
                                        obj.get("override_liquid_settings").getAsString())
                                : null;
                yield new SinglePoolElement(
                        Key.key(obj.get("location").getAsString()),
                        loader.resolveProcessors(obj.get("processors")),
                        projection,
                        overrideLiquidSettings,
                        type.equals("minecraft:legacy_single_pool_element"));
            }
            case "minecraft:feature_pool_element" ->
                    new FeaturePoolElement(Key.key(obj.get("feature").getAsString()), projection);
            case "minecraft:list_pool_element" -> {
                var children = new ArrayList<PoolElement>();
                for (var childTag : obj.getAsJsonArray("elements")) {
                    children.add(parseElement(childTag.getAsJsonObject(), loader));
                }
                yield new ListPoolElement(List.copyOf(children), projection);
            }
            default -> throw new IllegalArgumentException("Unsupported pool element type: " + type);
        };
    }
}
