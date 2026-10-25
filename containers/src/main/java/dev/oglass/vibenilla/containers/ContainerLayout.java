package dev.oglass.vibenilla.containers;

import java.util.List;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;

/** Resolves which block positions share one inventory. */
final class ContainerLayout {
    private ContainerLayout() {}

    /**
     * Returns the positions that make up the container at {@code position}: one for a single block,
     * or the right half followed by the left half for a double chest. The right half owns the top
     * 27 slots. The list is empty for a double chest whose partner is in an unloaded chunk.
     */
    static List<BlockVec> halves(Instance instance, BlockVec position, Block block) {
        var type = block.getProperty("type");
        var facing = facing(block);

        if (facing == null || (!"left".equals(type) && !"right".equals(type))) {
            return List.of(position);
        }

        var left = "left".equals(type);
        var partner = position.relative(left ? clockwise(facing) : counterClockwise(facing));

        if (!instance.isChunkLoaded(partner)) {
            return List.of();
        }

        var partnerBlock = instance.getBlock(partner);

        if (!(partnerBlock.compare(block)
                        || (CopperChests.is(block) && CopperChests.is(partnerBlock)))
                || facing(partnerBlock) != facing
                || !(left ? "right" : "left").equals(partnerBlock.getProperty("type"))) {
            return List.of(position);
        }

        return left ? List.of(partner, position) : List.of(position, partner);
    }

    private static BlockFace facing(Block block) {
        return switch (block.getProperty("facing")) {
            case "north" -> BlockFace.NORTH;
            case "east" -> BlockFace.EAST;
            case "south" -> BlockFace.SOUTH;
            case "west" -> BlockFace.WEST;
            case null, default -> null;
        };
    }

    private static BlockFace clockwise(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            default -> BlockFace.NORTH;
        };
    }

    private static BlockFace counterClockwise(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.WEST;
            case WEST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.EAST;
            default -> BlockFace.NORTH;
        };
    }
}
