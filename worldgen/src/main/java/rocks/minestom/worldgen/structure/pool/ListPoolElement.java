package rocks.minestom.worldgen.structure.pool;

import java.util.List;
import net.minestom.server.coordinate.BlockVec;
import rocks.minestom.worldgen.random.RandomSource;
import rocks.minestom.worldgen.structure.loader.StructureLoader;
import rocks.minestom.worldgen.structure.template.BoundingBox;
import rocks.minestom.worldgen.structure.template.JigsawBlockInfo;
import rocks.minestom.worldgen.structure.template.Rotation;

/**
 * Vanilla {@code ListPoolElement}: multiple elements placed together at the same location (pillager
 * outpost watchtower base + overgrowth). Jigsaw expansion only considers the first element; the
 * bounding box is the union of all non-empty children.
 */
public record ListPoolElement(List<PoolElement> elements, Projection projection)
        implements PoolElement {
    @Override
    public List<JigsawBlockInfo> getShuffledJigsawBlocks(
            StructureLoader loader, BlockVec position, Rotation rotation, RandomSource random) {
        return this.elements.getFirst().getShuffledJigsawBlocks(loader, position, rotation, random);
    }

    @Override
    public BoundingBox getBoundingBox(
            StructureLoader loader, BlockVec position, Rotation rotation) {
        BoundingBox result = null;
        for (var element : this.elements) {
            if (element instanceof EmptyPoolElement) {
                continue;
            }
            var bounds = element.getBoundingBox(loader, position, rotation);
            if (result == null) {
                result = bounds;
            } else {
                result.encapsulate(bounds);
            }
        }

        if (result == null) {
            throw new IllegalStateException("Unable to calculate bounding box for ListPoolElement");
        }
        return result;
    }
}
