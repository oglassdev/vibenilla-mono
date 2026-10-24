package rocks.minestom.worldgen.structure.loader;

import com.google.gson.JsonElement;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.kyori.adventure.key.Key;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rocks.minestom.worldgen.datapack.DataPack;
import rocks.minestom.worldgen.structure.Structure;
import rocks.minestom.worldgen.structure.StructureSet;
import rocks.minestom.worldgen.structure.context.BiomeTagManager;
import rocks.minestom.worldgen.structure.context.BlockTagManager;
import rocks.minestom.worldgen.structure.pool.TemplatePool;
import rocks.minestom.worldgen.structure.pool.TemplatePools;
import rocks.minestom.worldgen.structure.processor.StructureProcessorList;
import rocks.minestom.worldgen.structure.processor.StructureProcessors;
import rocks.minestom.worldgen.structure.template.StructureTemplate;

public final class StructureLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(StructureLoader.class);

    private static final List<String> DECORATION_STEPS =
            List.of(
                    "raw_generation",
                    "lakes",
                    "local_modifications",
                    "underground_structures",
                    "surface_structures",
                    "strongholds",
                    "underground_ores",
                    "underground_decoration",
                    "fluid_springs",
                    "vegetal_decoration",
                    "top_layer_modification");
    private static final int DEFAULT_STEP = DECORATION_STEPS.indexOf("surface_structures");

    private final DataPack dataPack;
    private final BlockTagManager blockTags;
    private final BiomeTagManager biomeTags;
    private final Map<Key, Optional<Structure>> structureCache;
    private final Map<Key, Optional<StructureSet>> structureSetCache;
    private final Map<Key, Optional<TemplatePool>> templatePoolCache;
    private final Map<Key, Optional<StructureTemplate>> templateCache;
    private final Map<Key, Optional<StructureProcessorList>> processorListCache;
    private final Map<Key, Integer> structureStepCache;
    private final Map<String, List<Key>> stepStructuresCache;

    public StructureLoader(DataPack dataPack) {
        this.dataPack = dataPack;
        this.blockTags = new BlockTagManager(dataPack.rootPath());
        this.biomeTags = new BiomeTagManager(dataPack.rootPath());
        this.structureCache = new ConcurrentHashMap<>();
        this.structureSetCache = new ConcurrentHashMap<>();
        this.templatePoolCache = new ConcurrentHashMap<>();
        this.templateCache = new ConcurrentHashMap<>();
        this.processorListCache = new ConcurrentHashMap<>();
        this.structureStepCache = new ConcurrentHashMap<>();
        this.stepStructuresCache = new ConcurrentHashMap<>();
    }

    public BlockTagManager blockTags() {
        return this.blockTags;
    }

    public BiomeTagManager biomeTags() {
        return this.biomeTags;
    }

    /** The structure, or null if it failed to load (a warning is logged once per id). */
    public Structure getStructure(Key id) {
        return cached(this.structureCache, "structure", id, this::loadStructure);
    }

    /** The structure set, or null if it failed to load (a warning is logged once per id). */
    public StructureSet getStructureSet(Key id) {
        return cached(this.structureSetCache, "structure set", id, this::loadStructureSet);
    }

    /** The template pool, or null if it failed to load (a warning is logged once per id). */
    public TemplatePool getTemplatePool(Key id) {
        return cached(this.templatePoolCache, "template pool", id, this::loadTemplatePool);
    }

    /** The structure template, or null if it failed to load (a warning is logged once per id). */
    public StructureTemplate getTemplate(Key id) {
        return cached(this.templateCache, "structure template", id, this::loadTemplate);
    }

    /** Like {@link #getTemplate} for callers that cannot continue without the template. */
    public StructureTemplate requireTemplate(Key id) {
        var template = this.getTemplate(id);
        if (template == null) {
            throw new IllegalStateException("Structure template " + id + " is unavailable");
        }
        return template;
    }

    /** The processor list, or null if it failed to load (a warning is logged once per id). */
    public StructureProcessorList getProcessorList(Key id) {
        return cached(this.processorListCache, "processor list", id, this::loadProcessorList);
    }

    /**
     * Resolves a processor list given either as a processor list id or inline; fails if a
     * referenced list is unavailable.
     */
    public StructureProcessorList resolveProcessors(JsonElement json) {
        if (json == null) {
            return StructureProcessorList.EMPTY;
        }

        if (json.isJsonPrimitive()) {
            var processorKey = Key.key(json.getAsString());
            var list = this.getProcessorList(processorKey);
            if (list == null) {
                throw new IllegalStateException(
                        "Processor list " + processorKey + " is unavailable");
            }
            return list;
        }

        return StructureProcessors.parseProcessorList(json);
    }

    private static <T> T cached(
            Map<Key, Optional<T>> cache, String kind, Key id, Function<Key, T> loader) {
        return cache.computeIfAbsent(
                        id,
                        key -> {
                            try {
                                return Optional.of(loader.apply(key));
                            } catch (RuntimeException exception) {
                                LOGGER.warn(
                                        "Failed to load {} {}: {}",
                                        kind,
                                        key,
                                        exception.toString());
                                return Optional.empty();
                            }
                        })
                .orElse(null);
    }

    private Structure loadStructure(Key id) {
        return Structures.parseStructure(this.dataPack.readStructure(id));
    }

    private StructureSet loadStructureSet(Key id) {
        return StructureSets.parseStructureSet(
                this.dataPack.readStructureSet(id), this::getStructureSet);
    }

    private TemplatePool loadTemplatePool(Key id) {
        return TemplatePools.parseTemplatePool(this.dataPack.readTemplatePool(id), this);
    }

    private StructureTemplate loadTemplate(Key id) {
        return StructureTemplate.load(this.resolveTemplatePath(id));
    }

    private StructureProcessorList loadProcessorList(Key id) {
        return StructureProcessors.parseProcessorList(this.dataPack.readProcessorList(id));
    }

    private Path resolveTemplatePath(Key id) {
        return this.dataPack
                .rootPath()
                .resolve("data")
                .resolve(id.namespace())
                .resolve("structure")
                .resolve(id.value() + ".nbt");
    }

    /**
     * The decoration step ordinal of the structure (its {@code step} field), matching vanilla
     * {@code GenerationStep.Decoration}. A structure that fails to load is skipped by the placers,
     * so its step falls back to surface_structures after the load failure is logged.
     */
    public int structureStep(Key id) {
        return this.structureStepCache.computeIfAbsent(
                id,
                key -> {
                    try {
                        var json = this.dataPack.readStructure(key);
                        return stepOrdinal(json.getAsJsonObject().get("step").getAsString());
                    } catch (RuntimeException exception) {
                        LOGGER.warn(
                                "Failed to read the step of structure {}: {}",
                                key,
                                exception.toString());
                        return DEFAULT_STEP;
                    }
                });
    }

    /**
     * The structures of the given decoration step in the order vanilla's {@code
     * ChunkGenerator.applyBiomeDecoration} sees them: the structure registry order (resource path,
     * then namespace), filtered by the structure's {@code step}. The position in this list is the
     * {@code setFeatureSeed} index a structure's placement random is seeded with (verified against
     * an instrumented server: the runtime order is alphabetical by structure path, NOT
     * structure-set flattening order).
     */
    public List<Key> structuresAtStep(String step) {
        return this.stepStructuresCache.computeIfAbsent(
                step,
                stepName -> {
                    var stepOrdinal = stepOrdinal(stepName);
                    var keys = new ArrayList<Key>();
                    var dataRoot = this.dataPack.rootPath().resolve("data");
                    try (var namespaces = Files.list(dataRoot)) {
                        for (var namespaceDir : namespaces.filter(Files::isDirectory).toList()) {
                            var structureDir =
                                    namespaceDir.resolve("worldgen").resolve("structure");
                            if (!Files.isDirectory(structureDir)) {
                                continue;
                            }
                            try (var files = Files.list(structureDir)) {
                                for (var file :
                                        files.filter(
                                                        path ->
                                                                path.getFileName()
                                                                        .toString()
                                                                        .endsWith(".json"))
                                                .toList()) {
                                    var name = file.getFileName().toString();
                                    keys.add(
                                            Key.key(
                                                    namespaceDir.getFileName().toString(),
                                                    name.substring(
                                                            0, name.length() - ".json".length())));
                                }
                            }
                        }
                    } catch (java.io.IOException exception) {
                        LOGGER.warn(
                                "Failed to list structures under {}: {}",
                                dataRoot,
                                exception.toString());
                        return List.of();
                    }

                    keys.sort(Comparator.comparing(Key::namespace).thenComparing(Key::value));
                    return keys.stream()
                            .filter(key -> this.structureStep(key) == stepOrdinal)
                            .toList();
                });
    }

    private static int stepOrdinal(String step) {
        var ordinal = DECORATION_STEPS.indexOf(step.replace("minecraft:", ""));
        if (ordinal < 0) {
            throw new IllegalArgumentException("Unknown decoration step: " + step);
        }
        return ordinal;
    }
}
