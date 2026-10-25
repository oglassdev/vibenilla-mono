package rocks.minestom.placement;

import java.util.List;
import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.rule.BlockPlacementRule;
import org.jetbrains.annotations.Nullable;

public final class ChestPlacementRule extends BlockPlacementRule {
    private static final List<String> COPPER_STAGES =
            List.of(
                    "copper_chest",
                    "exposed_copper_chest",
                    "weathered_copper_chest",
                    "oxidized_copper_chest");
    private static final String WAXED = "waxed_";

    public ChestPlacementRule(Block block) {
        super(block);
    }

    @Override
    public Block blockPlace(PlacementState placementState) {
        var playerPosition = placementState.playerPosition();
        var yaw = playerPosition == null ? 0.0F : playerPosition.yaw();
        var facing = BlockFace.fromYaw(yaw).getOppositeFace();
        var clickedFace = placementState.blockFace();
        var shifting = placementState.isPlayerShifting();
        var instance = placementState.instance();
        var placePosition = placementState.placePosition();
        var replaced = instance.getBlock(placePosition);
        var waterlogged = Utility.isWaterSource(replaced);
        var type = "single";

        if (shifting
                && clickedFace != null
                && clickedFace != BlockFace.TOP
                && clickedFace != BlockFace.BOTTOM) {
            var neighborFacing =
                    this.candidatePartnerFacing(
                            instance, placePosition, clickedFace.getOppositeFace(), false, true);

            if (neighborFacing != null && !neighborFacing.isSimilar(clickedFace)) {
                facing = neighborFacing;
                type =
                        counterClockwise(neighborFacing) == clickedFace.getOppositeFace()
                                ? "right"
                                : "left";
            }
        }

        if ("single".equals(type) && !shifting) {
            type = this.getChestType(instance, placePosition, facing, false, true);
        }

        var placed =
                placementState
                        .block()
                        .withHandler(ConsumeInteractionBlockHandler.INSTANCE)
                        .withProperty("facing", facing.name().toLowerCase())
                        .withProperty("type", type)
                        .withProperty("waterlogged", waterlogged ? "true" : "false");
        return normalizeCopperPair(placed, instance, placePosition);
    }

    @Override
    public Block blockUpdate(UpdateState updateState) {
        var currentBlock = updateState.currentBlock();
        var fromFace = updateState.fromFace();
        var type = currentBlock.getProperty("type");

        if (fromFace == BlockFace.TOP
                || fromFace == BlockFace.BOTTOM
                || (!"single".equals(type) && !"left".equals(type) && !"right".equals(type))) {
            return currentBlock;
        }

        var facing = parseFacing(currentBlock.getProperty("facing"));

        if (facing == null) {
            return currentBlock;
        }

        var updatedType =
                this.getChestType(
                        updateState.instance(), updateState.blockPosition(), facing, true, false);
        return normalizeCopperPair(
                currentBlock.withProperty("type", updatedType),
                updateState.instance(),
                updateState.blockPosition());
    }

    private static int copperStage(Block block) {
        var name = block.key().value();
        return COPPER_STAGES.indexOf(
                name.startsWith(WAXED) ? name.substring(WAXED.length()) : name);
    }

    /**
     * Vanilla {@code CopperChestBlock}: both halves of a copper double chest become the least
     * oxidized variant, and unwaxed unless both were waxed.
     */
    private static Block normalizeCopperPair(
            Block chest, Block.Getter blockGetter, Point position) {
        var facing = parseFacing(chest.getProperty("facing"));
        var type = chest.getProperty("type");

        if (copperStage(chest) < 0 || facing == null || "single".equals(type)) {
            return chest;
        }

        var direction = "left".equals(type) ? clockwise(facing) : counterClockwise(facing);
        var partner = blockGetter.getBlock(position.relative(direction));

        if (copperStage(partner) < 0) {
            return chest;
        }

        var waxed =
                chest.key().value().startsWith(WAXED) && partner.key().value().startsWith(WAXED);
        var stage = Math.min(copperStage(chest), copperStage(partner));
        var name = "minecraft:" + (waxed ? WAXED : "") + COPPER_STAGES.get(stage);

        return Block.fromKey(name)
                .withProperties(chest.properties())
                .withHandler(chest.handler())
                .withNbt(chest.nbt());
    }

    private boolean connectsTo(Block neighbor) {
        return copperStage(this.block) >= 0
                ? copperStage(neighbor) >= 0
                : neighbor.compare(this.block);
    }

    private String getChestType(
            Block.Getter blockGetter,
            Point position,
            BlockFace facing,
            boolean allowDoublePartner,
            boolean allowSinglePartner) {
        if (facing
                == this.candidatePartnerFacing(
                        blockGetter,
                        position,
                        clockwise(facing),
                        allowDoublePartner,
                        allowSinglePartner)) {
            return "left";
        }

        return facing
                        == this.candidatePartnerFacing(
                                blockGetter,
                                position,
                                counterClockwise(facing),
                                allowDoublePartner,
                                allowSinglePartner)
                ? "right"
                : "single";
    }

    private @Nullable BlockFace candidatePartnerFacing(
            Block.Getter blockGetter,
            Point position,
            BlockFace neighborDirection,
            boolean allowDoublePartner,
            boolean allowSinglePartner) {
        var neighborBlock = blockGetter.getBlock(position.relative(neighborDirection));

        if (!connectsTo(neighborBlock)) {
            return null;
        }

        var neighborType = neighborBlock.getProperty("type");

        if ("single".equals(neighborType)) {
            if (!allowSinglePartner) {
                return null;
            }
        } else if (!allowDoublePartner
                || !isCompatibleDoublePartner(neighborType, neighborDirection, neighborBlock)) {
            return null;
        }

        return parseFacing(neighborBlock.getProperty("facing"));
    }

    private static boolean isCompatibleDoublePartner(
            String neighborType, BlockFace neighborDirection, Block neighborBlock) {
        var neighborFacing = parseFacing(neighborBlock.getProperty("facing"));

        if (neighborFacing == null) {
            return false;
        }

        var directionToPartner = neighborDirection.getOppositeFace();
        return "left".equals(neighborType) && directionToPartner == clockwise(neighborFacing)
                || "right".equals(neighborType)
                        && directionToPartner == counterClockwise(neighborFacing);
    }

    private static @Nullable BlockFace parseFacing(String facingName) {
        return switch (facingName) {
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
            case WEST -> BlockFace.NORTH;
            default -> face;
        };
    }

    private static BlockFace counterClockwise(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.WEST;
            case WEST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.EAST;
            case EAST -> BlockFace.NORTH;
            default -> face;
        };
    }
}
