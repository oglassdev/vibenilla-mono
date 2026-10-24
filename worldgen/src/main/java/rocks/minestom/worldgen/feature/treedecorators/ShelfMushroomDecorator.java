package rocks.minestom.worldgen.feature.treedecorators;

import java.util.List;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.Direction;
import rocks.minestom.worldgen.random.RandomSource;

/**
 * Port of vanilla {@code ShelfMushroomDecorator}: shelf mushrooms on the sides of standing tree
 * trunks between one and four blocks above the base, or along both sides of a fallen log.
 */
public record ShelfMushroomDecorator(float probability) implements TreeDecorator {
    public static final Codec<ShelfMushroomDecorator> CODEC =
            StructCodec.struct(
                    "probability",
                    Codec.FLOAT,
                    ShelfMushroomDecorator::probability,
                    ShelfMushroomDecorator::new);

    private static final float PER_SIDE_PLACEMENT_CHANCE = 0.25F;

    @Override
    public void place(TreeDecorator.Context context) {
        var random = context.random();
        if (random.nextFloat() >= this.probability) {
            return;
        }

        var logs = context.logs();
        if (logs.isEmpty()) {
            return;
        }

        if (logs.getFirst().blockY() == logs.getLast().blockY()) {
            placeOnFallenLog(context, logs, random);
        } else {
            placeOnStandingTree(context, logs, random);
        }
    }

    private static void placeOnStandingTree(
            TreeDecorator.Context context, List<BlockVec> logs, RandomSource random) {
        var first = Direction.HORIZONTAL.get(random.nextInt(Direction.HORIZONTAL.size()));
        var directions = new Direction[] {first, first.getClockWise()};
        var treeBaseY = logs.getFirst().blockY();

        for (var logPosition : logs) {
            var dy = logPosition.blockY() - treeBaseY;
            if (dy < 1 || dy > 4) {
                continue;
            }

            for (var facing : directions) {
                if (random.nextFloat() > PER_SIDE_PLACEMENT_CHANCE) {
                    continue;
                }

                var mushroom = facing.relative(logPosition);
                if (isBlockReplaceableWithShelfMushroom(context, mushroom)
                        && !hasShelfMushroomAt(context, mushroom.sub(0, 1, 0))) {
                    placeMushroom(context, mushroom, facing, random);
                    break;
                }
            }
        }
    }

    private static void placeOnFallenLog(
            TreeDecorator.Context context, List<BlockVec> logs, RandomSource random) {
        var first = logs.getFirst();
        var last = logs.getLast();
        var directions =
                first.blockX() != last.blockX()
                        ? new Direction[] {Direction.NORTH, Direction.SOUTH}
                        : new Direction[] {Direction.EAST, Direction.WEST};

        for (var logPosition : logs) {
            for (var facing : directions) {
                if (random.nextFloat() > PER_SIDE_PLACEMENT_CHANCE) {
                    continue;
                }

                var mushroom = facing.relative(logPosition);
                if (isBlockReplaceableWithShelfMushroom(context, mushroom)
                        && !hasHorizontallyAdjacentShelfMushroom(context, mushroom)
                        && !hasHorizontallyAdjacentShelfMushroom(context, logPosition)) {
                    placeMushroom(context, mushroom, facing, random);
                }
            }
        }
    }

    private static void placeMushroom(
            TreeDecorator.Context context,
            BlockVec position,
            Direction facing,
            RandomSource random) {
        context.setBlock(
                position,
                Block.SHELF_MUSHROOM
                        .withProperty("age", Integer.toString(random.nextInt(2)))
                        .withProperty("facing", facing.serializedName()));
    }

    private static boolean hasShelfMushroomAt(TreeDecorator.Context context, BlockVec position) {
        return context.checkBlock(position, block -> block.compare(Block.SHELF_MUSHROOM));
    }

    private static boolean hasHorizontallyAdjacentShelfMushroom(
            TreeDecorator.Context context, BlockVec position) {
        for (var direction : Direction.HORIZONTAL) {
            if (hasShelfMushroomAt(context, direction.relative(position))) {
                return true;
            }
        }

        return false;
    }

    private static boolean isBlockReplaceableWithShelfMushroom(
            TreeDecorator.Context context, BlockVec position) {
        return context.isReplaceable(position) && !context.isWaterOrWaterNearby(position);
    }
}
