package rocks.minestom.placement;

import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.rule.BlockPlacementRule;

public final class RedstoneWirePlacementRule extends BlockPlacementRule {
    private static final Key TRAPDOORS = Key.key("minecraft:trapdoors");

    public RedstoneWirePlacementRule(Block block) {
        super(block);
    }

    @Override
    public Block blockPlace(PlacementState placementState) {
        var blockGetter = placementState.instance();
        var placePosition = placementState.placePosition();
        var below = blockGetter.getBlock(placePosition.relative(BlockFace.BOTTOM));

        if (!Utility.canSupportCenter(below, BlockFace.TOP)) {
            return null;
        }

        return this.withConnections(placementState.block(), blockGetter, placePosition, true)
                .withHandler(RedstoneWireBlockHandler.INSTANCE)
                .withProperty("power", "0");
    }

    @Override
    public Block blockUpdate(UpdateState updateState) {
        var below =
                updateState
                        .instance()
                        .getBlock(updateState.blockPosition().relative(BlockFace.BOTTOM));
        if (!Utility.canSupportCenter(below, BlockFace.TOP)) {
            return Block.AIR;
        }

        var current = updateState.currentBlock();
        return this.withConnections(
                current, updateState.instance(), updateState.blockPosition(), isDot(current));
    }

    Block withConnections(
            Block base, Block.Getter blockGetter, Point placePosition, boolean preserveDot) {
        var north = this.computeSide(blockGetter, placePosition, BlockFace.NORTH);
        var east = this.computeSide(blockGetter, placePosition, BlockFace.EAST);
        var south = this.computeSide(blockGetter, placePosition, BlockFace.SOUTH);
        var west = this.computeSide(blockGetter, placePosition, BlockFace.WEST);
        var northConnected = !"none".equals(north);
        var southConnected = !"none".equals(south);
        var eastConnected = !"none".equals(east);
        var westConnected = !"none".equals(west);

        if (preserveDot && !northConnected && !southConnected && !eastConnected && !westConnected) {
            return base.withProperty("north", "none")
                    .withProperty("east", "none")
                    .withProperty("south", "none")
                    .withProperty("west", "none");
        }

        var northSouthEmpty = !northConnected && !southConnected;
        var eastWestEmpty = !eastConnected && !westConnected;
        var resolvedNorth = !northConnected && eastWestEmpty ? "side" : north;
        var resolvedSouth = !southConnected && eastWestEmpty ? "side" : south;
        var resolvedEast = !eastConnected && northSouthEmpty ? "side" : east;
        var resolvedWest = !westConnected && northSouthEmpty ? "side" : west;

        return base.withProperty("north", resolvedNorth)
                .withProperty("east", resolvedEast)
                .withProperty("south", resolvedSouth)
                .withProperty("west", resolvedWest);
    }

    static boolean isCross(Block block) {
        return connected(block, "north")
                && connected(block, "east")
                && connected(block, "south")
                && connected(block, "west");
    }

    static boolean isDot(Block block) {
        return !connected(block, "north")
                && !connected(block, "east")
                && !connected(block, "south")
                && !connected(block, "west");
    }

    private static boolean connected(Block block, String property) {
        return !"none".equals(block.getProperty(property));
    }

    private String computeSide(Block.Getter blockGetter, Point placePosition, BlockFace face) {
        var sidePosition = placePosition.relative(face);
        var sideBlock = blockGetter.getBlock(sidePosition);
        var aboveBlock = blockGetter.getBlock(placePosition.relative(BlockFace.TOP));

        if (!aboveBlock.redstoneConductor()) {
            var sideSupportsWire =
                    Utility.hasTag(sideBlock, TRAPDOORS)
                            || sideBlock.compare(Block.HOPPER)
                            || sideBlock.registry().collisionShape().isFaceFull(BlockFace.TOP);
            var aboveSideBlock = blockGetter.getBlock(sidePosition.relative(BlockFace.TOP));

            if (sideSupportsWire && aboveSideBlock.compare(this.block)) {
                var facesWire =
                        sideBlock.registry().collisionShape().isFaceFull(face.getOppositeFace());
                return facesWire ? "up" : "side";
            }
        }

        if (shouldConnectTo(sideBlock, face)) {
            return "side";
        }

        var belowSideBlock = blockGetter.getBlock(sidePosition.relative(BlockFace.BOTTOM));
        return !sideBlock.redstoneConductor() && belowSideBlock.compare(this.block)
                ? "side"
                : "none";
    }

    private static boolean shouldConnectTo(Block block, BlockFace direction) {
        if (block.compare(Block.REDSTONE_WIRE)) {
            return true;
        }

        var facing = block.getProperty("facing");

        if (block.compare(Block.REPEATER)) {
            return direction.name().equalsIgnoreCase(facing)
                    || direction.getOppositeFace().name().equalsIgnoreCase(facing);
        }

        if (block.compare(Block.OBSERVER)) {
            return direction.name().equalsIgnoreCase(facing);
        }

        return block.signalSource();
    }
}
