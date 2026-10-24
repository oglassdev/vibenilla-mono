package rocks.minestom.worldgen.feature.treedecorators;

import java.util.ArrayList;
import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.FeatureLoader;
import rocks.minestom.worldgen.feature.Features;

/**
 * Vanilla's {@code PaleMossDecorator}: a pale moss patch around the lowest log, plus hanging pale
 * moss below logs and leaves.
 */
public record PaleMossDecorator(
        float leavesProbability,
        float trunkProbability,
        float groundProbability,
        FeatureLoader loader)
        implements TreeDecorator {
    private static final Key PALE_MOSS_PATCH = Key.key("minecraft:pale_moss_patch");

    public static final Codec<PaleMossDecorator> CODEC =
            StructCodec.struct(
                    "leaves_probability",
                    Codec.FLOAT,
                    PaleMossDecorator::leavesProbability,
                    "trunk_probability",
                    Codec.FLOAT,
                    PaleMossDecorator::trunkProbability,
                    "ground_probability",
                    Codec.FLOAT,
                    PaleMossDecorator::groundProbability,
                    (leaves, trunk, ground) ->
                            new PaleMossDecorator(leaves, trunk, ground, Features.currentLoader()));

    @Override
    public void place(TreeDecorator.Context context) {
        var random = context.random();
        var shuffledLogs = new ArrayList<>(context.logs());
        TreeDecorator.shuffle(shuffledLogs, random);
        if (shuffledLogs.isEmpty()) {
            return;
        }

        var origin = shuffledLogs.getFirst();
        for (var log : shuffledLogs) {
            if (log.blockY() < origin.blockY()) {
                origin = log;
            }
        }

        if (random.nextFloat() < this.groundProbability && this.loader != null) {
            var mossPatch = this.loader.getConfiguredFeature(PALE_MOSS_PATCH);
            if (mossPatch != null) {
                context.placeFeature(mossPatch, origin.add(0, 1, 0));
            }
        }

        for (var log : context.logs()) {
            this.hangMossBelow(context, log, this.trunkProbability);
        }

        for (var leaf : context.leaves()) {
            this.hangMossBelow(context, leaf, this.leavesProbability);
        }
    }

    private void hangMossBelow(
            TreeDecorator.Context context, BlockVec position, float probability) {
        if (context.random().nextFloat() < probability) {
            var below = position.add(0, -1, 0);
            if (context.isAir(below)) {
                addMossHanger(below, context);
            }
        }
    }

    private static void addMossHanger(BlockVec position, TreeDecorator.Context context) {
        while (context.isAir(position.add(0, -1, 0)) && !(context.random().nextFloat() < 0.5F)) {
            context.setBlock(position, Block.PALE_HANGING_MOSS.withProperty("tip", "false"));
            position = position.add(0, -1, 0);
        }

        context.setBlock(position, Block.PALE_HANGING_MOSS.withProperty("tip", "true"));
    }
}
