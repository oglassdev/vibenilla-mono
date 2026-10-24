package rocks.minestom.worldgen;

import java.util.Map;
import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.instance.block.Block;

/** A block state as a bare id string or an {@code {id, properties}} object. */
public final class BlockCodec {
    private BlockCodec() {}

    public static final Codec<Block> CODEC =
            StructCodec.struct(
                            "id",
                            Codec.KEY,
                            block -> {
                                throw new UnsupportedOperationException(
                                        "Encoding is not supported");
                            },
                            "properties",
                            Codec.STRING.mapValue(Codec.STRING).optional(Map.of()),
                            block -> {
                                throw new UnsupportedOperationException(
                                        "Encoding is not supported");
                            },
                            BlockCodec::create)
                    .orElse(Codec.KEY, name -> create(name, Map.of()));

    private static Block create(Key name, Map<String, String> properties) {
        var block = Block.fromKey(name);

        if (block == null) {
            throw new IllegalStateException("Unknown block key: " + name.asString());
        }

        return properties.isEmpty() ? block : block.withProperties(properties);
    }
}
