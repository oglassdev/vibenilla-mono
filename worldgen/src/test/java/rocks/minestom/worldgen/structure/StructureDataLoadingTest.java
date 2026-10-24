package rocks.minestom.worldgen.structure;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.JsonElement;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.BlockVec;
import org.junit.jupiter.api.Test;
import rocks.minestom.worldgen.carver.CarverLoader;
import rocks.minestom.worldgen.datapack.DataPack;
import rocks.minestom.worldgen.structure.loader.StructureLoader;
import rocks.minestom.worldgen.structure.loader.StructureSets;
import rocks.minestom.worldgen.structure.loader.Structures;
import rocks.minestom.worldgen.structure.pool.TemplatePools;
import rocks.minestom.worldgen.structure.processor.JigsawReplacementProcessor;
import rocks.minestom.worldgen.structure.processor.StructureProcessors;
import rocks.minestom.worldgen.structure.template.Rotation;
import rocks.minestom.worldgen.structure.template.StructureTemplate;

/**
 * Strictly parses every structure set, structure, template pool, processor list, carver and
 * structure template of the target datapack through the same code the loaders use (the loaders
 * themselves only log failures), and checks that cross references resolve.
 */
class StructureDataLoadingTest {
    private static final String DEFAULT_DATAPACK =
            "/home/alex/.t3/scratch/vibenilla-mono/server/run/vanilla";

    /**
     * Vanilla pools reference this template but ship no file; vanilla treats it as an empty
     * template.
     */
    private static final Set<Key> KNOWN_MISSING_TEMPLATES =
            Set.of(Key.key("minecraft:ancient_city/walls/intact_horizontal_wall_stairs_5"));

    @Test
    void loadsEveryStructureAndCarverDefinition() throws IOException {
        var root = Path.of(datapackLocation());
        assumeTrue(Files.isDirectory(root.resolve("data")), "datapack not found: " + root);

        var dataPack = new DataPack(root);
        var loader = new StructureLoader(dataPack);
        var failures = new ArrayList<String>();

        var structureSets = ids(root, "worldgen/structure_set");
        var structures = ids(root, "worldgen/structure");
        var pools = ids(root, "worldgen/template_pool");
        var processorLists = ids(root, "worldgen/processor_list");
        var carvers = ids(root, "worldgen/carver");
        var biomes = ids(root, "worldgen/biome");
        var templates = templateIds(root);

        forEach(
                "processor_list",
                processorLists,
                failures,
                (id, check) ->
                        StructureProcessors.parseProcessorList(dataPack.readProcessorList(id)));
        forEach(
                "template_pool",
                pools,
                failures,
                (id, check) -> {
                    var json = dataPack.readTemplatePool(id);
                    TemplatePools.parseTemplatePool(json, loader);
                    check.require(
                            pools, Key.key(json.getAsJsonObject().get("fallback").getAsString()));
                    for (var location : poolLocations(json)) {
                        if (!KNOWN_MISSING_TEMPLATES.contains(location)) {
                            check.require(templates, location);
                        }
                    }
                });
        forEach(
                "structure",
                structures,
                failures,
                (id, check) -> {
                    var json = dataPack.readStructure(id);
                    Structures.parseStructure(json);
                    var startPool = json.getAsJsonObject().get("start_pool");
                    if (startPool != null) {
                        check.require(pools, Key.key(startPool.getAsString()));
                    }
                });
        forEach(
                "structure_set",
                structureSets,
                failures,
                (id, check) -> {
                    var set =
                            StructureSets.parseStructureSet(
                                    dataPack.readStructureSet(id), loader::getStructureSet);
                    for (var selection : set.structures()) {
                        check.require(structures, selection.structure());
                    }
                });
        forEach(
                "carver",
                carvers,
                failures,
                (id, check) -> CarverLoader.parseCarver(dataPack.readCarver(id)));
        forEach(
                "biome carvers",
                biomes,
                failures,
                (id, check) -> {
                    var carverRefs = dataPack.readBiome(id).getAsJsonObject().get("carvers");
                    if (carverRefs == null) {
                        return;
                    }
                    var refs =
                            carverRefs.isJsonArray()
                                    ? carverRefs.getAsJsonArray().asList()
                                    : List.of(carverRefs);
                    for (var ref : refs) {
                        check.require(carvers, Key.key(ref.getAsString()));
                    }
                });
        forEach(
                "structure template",
                templates,
                failures,
                (id, check) -> {
                    var template = StructureTemplate.load(templatePath(root, id));
                    for (var jigsaw : template.getJigsaws(BlockVec.ZERO, Rotation.NONE)) {
                        if (JigsawReplacementProcessor.parseBlockState(jigsaw.finalState())
                                == null) {
                            throw new IllegalStateException(
                                    "unparseable jigsaw final_state '" + jigsaw.finalState() + "'");
                        }
                    }
                });

        assertTrue(
                failures.isEmpty(),
                failures.size()
                        + " datapack entries failed to load:\n"
                        + String.join("\n", failures));
    }

