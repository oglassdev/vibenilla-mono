package rocks.minestom.worldgen.feature.treedecorators;

import java.util.ArrayList;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.BlockSupports;
import rocks.minestom.worldgen.feature.Direction;
import rocks.minestom.worldgen.feature.Features;

/**
 * Vanilla's {@code CreakingHeartDecorator}: with the configured probability, replaces one log that
 * is enclosed by logs on all six sides with a natural, dormant creaking heart.
 */
public record CreakingHeartDecorator(float probability, BlockSupports supports)
        implements TreeDecorator {
    public static final Codec<CreakingHeartDecorator> CODEC =
            StructCodec.struct(
                    "probability",
                    Codec.FLOAT,
                    CreakingHeartDecorator::probability,
                    probability ->
                            new CreakingHeartDecorator(
                                    probability, Features.currentBlockSupports()));

    @Override
    public void place(TreeDecorator.Context context) {
        var random = context.random();
        if (context.logs().isEmpty() || random.nextFloat() >= this.probability) {
            return;
        }

        var heartPlacements = new ArrayList<>(context.logs());
        TreeDecorator.shuffle(heartPlacements, random);
        for (var position : heartPlacements) {
            if (this.isEnclosedByLogs(context, position)) {
                context.setBlock(
                        position,
                        Block.CREAKING_HEART
                                .withProperty("creaking_heart_state", "dormant")
                                .withProperty("natural", "true"));
                return;
            }
        }
    }

    private boolean isEnclosedByLogs(
            TreeDecorator.Context context, net.minestom.server.coordinate.BlockVec position) {
        for (var direction : Direction.values()) {
            if (!context.checkBlock(
                    direction.relative(position),
                    block -> this.supports.isInTag("minecraft:logs", block))) {
                return false;
            }
        }

        return true;
    }
}
