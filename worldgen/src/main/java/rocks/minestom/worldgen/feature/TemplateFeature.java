package rocks.minestom.worldgen.feature;

import java.util.ArrayList;
import net.minestom.server.instance.block.Block;
import net.minestom.server.utils.Direction;
import rocks.minestom.worldgen.feature.configurations.TemplateConfiguration;
import rocks.minestom.worldgen.structure.processor.StructureProcessorContext;
import rocks.minestom.worldgen.structure.template.LiquidSettings;
import rocks.minestom.worldgen.structure.template.StructureTemplate;

/**
 * Port of vanilla {@code TemplateFeature}: places a weighted random structure template with a
 * random rotation, centred on the origin. Only available when the feature accessor is the worldgen
 * generation unit, which template placement requires.
 */
public final class TemplateFeature implements Feature<TemplateConfiguration> {
    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(
            FeaturePlaceContext<TemplateConfiguration, T> context) {
        if (!(context.accessor() instanceof GenerationUnitAdapter level)) {
            return false;
        }

        var config = context.config();
        var random = context.random();
        var selected = random.nextInt(config.totalWeight());
        var entry = config.templates().getLast();
        for (var candidate : config.templates()) {
            selected -= candidate.weight();
            if (selected < 0) {
                entry = candidate;
                break;
            }
        }

        var rotation = entry.rotations().get(random.nextInt(entry.rotations().size()));
        var size = entry.template().size();
        var west = rotation.rotate(Direction.WEST);
        var north = rotation.rotate(Direction.NORTH);
        var position =
                context.origin()
                        .add(
                                west.normalX() * (size.blockX() / 2)
                                        + north.normalX() * (size.blockZ() / 2),
                                0,
                                west.normalZ() * (size.blockX() / 2)
                                        + north.normalZ() * (size.blockZ() / 2));

        // Vanilla draws the palette from the shared random
        random.nextInt(1);
        var processorContext =
                new StructureProcessorContext(
                        level, config.blockTags(), context.worldSeed(), position, position, null);
        entry.template()
                .place(
                        new StructureTemplate.PlacementContext(
                                level, null, processorContext, new ArrayList<>()),
                        position,
                        rotation,
                        config.processors(),
                        false,
                        false,
                        LiquidSettings.APPLY_WATERLOGGING,
                        false);
        return true;
    }
}
