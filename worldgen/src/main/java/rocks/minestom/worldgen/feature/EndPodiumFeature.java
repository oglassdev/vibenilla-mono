package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.EndPodiumConfiguration;

/**
 * Port of vanilla {@code EndPodiumFeature}: the bedrock fountain the dragon fight returns to, with
 * its portal opening when active.
 */
public final class EndPodiumFeature implements Feature<EndPodiumConfiguration> {
    private static final int PODIUM_RADIUS = 4;
    private static final int PODIUM_PILLAR_HEIGHT = 4;
    private static final double RIM_RADIUS_SQUARED = 2.5 * 2.5;
    private static final double OUTER_RADIUS_SQUARED = 3.5 * 3.5;

    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(
            FeaturePlaceContext<EndPodiumConfiguration, T> context) {
        var level = context.accessor();
        var origin = context.origin();
        var active = context.config().active();
        var originX = origin.blockX();
        var originY = origin.blockY();
        var originZ = origin.blockZ();

        for (var z = originZ - PODIUM_RADIUS; z <= originZ + PODIUM_RADIUS; z++) {
            for (var y = originY - 1; y <= originY + 32; y++) {
                for (var x = originX - PODIUM_RADIUS; x <= originX + PODIUM_RADIUS; x++) {
                    var dx = x - originX;
                    var dy = y - originY;
                    var dz = z - originZ;
                    var distanceSquared = dx * dx + dy * dy + dz * dz;
                    var insideRim = distanceSquared < RIM_RADIUS_SQUARED;
                    if (!insideRim && distanceSquared >= OUTER_RADIUS_SQUARED) {
                        continue;
                    }

                    var position = new BlockVec(x, y, z);
                    if (y < originY) {
                        level.setBlock(position, insideRim ? Block.BEDROCK : Block.END_STONE);
                    } else if (y > originY) {
                        level.setBlock(position, Block.AIR);
                    } else if (!insideRim) {
                        level.setBlock(position, Block.BEDROCK);
                    } else {
                        level.setBlock(position, active ? Block.END_PORTAL : Block.AIR);
                    }
                }
            }
        }

        for (var y = 0; y < PODIUM_PILLAR_HEIGHT; y++) {
            level.setBlock(origin.add(0, y, 0), Block.BEDROCK);
        }

        var pillarCenter = origin.add(0, 2, 0);
        for (var face : Direction.HORIZONTAL) {
            level.setBlock(
                    face.relative(pillarCenter),
                    Block.WALL_TORCH.withProperty("facing", face.serializedName()));
        }

        return true;
    }
}
