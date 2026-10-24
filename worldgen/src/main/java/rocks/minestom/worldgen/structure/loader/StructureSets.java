package rocks.minestom.worldgen.structure.loader;

import com.google.gson.JsonElement;
import java.util.List;
import java.util.function.Function;
import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.codec.Transcoder;
import rocks.minestom.worldgen.structure.StructureSet;
import rocks.minestom.worldgen.structure.placement.StructurePlacements;

public final class StructureSets {
    private static final Codec<StructureSetData> STRUCTURE_SET_CODEC =
            StructCodec.struct(
                    "structures",
                    StructureSet.StructureSelection.CODEC.list(),
                    StructureSetData::structures,
                    "placement",
                    Codec.RAW_VALUE,
                    StructureSetData::placement,
                    StructureSetData::new);

    private StructureSets() {}

    /**
     * Parses a structure set, failing on unknown placement types. {@code structureSets} resolves
     * the sets referenced by placement exclusion zones.
     */
    public static StructureSet parseStructureSet(
            JsonElement json, Function<Key, StructureSet> structureSets) {
        var decoded = STRUCTURE_SET_CODEC.decode(Transcoder.JSON, json).orElseThrow();
        var placementJson = decoded.placement().convertTo(Transcoder.JSON).orElseThrow();
        var placement = StructurePlacements.parsePlacement(placementJson, structureSets);
        return new StructureSet(decoded.structures(), placement);
    }

    private record StructureSetData(
            List<StructureSet.StructureSelection> structures, Codec.RawValue placement) {}
}
