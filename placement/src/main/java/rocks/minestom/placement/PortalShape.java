package rocks.minestom.placement;

import java.util.ArrayList;
import java.util.List;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;

/**
 * Vanilla {@code PortalShape}: an obsidian frame with a 2x3 to 21x21 interior in either vertical
 * plane. Positions are read through the getter; unloaded and out of world positions read as solid.
 */
public final class PortalShape {
    private static final int MIN_WIDTH = 2;
    private static final int MAX_WIDTH = 21;
    private static final int MIN_HEIGHT = 3;
    private static final int MAX_HEIGHT = 21;

    /** The horizontal axis a portal extends along. */
    public enum Axis {
        X("x", -1, 0),
        Z("z", 0, 1);

        private final String property;
        private final int rightX;
        private final int rightZ;

        Axis(String property, int rightX, int rightZ) {
            this.property = property;
            this.rightX = rightX;
            this.rightZ = rightZ;
        }

        public String property() {
            return this.property;
        }

        public Axis other() {
            return this == X ? Z : X;
        }

        /** The axis stored in a {@code nether_portal} block. */
        public static Axis of(Block portal) {
            return "z".equals(portal.getProperty("axis")) ? Z : X;
        }
    }

    private final Block.Getter level;
    private final Axis axis;
    private BlockVec bottomLeft;
    private int width;
    private int height;
    private int portalBlocks;

    private PortalShape(Block.Getter level, BlockVec position, Axis axis) {
        this.level = level;
        this.axis = axis;
        this.bottomLeft = findBottomLeft(position);
        if (this.bottomLeft != null) {
            this.width = measureWidth();
            if (this.width > 0) {
                this.height = measureHeight();
            }
        }
    }

    /** The shape around the position along the axis, which may not be valid. */
    public static PortalShape at(Block.Getter level, BlockVec position, Axis axis) {
        return new PortalShape(level, position, axis);
    }

    /** A valid shape without portal blocks around the position, trying the axis X first. */
    public static PortalShape findEmpty(Block.Getter level, BlockVec position) {
        for (var axis : Axis.values()) {
            var shape = at(level, position, axis);
            if (shape.isValid() && shape.portalBlocks == 0) {
                return shape;
            }
        }
        return null;
    }

    public Axis axis() {
        return this.axis;
    }

    public boolean isValid() {
        return this.bottomLeft != null
                && this.width >= MIN_WIDTH
                && this.width <= MAX_WIDTH
                && this.height >= MIN_HEIGHT
                && this.height <= MAX_HEIGHT;
    }

    /** The positions inside the frame, which must be valid. */
    public List<BlockVec> interior() {
        var positions = new ArrayList<BlockVec>();
        for (var w = 0; w < this.width; w++) {
            for (var h = 0; h < this.height; h++) {
                positions.add(
                        new BlockVec(
                                this.bottomLeft.blockX() + this.axis.rightX * w,
                                this.bottomLeft.blockY() + h,
                                this.bottomLeft.blockZ() + this.axis.rightZ * w));
            }
        }
        return positions;
    }

    private BlockVec findBottomLeft(BlockVec start) {
        var x = start.blockX();
        var y = start.blockY();
        var z = start.blockZ();
        var limit = y - MAX_HEIGHT;
        while (y > limit && isEmpty(block(x, y - 1, z))) {
            y--;
        }
        var edge = distanceUntilEdgeAboveFrame(x, y, z, -this.axis.rightX, -this.axis.rightZ) - 1;
        if (edge < 0) {
            return null;
        }
        return new BlockVec(x - this.axis.rightX * edge, y, z - this.axis.rightZ * edge);
    }

    private int distanceUntilEdgeAboveFrame(int x, int y, int z, int dx, int dz) {
        var distance = 0;
        while (distance <= MAX_WIDTH) {
            var tx = x + dx * distance;
            var tz = z + dz * distance;
            if (!isEmpty(block(tx, y, tz)) || !isFrame(block(tx, y - 1, tz))) {
                break;
            }
            distance++;
        }
        return isFrame(block(x + dx * distance, y, z + dz * distance)) ? distance : 0;
    }

    private int measureWidth() {
        var width =
                distanceUntilEdgeAboveFrame(
                        this.bottomLeft.blockX(),
                        this.bottomLeft.blockY(),
                        this.bottomLeft.blockZ(),
                        this.axis.rightX,
                        this.axis.rightZ);
        return width >= MIN_WIDTH && width <= MAX_WIDTH ? width : 0;
    }

    private int measureHeight() {
        var top = distanceUntilTop();
        return top >= MIN_HEIGHT && top <= MAX_HEIGHT && hasTopFrame(top) ? top : 0;
    }

    private boolean hasTopFrame(int top) {
        for (var w = 0; w < this.width; w++) {
            if (!isFrame(at(top, w))) {
                return false;
            }
        }
        return true;
    }

    private int distanceUntilTop() {
        for (var h = 0; h < MAX_HEIGHT; h++) {
            if (!isFrame(at(h, -1)) || !isFrame(at(h, this.width))) {
                return h;
            }
            for (var w = 0; w < this.width; w++) {
                var block = at(h, w);
                if (!isEmpty(block)) {
                    return h;
                }
                if (block.compare(Block.NETHER_PORTAL)) {
                    this.portalBlocks++;
                }
            }
        }
        return MAX_HEIGHT;
    }

    private Block at(int h, int w) {
        return block(
                this.bottomLeft.blockX() + this.axis.rightX * w,
                this.bottomLeft.blockY() + h,
                this.bottomLeft.blockZ() + this.axis.rightZ * w);
    }

    private Block block(int x, int y, int z) {
        try {
            var block = this.level.getBlock(x, y, z, Block.Getter.Condition.TYPE);
            return block == null ? Block.BARRIER : block;
        } catch (RuntimeException e) {
            return Block.BARRIER;
        }
    }

    private static boolean isEmpty(Block block) {
        return block.air()
                || block.compare(Block.FIRE)
                || block.compare(Block.SOUL_FIRE)
                || block.compare(Block.NETHER_PORTAL);
    }

    private static boolean isFrame(Block block) {
        return block.compare(Block.OBSIDIAN);
    }
}
