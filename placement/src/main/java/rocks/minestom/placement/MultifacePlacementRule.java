package rocks.minestom.placement;

import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.rule.BlockPlacementRule;

public final class MultifacePlacementRule extends BlockPlacementRule {
    private static final BlockFace[] DEFAULT_ORDER = {
        BlockFace.NORTH,
        BlockFace.EAST,
        BlockFace.SOUTH,
        BlockFace.WEST,
        BlockFace.TOP,
        BlockFace.BOTTOM
    };

    private final boolean waterloggable;
    private final boolean vine;

    public MultifacePlacementRule(Block block) {
        this(block, true);
    }

    public MultifacePlacementRule(Block block, boolean waterloggable) {
        this(block, waterloggable, false);
    }

    private MultifacePlacementRule(Block block, boolean waterloggable, boolean vine) {
        super(block);
        this.waterloggable = waterloggable;
        this.vine = vine;
    }

    /**
     * A vine-style rule: no downward face, no waterlogging, and side faces may also be supported by
     * a vine above that has the same face.
     */
    public static MultifacePlacementRule vine(Block block) {
        return new MultifacePlacementRule(block, false, true);
    }

    @Override
    public Block blockPlace(PlacementState placementState) {
        var instance = placementState.instance();
        var placePosition = placementState.placePosition();
        var existingBlock = instance.getBlock(placePosition);
        var playerPosition = placementState.playerPosition();
        var nearest =
                playerPosition == null
                        ? DEFAULT_ORDER
                        : orderedByNearest(playerPosition.yaw(), playerPosition.pitch());

        for (var direction : nearest) {
            if (!canSupportFace(instance, placePosition, direction)) {
                continue;
            }

            var faceName = faceProperty(direction);

            if (existingBlock.compare(placementState.block())
                    && "true".equals(existingBlock.getProperty(faceName))) {
                continue;
            }

            Block result;

            if (existingBlock.compare(placementState.block())) {
                result = existingBlock;
            } else if (this.waterloggable
                    && existingBlock.compare(Block.WATER)
                    && isWaterSource(existingBlock)) {
                result = placementState.block().withProperty("waterlogged", "true");
            } else {
                result = placementState.block();
            }

            return result.withProperty(faceName, "true");
        }

        return null;
    }

    @Override
    public Block blockUpdate(UpdateState updateState) {
        var currentBlock = updateState.currentBlock();

        if (this.vine && updateState.fromFace() == BlockFace.BOTTOM) {
            return currentBlock;
        }

        var instance = updateState.instance();
        var blockPosition = updateState.blockPosition();
        var result = currentBlock;
        var anyFace = false;

        for (var direction : DEFAULT_ORDER) {
            var faceName = faceProperty(direction);
            var faceValue = currentBlock.getProperty(faceName);

            if (!"true".equals(faceValue)) {
                continue;
            }

            if (canSupportFace(instance, blockPosition, direction)) {
                anyFace = true;
                continue;
            }

            result = result.withProperty(faceName, "false");
        }

        if (!anyFace) {
            return Block.AIR;
        }

        return result;
    }

    @Override
    public boolean isSelfReplaceable(Replacement replacement) {
        if (!replacement.block().compare(this.block)) {
            return false;
        }

        if (replacement.material() != this.block.registry().material()) {
            return false;
        }

        var blockFace = replacement.blockFace();

        if (blockFace == null) {
            return false;
        }

        var faceName = faceProperty(blockFace);
        var current = replacement.block().getProperty(faceName);
        return "false".equals(current);
    }

    private boolean canSupportFace(Block.Getter getter, Point position, BlockFace face) {
        if (this.vine && face == BlockFace.BOTTOM) {
            return false;
        }

        var support = getter.getBlock(position.relative(face));

        if (support.registry().collisionShape().isFaceFull(face.getOppositeFace())) {
            return true;
        }

        if (!this.vine || face == BlockFace.TOP) {
            return false;
        }

        var above = getter.getBlock(position.relative(BlockFace.TOP));
        return above.compare(this.block) && "true".equals(above.getProperty(faceProperty(face)));
    }

    private static boolean isWaterSource(Block water) {
        var level = water.getProperty("level");
        return level == null || "0".equals(level);
    }

    private static String faceProperty(BlockFace face) {
        return switch (face) {
            case TOP -> "up";
            case BOTTOM -> "down";
            case NORTH -> "north";
            case SOUTH -> "south";
            case WEST -> "west";
            case EAST -> "east";
        };
    }

    private static BlockFace[] orderedByNearest(float yawDegrees, float pitchDegrees) {
        var pitchRadians = pitchDegrees * (float) (Math.PI / 180.0D);
        var yawRadians = -yawDegrees * (float) (Math.PI / 180.0D);
        var pitchSin = (float) Math.sin(pitchRadians);
        var pitchCos = (float) Math.cos(pitchRadians);
        var yawSin = (float) Math.sin(yawRadians);
        var yawCos = (float) Math.cos(yawRadians);
        var xPositive = yawSin > 0.0F;
        var yPositive = pitchSin < 0.0F;
        var zPositive = yawCos > 0.0F;
        var xYaw = xPositive ? yawSin : -yawSin;
        var yMagnitude = yPositive ? -pitchSin : pitchSin;
        var zYaw = zPositive ? yawCos : -yawCos;
        var xMagnitude = xYaw * pitchCos;
        var zMagnitude = zYaw * pitchCos;
        var axisX = xPositive ? BlockFace.EAST : BlockFace.WEST;
        var axisY = yPositive ? BlockFace.TOP : BlockFace.BOTTOM;
        var axisZ = zPositive ? BlockFace.SOUTH : BlockFace.NORTH;

        if (xYaw > zYaw) {
            if (yMagnitude > xMagnitude) {
                return makeDirectionArray(axisY, axisX, axisZ);
            }

            if (zMagnitude > yMagnitude) {
                return makeDirectionArray(axisX, axisZ, axisY);
            }

            return makeDirectionArray(axisX, axisY, axisZ);
        }

        if (yMagnitude > zMagnitude) {
            return makeDirectionArray(axisY, axisZ, axisX);
        }

        if (xMagnitude > yMagnitude) {
            return makeDirectionArray(axisZ, axisX, axisY);
        }

        return makeDirectionArray(axisZ, axisY, axisX);
    }

    private static BlockFace[] makeDirectionArray(
            BlockFace first, BlockFace second, BlockFace third) {
        return new BlockFace[] {
            first,
            second,
            third,
            third.getOppositeFace(),
            second.getOppositeFace(),
            first.getOppositeFace()
        };
    }
}
