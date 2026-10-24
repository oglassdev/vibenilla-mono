package rocks.minestom.worldgen.feature.configurations;

import com.google.gson.JsonElement;
import java.util.LinkedHashSet;
import java.util.Set;
import net.kyori.adventure.key.Key;
import rocks.minestom.worldgen.feature.UnknownTypes;
import rocks.minestom.worldgen.structure.context.BlockTagManager;

/**
 * Parses vanilla {@code HolderSet<Block>} JSON shapes: a single block id, a {@code #tag} reference
 * or a list of block ids, resolved to a set of block keys.
 */
public final class BlockSets {
    private BlockSets() {}

    public static Set<Key> parse(JsonElement json, BlockTagManager blockTags) {
        if (json.isJsonArray()) {
            var blocks = new LinkedHashSet<Key>();
            for (var element : json.getAsJsonArray()) {
                blocks.add(Key.key(element.getAsString()));
            }
            return Set.copyOf(blocks);
        }

        var value = json.getAsString();
        if (value.startsWith("#")) {
            return resolveTag(Key.key(value.substring(1)), blockTags);
        }

        return Set.of(Key.key(value));
    }

    public static Set<Key> resolveTag(Key tag, BlockTagManager blockTags) {
        if (blockTags == null) {
            throw new IllegalStateException(
                    "No block tags available to resolve #" + tag.asString());
        }

        Set<Key> blocks;
        synchronized (blockTags) {
            blocks = blockTags.blocks(tag);
        }

        if (blocks.isEmpty()) {
            UnknownTypes.report("block tag", tag.asString());
        }

        return blocks;
    }
}
