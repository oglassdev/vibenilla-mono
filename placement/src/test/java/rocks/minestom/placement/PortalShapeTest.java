package rocks.minestom.placement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import org.junit.jupiter.api.Test;

final class PortalShapeTest {
    @Test
    void findsMinimalFrameAlongX() {
        var level = frame(PortalShape.Axis.X, 2, 3);
        var shape = PortalShape.findEmpty(blocksAt(level), new BlockVec(0, 1, 0));
        assertNotNull(shape);
        assertEquals(PortalShape.Axis.X, shape.axis());
        assertEquals(6, shape.interior().size());
    }

    @Test
    void findsFrameAlongZ() {
        var level = frame(PortalShape.Axis.Z, 4, 5);
        var shape = PortalShape.findEmpty(blocksAt(level), new BlockVec(0, 2, 2));
        assertNotNull(shape);
        assertEquals(PortalShape.Axis.Z, shape.axis());
        assertEquals(20, shape.interior().size());
    }

    @Test
    void acceptsLargestFrameAndRejectsOversized() {
        assertTrue(
                PortalShape.at(
                                blocksAt(frame(PortalShape.Axis.X, 21, 21)),
                                new BlockVec(-5, 3, 0),
                                PortalShape.Axis.X)
                        .isValid());
        assertFalse(
                PortalShape.at(
                                blocksAt(frame(PortalShape.Axis.X, 22, 3)),
                                new BlockVec(-5, 3, 0),
                                PortalShape.Axis.X)
                        .isValid());
    }

    @Test
    void rejectsMissingTopFrameBlock() {
        var level = frame(PortalShape.Axis.X, 2, 3);
        level.remove(new BlockVec(0, 4, 0));
        assertNull(PortalShape.findEmpty(blocksAt(level), new BlockVec(0, 1, 0)));
    }

    @Test
    void rejectsFrameThatIsTooShort() {
        assertNull(
                PortalShape.findEmpty(
                        blocksAt(frame(PortalShape.Axis.X, 3, 2)), new BlockVec(0, 1, 0)));
    }

    @Test
    void filledFrameIsNotEmptyButStaysValid() {
        var level = frame(PortalShape.Axis.X, 2, 3);
        var shape = PortalShape.findEmpty(blocksAt(level), new BlockVec(0, 1, 0));
        for (var position : shape.interior()) {
            level.put(position, Block.NETHER_PORTAL.withProperty("axis", "x"));
        }
        assertNull(PortalShape.findEmpty(blocksAt(level), new BlockVec(0, 1, 0)));
        assertTrue(
                PortalShape.at(blocksAt(level), new BlockVec(0, 1, 0), PortalShape.Axis.X)
                        .isValid());
    }

    @Test
    void portalWithAHoleIsValidButNotComplete() {
        var level = frame(PortalShape.Axis.X, 2, 3);
        var shape = PortalShape.findEmpty(blocksAt(level), new BlockVec(0, 1, 0));
        for (var position : shape.interior()) {
            level.put(position, Block.NETHER_PORTAL.withProperty("axis", "x"));
        }
        var start = new BlockVec(0, 1, 0);
        assertTrue(PortalShape.at(blocksAt(level), start, PortalShape.Axis.X).isComplete());

        level.remove(new BlockVec(0, 2, 0));
        var holed = PortalShape.at(blocksAt(level), start, PortalShape.Axis.X);
        assertTrue(holed.isValid());
        assertFalse(holed.isComplete());
    }

    @Test
    void fireInsideTheFrameCountsAsEmpty() {
        var level = frame(PortalShape.Axis.X, 2, 3);
        level.put(new BlockVec(0, 1, 0), Block.FIRE);
        assertNotNull(PortalShape.findEmpty(blocksAt(level), new BlockVec(0, 1, 0)));
    }

    /** Obsidian frame whose interior starts at x=0 (or z=0) and y=1, at the other coordinate 0. */
    private static Map<BlockVec, Block> frame(PortalShape.Axis axis, int width, int height) {
        var blocks = new HashMap<BlockVec, Block>();
        var step = axis == PortalShape.Axis.X ? new BlockVec(-1, 0, 0) : new BlockVec(0, 0, 1);
        for (var w = -1; w <= width; w++) {
            for (var h = 0; h <= height + 1; h++) {
                if (w == -1 || w == width || h == 0 || h == height + 1) {
                    blocks.put(
                            new BlockVec(step.blockX() * w, h, step.blockZ() * w), Block.OBSIDIAN);
                }
            }
        }
        return blocks;
    }

    private static Block.Getter blocksAt(Map<BlockVec, Block> blocks) {
        return (x, y, z, condition) -> blocks.getOrDefault(new BlockVec(x, y, z), Block.AIR);
    }
}
