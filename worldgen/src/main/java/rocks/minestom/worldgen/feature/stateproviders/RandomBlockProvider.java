package rocks.minestom.worldgen.feature.stateproviders;

import com.google.gson.JsonObject;
import java.util.List;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.BlockSets;
import rocks.minestom.worldgen.random.RandomSource;
import rocks.minestom.worldgen.structure.context.BlockTagManager;

/**
 * Provides the default state of a random block from a set. With an empty set nothing is provided
 * optionally and the already placed block is kept otherwise.
 */
public record RandomBlockProvider(List<Block> blocks) implements BlockStateProvider {
    public RandomBlockProvider(List<Block> blocks) {
        this.blocks = List.copyOf(blocks);
    }

    public static RandomBlockProvider fromJson(JsonObject object, BlockTagManager blockTags) {
        var blocks =
                BlockSets.parse(object.get("blocks"), blockTags).stream()
                        .map(
                                key -> {
                                    var block = Block.fromKey(key);
                                    if (block == null) {
                                        throw new IllegalStateException(
                                                "Unknown block key: " + key.asString());
                                    }
                                    return block;
                                })
                        .toList();
        return new RandomBlockProvider(blocks);
    }

    @Override
    public Block getState(RandomSource random, BlockVec position) {
        var state = this.randomState(random);
        return state != null ? state : Block.AIR;
    }

    @Override
    public Block getState(Block.Getter accessor, RandomSource random, BlockVec position) {
        var state = this.randomState(random);
        return state != null ? state : accessor.getBlock(position);
    }

    @Override
    public Block getOptionalState(Block.Getter accessor, RandomSource random, BlockVec position) {
        return this.randomState(random);
    }

    private Block randomState(RandomSource random) {
        return this.blocks.isEmpty() ? null : this.blocks.get(random.nextInt(this.blocks.size()));
    }
}
