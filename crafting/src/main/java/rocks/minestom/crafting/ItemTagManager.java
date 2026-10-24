package rocks.minestom.crafting;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.kyori.adventure.key.Key;
import net.minestom.server.item.Material;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Loads the vanilla item tag definitions and exposes them as resolved material sets.
 */
public final class ItemTagManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ItemTagManager.class);

    private final Map<Key, TagDefinition> definitions = new LinkedHashMap<>();
    private final Map<Key, Set<Material>> resolved = new IdentityHashMap<>();

    public ItemTagManager(Path tagDirectory) {
        this.loadDefinitions(tagDirectory);
    }

    private static Key toTagKey(Path tagFilePath) {
        var relativePath = tagFilePath;

        for (var segmentIndex = tagFilePath.getNameCount() - 1; segmentIndex >= 0; segmentIndex--) {
            if (tagFilePath.getName(segmentIndex).toString().equals("item")) {
                relativePath = tagFilePath.subpath(segmentIndex + 1, tagFilePath.getNameCount());
                break;
            }
        }

        var tagName = relativePath.toString()
                .replace('\\', '/')
                .replace(".json", "");

        return Key.key("minecraft", tagName);
    }

    private void loadDefinitions(Path tagDirectory) {
        if (!Files.exists(tagDirectory)) {
            LOGGER.warn("Item tag directory {} is missing, crafting tags will be empty.", tagDirectory);
            return;
        }

        try {
            Files.walk(tagDirectory)
                    .filter(Files::isRegularFile)
                    .filter(filePath -> filePath.toString().endsWith(".json"))
                    .sorted()
                    .forEach(this::readDefinition);
        } catch (IOException exception) {
            LOGGER.error("Failed to enumerate item tag directory {}", tagDirectory, exception);
        }
    }

    private void readDefinition(Path tagFilePath) {
        try (var reader = Files.newBufferedReader(tagFilePath)) {
            var tagObject = JsonParser.parseReader(reader).getAsJsonObject();
            var valuesArray = tagObject.getAsJsonArray("values");

            if (valuesArray == null) {
                return;
            }

            var rawValues = getRawValues(valuesArray);
            var tagKey = toTagKey(tagFilePath);
            this.definitions.put(tagKey, new TagDefinition(tagKey, rawValues));
        } catch (Exception exception) {
            LOGGER.error("Failed to parse item tag {}", tagFilePath, exception);
        }
    }

    private static @NotNull LinkedHashSet<String> getRawValues(JsonArray valuesArray) {
        var rawValues = new LinkedHashSet<String>();

        for (var valueElement : valuesArray) {
            if (valueElement.isJsonPrimitive()) {
                rawValues.add(valueElement.getAsString());
            } else if (valueElement.isJsonObject()) {
                var valueObject = valueElement.getAsJsonObject();

                if (valueObject.has("id")) {
                    rawValues.add(valueObject.get("id").getAsString());
                }
            }
        }

        return rawValues;
    }

    public Set<Material> materials(Key tagKey) {
        return this.resolved.computeIfAbsent(tagKey, this::resolve);
    }

    private Set<Material> resolve(Key tagKey) {
        var tagDefinition = this.definitions.get(tagKey);

        if (tagDefinition == null) {
            return Collections.emptySet();
        }

        var resolvedMaterials = new LinkedHashSet<Material>();

        for (var entry : tagDefinition.values()) {
            if (entry.startsWith("#")) {
                var childKey = Key.key(entry.substring(1));
                resolvedMaterials.addAll(this.resolve(childKey));
            } else {
                var material = Material.fromKey(entry);

                if (material == null) {
                    LOGGER.warn("Unknown material {} referenced in tag {}", entry, tagKey);
                    continue;
                }

                resolvedMaterials.add(material);
            }
        }

        return Collections.unmodifiableSet(resolvedMaterials);
    }

    private record TagDefinition(Key tagKey, Set<String> values) {
        TagDefinition(Key tagKey, Set<String> values) {
            this.tagKey = tagKey;
            this.values = Set.copyOf(values);
        }
    }
}
