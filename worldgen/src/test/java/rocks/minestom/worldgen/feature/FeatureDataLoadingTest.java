package rocks.minestom.worldgen.feature;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;
import rocks.minestom.worldgen.datapack.DataPack;

class FeatureDataLoadingTest {
    private static final String DEFAULT_DATAPACK =
            "/home/alex/.t3/scratch/vibenilla-mono/server/run/vanilla";

    @Test
    void loadsEveryFeatureDataFile() throws IOException {
        var root = Path.of(configuredDatapack());
        assumeTrue(Files.isDirectory(root.resolve("data")), "datapack not found at " + root);

        var loader = new FeatureLoader(new DataPack(root));
        var failures = new ArrayList<String>();
        check(root, "feature", loader::parseFeature, failures);
        check(root, "placed_feature", loader::parsePlacedFeature, failures);
        check(root, "block_state_provider", loader::parseBlockStateProvider, failures);
        check(root, "biome", loader::parseBiomeFeatures, failures);
        for (var unknown : UnknownTypes.reported()) {
            failures.add("unknown type: " + unknown);
        }

        assertTrue(
                failures.isEmpty(), failures.size() + " failures:\n" + String.join("\n", failures));
    }

    private static String configuredDatapack() {
        var property = System.getProperty("worldgen.datapack");
        if (property != null) {
            return property;
        }

        var environment = System.getenv("WORLDGEN_DATAPACK");
        return environment != null ? environment : DEFAULT_DATAPACK;
    }

    private static void check(
            Path root, String directory, Function<Key, ?> parser, List<String> failures)
            throws IOException {
        try (var namespaces = Files.list(root.resolve("data"))) {
            for (var namespace : namespaces.toList()) {
                var base = namespace.resolve("worldgen").resolve(directory);
                if (!Files.isDirectory(base)) {
                    continue;
                }

                try (Stream<Path> files = Files.walk(base)) {
                    for (var file :
                            files.filter(path -> path.toString().endsWith(".json"))
                                    .sorted()
                                    .toList()) {
                        var relative = base.relativize(file).toString().replace('\\', '/');
                        var id =
                                Key.key(
                                        namespace.getFileName().toString(),
                                        relative.substring(0, relative.length() - 5));
                        try {
                            parser.apply(id);
                        } catch (Exception exception) {
                            failures.add(
                                    directory + " " + id.asString() + ": " + describe(exception));
                        }
                    }
                }
            }
        }
    }

    private static String describe(Throwable throwable) {
        var message = new StringBuilder(throwable.toString());
        for (var cause = throwable.getCause(); cause != null; cause = cause.getCause()) {
            message.append(" <- ").append(cause);
        }
        return message.toString();
    }
}
