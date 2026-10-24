package rocks.minestom.worldgen.feature;

import java.util.Set;
import net.kyori.adventure.key.Key;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.structure.context.BlockTagManager;

/**
 * Access to the per-block {@code #minecraft:supports_<name>} block tags that carry most plant
 * survival rules, backed by one data pack's block tags.
 */
public final class BlockSupports {
    /** Support lookups for a context without a data pack: no tags exist. */
    public static final BlockSupports NONE = new BlockSupports(null);

    private final BlockTagManager blockTags;

    public BlockSupports(BlockTagManager blockTags) {
        this.blockTags = blockTags;
    }

    /** The backing block tag manager, or null when there is none. */
    public BlockTagManager manager() {
        return this.blockTags;
    }

    /** Whether the block is in the given block tag (resolved recursively). */
    public boolean isInTag(String tagName, Block block) {
        var members = this.members(Key.key(tagName));
        return members != null && members.contains(block.key());
    }

    /**
     * The support set for the given block state, or null when no {@code supports_<name>} tag exists
     * for it.
     */
    public Set<Key> supportsOf(Block block) {
        var supported = this.members(Key.key("minecraft:supports_" + block.key().value()));
        return supported == null || supported.isEmpty() ? null : supported;
    }

    private Set<Key> members(Key tag) {
        if (this.blockTags == null) {
            return null;
        }

        synchronized (this.blockTags) {
            return this.blockTags.blocks(tag);
        }
    }
}
