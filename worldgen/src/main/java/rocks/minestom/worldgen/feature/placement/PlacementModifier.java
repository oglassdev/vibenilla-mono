package rocks.minestom.worldgen.feature.placement;

import java.util.List;
import net.minestom.server.coordinate.BlockVec;
import rocks.minestom.worldgen.random.RandomSource;

public interface PlacementModifier {
    List<BlockVec> apply(PlacementContext context, RandomSource random, BlockVec position);
}