    private static String datapackLocation() {
        var property = System.getProperty("worldgen.datapack");
        if (property != null && !property.isBlank()) {
            return property;
        }
        var environment = System.getenv("WORLDGEN_DATAPACK");
        return environment != null && !environment.isBlank() ? environment : DEFAULT_DATAPACK;
    }

    private static void forEach(
            String kind, Set<Key> ids, List<String> failures, BiConsumer<Key, References> check) {
        for (var id : ids) {
            try {
                check.accept(id, new References());
            } catch (RuntimeException exception) {
                failures.add(kind + " " + id.asString() + ": " + exception);
            }
        }
    }

    private static List<Key> poolLocations(JsonElement pool) {
        var locations = new ArrayList<Key>();
        for (var entry : pool.getAsJsonObject().getAsJsonArray("elements")) {
            collectLocations(entry.getAsJsonObject().getAsJsonObject("element"), locations);
        }
        return locations;
    }

    private static void collectLocations(com.google.gson.JsonObject element, List<Key> locations) {
        if (element.has("location")) {
            locations.add(Key.key(element.get("location").getAsString()));
        }
        if (element.has("elements")) {
            for (var child : element.getAsJsonArray("elements")) {
                collectLocations(child.getAsJsonObject(), locations);
            }
        }
    }

    private static Set<Key> ids(Path root, String directory) throws IOException {
        var result = new HashSet<Key>();
        for (var namespace : namespaces(root)) {
            var base = root.resolve("data").resolve(namespace).resolve(directory);
            if (!Files.isDirectory(base)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(base)) {
                files.filter(path -> path.toString().endsWith(".json"))
                        .forEach(path -> result.add(idOf(namespace, base, path, ".json")));
            }
        }
        return result;
    }

    private static Set<Key> templateIds(Path root) throws IOException {
        var result = new HashSet<Key>();
        for (var namespace : namespaces(root)) {
            var base = root.resolve("data").resolve(namespace).resolve("structure");
            if (!Files.isDirectory(base)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(base)) {
                files.filter(path -> path.toString().endsWith(".nbt"))
                        .forEach(path -> result.add(idOf(namespace, base, path, ".nbt")));
            }
        }
        return result;
    }

    private static List<String> namespaces(Path root) throws IOException {
        try (Stream<Path> children = Files.list(root.resolve("data"))) {
            return children.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .toList();
        }
    }

    private static Key idOf(String namespace, Path base, Path file, String extension) {
        var relative = base.relativize(file).toString().replace('\\', '/');
        return Key.key(namespace, relative.substring(0, relative.length() - extension.length()));
    }

    private static Path templatePath(Path root, Key id) {
        return root.resolve("data")
                .resolve(id.namespace())
                .resolve("structure")
                .resolve(id.value() + ".nbt");
    }

    private static final class References {
        void require(Set<Key> known, Key id) {
            if (!known.contains(id)) {
                throw new IllegalStateException("missing reference " + id.asString());
            }
        }
    }
}
