package rocks.minestom.worldgen.feature;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import net.minestom.server.instance.block.Block;

/** Vanilla {@code BlockState.trySetValue}-style helpers over Minestom's string properties. */
public final class BlockProperties {
    private static final ConcurrentHashMap<String, Set<String>> ALLOWED_VALUES =
            new ConcurrentHashMap<>();

    private BlockProperties() {}

    /**
     * Sets the property when the block has it and accepts the value; otherwise returns the block
     * unchanged.
     */
    public static Block trySet(Block block, String property, String value) {
        if (block.getProperty(property) == null) {
            return block;
        }

        var allowed =
                ALLOWED_VALUES.computeIfAbsent(
                        block.name() + "#" + property,
                        ignored ->
                                block.possibleStates().stream()
                                        .map(state -> state.getProperty(property))
                                        .collect(Collectors.toUnmodifiableSet()));
        return allowed.contains(value) ? block.withProperty(property, value) : block;
    }

    /** Vanilla {@code withPropertiesOf}: copies every property both blocks share. */
    public static Block withPropertiesOf(Block block, Block other) {
        var result = block;
        for (var entry : other.properties().entrySet()) {
            result = trySet(result, entry.getKey(), entry.getValue());
        }
        return result;
    }
}
