package rocks.minestom.worldgen;

import java.util.Map;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.instance.block.Block;

public final class BlockCodec {
    private BlockCodec() {}

    public static final Codec<Block> CODEC =
            StructCodec.struct(
                    "Name",
                    Codec.KEY,
                    block -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    "Properties",
                    Codec.STRING.mapValue(Codec.STRING).optional(Map.of()),
                    block -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    (name, properties) -> {
                        var block = Block.fromKey(name);

                        if (block == null) {
                            throw new IllegalStateException(
                                    "Unknown block key: " + name.asString());
                        }

                        if (!properties.isEmpty()) {
                            block = block.withProperties(properties);
                        }

                        return block;
                    });
}
