package dev.oglass.vibenilla.ticking;

import java.util.random.RandomGenerator;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;

/** Vanilla {@code VineBlock} spread: vines creep around corners, upward and downward. */
final class Vines {
    private static final int SPREAD_RADIUS = 4;
    private static final int MAX_VINES_NEARBY = 4;
    private static final float TOP_SPREAD_ODDS = 0.05F;

    private Vines() {}

    static void register(RandomTicks ticks) {
        ticks.register(Block.VINE, Vines::tick);
    }

    static void tick(Instance instance, BlockVec position, Block block, RandomGenerator random) {
        if (random.nextInt(4) != 0) {
            return;
        }

        var face = BlockFace.values()[random.nextInt(BlockFace.values().length)];
        var horizontal = Level.HORIZONTAL.contains(face);
        if (horizontal && !has(block, face)) {
            if (canSpread(instance, position)) {
                spreadSideways(instance, position, block, face, random);
            }
            return;
        }

        var dimension = instance.getCachedDimensionType();
        if (face == BlockFace.TOP && position.blockY() < dimension.maxY() - 1) {
            if (canSupportAtFace(instance, position, BlockFace.TOP)) {
                Level.set(instance, position, with(block, BlockFace.TOP, true));
                return;
            }

            var above = position.add(0, 1, 0);
            if (Level.block(instance, above).air()) {
                if (canSpread(instance, position)) {
                    spreadUp(instance, above, block, random);
                }
                return;
            }
        }

        if (position.blockY() > dimension.minY()) {
            spreadDown(instance, position, block, random);
        }
    }

    private static void spreadSideways(
            Instance instance,
            BlockVec position,
            Block block,
            BlockFace face,
            RandomGenerator random) {
        var target = position.relative(face);
        if (!Level.block(instance, target).air()) {
            if (attachable(instance, target, face)) {
                Level.set(instance, position, with(block, face, true));
            }
            return;
        }

        var clockwise = turn(face, 1);
        var counterClockwise = turn(face, -1);
        var hasClockwise = has(block, clockwise);
        var hasCounterClockwise = has(block, counterClockwise);
        var clockwiseTarget = target.relative(clockwise);
        var counterClockwiseTarget = target.relative(counterClockwise);
        var opposite = face.getOppositeFace();
        if (hasClockwise && attachable(instance, clockwiseTarget, clockwise)) {
            Level.set(instance, target, only(clockwise));
        } else if (hasCounterClockwise
                && attachable(instance, counterClockwiseTarget, counterClockwise)) {
            Level.set(instance, target, only(counterClockwise));
        } else if (hasClockwise
                && Level.block(instance, clockwiseTarget).air()
                && attachable(instance, position.relative(clockwise), opposite)) {
            Level.set(instance, clockwiseTarget, only(opposite));
        } else if (hasCounterClockwise
                && Level.block(instance, counterClockwiseTarget).air()
                && attachable(instance, position.relative(counterClockwise), opposite)) {
            Level.set(instance, counterClockwiseTarget, only(opposite));
        } else if (random.nextFloat() < TOP_SPREAD_ODDS
                && attachable(instance, target.add(0, 1, 0), BlockFace.TOP)) {
            Level.set(instance, target, only(BlockFace.TOP));
        }
    }

    private static void spreadUp(
            Instance instance, BlockVec above, Block block, RandomGenerator random) {
        var grown = block;
        for (var face : Level.HORIZONTAL) {
            if (random.nextBoolean() || !attachable(instance, above.relative(face), face)) {
                grown = with(grown, face, false);
            }
        }
        if (hasHorizontal(grown)) {
            Level.set(instance, above, grown);
        }
    }

    private static void spreadDown(
            Instance instance, BlockVec position, Block block, RandomGenerator random) {
        var belowPosition = position.add(0, -1, 0);
        var below = Level.block(instance, belowPosition);
        if (!below.air() && !below.compare(Block.VINE)) {
            return;
        }

        var base = below.air() ? Block.VINE : below;
        var grown = base;
        for (var face : Level.HORIZONTAL) {
            if (random.nextBoolean() && has(block, face)) {
                grown = with(grown, face, true);
            }
        }
        if (!grown.equals(base) && hasHorizontal(grown)) {
            Level.set(instance, belowPosition, grown);
        }
    }

    /** At most {@value #MAX_VINES_NEARBY} vines may stand around the position. */
    private static boolean canSpread(Instance instance, BlockVec position) {
        var vines = 0;
        for (var x = -SPREAD_RADIUS; x <= SPREAD_RADIUS; x++) {
            for (var y = -1; y <= 1; y++) {
                for (var z = -SPREAD_RADIUS; z <= SPREAD_RADIUS; z++) {
                    if (Level.block(instance, position.add(x, y, z)).compare(Block.VINE)
                            && ++vines > MAX_VINES_NEARBY) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Whether the block at the position has a full face for a vine reaching toward {@code face}.
     */
    private static boolean attachable(Instance instance, BlockVec position, BlockFace face) {
        return canAttachTo(Level.block(instance, position), face);
    }

    /** Whether a vine at the position could hold on to the face: a full block or a vine above. */
    private static boolean canSupportAtFace(Instance instance, BlockVec position, BlockFace face) {
        if (face == BlockFace.BOTTOM) {
            return false;
        }
        if (attachable(instance, position.relative(face), face)) {
            return true;
        }
        if (face == BlockFace.TOP) {
            return false;
        }

        var above = Level.block(instance, position.add(0, 1, 0));
        return above.compare(Block.VINE) && has(above, face);
    }

    private static boolean canAttachTo(Block neighbor, BlockFace face) {
        return neighbor.collisionShape().isFaceFull(face.getOppositeFace());
    }

    private static BlockFace turn(BlockFace face, int steps) {
        var count = Level.HORIZONTAL.size();
        return Level.HORIZONTAL.get((Level.HORIZONTAL.indexOf(face) + steps + count) % count);
    }

    private static boolean hasHorizontal(Block block) {
        return Level.HORIZONTAL.stream().anyMatch(face -> has(block, face));
    }

    private static boolean has(Block block, BlockFace face) {
        return "true".equals(block.getProperty(property(face)));
    }

    private static Block with(Block block, BlockFace face, boolean value) {
        return block.withProperty(property(face), Boolean.toString(value));
    }

    private static Block only(BlockFace face) {
        return with(Block.VINE, face, true);
    }

    private static String property(BlockFace face) {
        return face == BlockFace.TOP ? "up" : face.name().toLowerCase();
    }
}
