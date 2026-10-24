package rocks.minestom.worldgen.feature.configurations;

import com.google.gson.JsonElement;
import net.kyori.adventure.key.Key;
import rocks.minestom.worldgen.datapack.DataPack;
import rocks.minestom.worldgen.structure.processor.StructureProcessorList;
import rocks.minestom.worldgen.structure.processor.StructureProcessors;
import rocks.minestom.worldgen.structure.template.StructureTemplate;

/** Loading of the structure templates and processor lists that features reference by id. */
final class StructureData {
    private StructureData() {}

    static StructureTemplate template(DataPack dataPack, Key key) {
        var path =
                dataPack.rootPath()
                        .resolve("data")
                        .resolve(key.namespace())
                        .resolve("structure")
                        .resolve(key.value() + ".nbt");
        return StructureTemplate.load(path);
    }

    /** A processor list given as an id, an inline object, or absent (null). */
    static StructureProcessorList processorList(JsonElement json, DataPack dataPack) {
        if (json == null) {
            return StructureProcessorList.EMPTY;
        }

        if (json.isJsonPrimitive()) {
            return StructureProcessors.parseProcessorList(
                    dataPack.readProcessorList(Key.key(json.getAsString())));
        }

        return StructureProcessors.parseProcessorList(json);
    }
}
