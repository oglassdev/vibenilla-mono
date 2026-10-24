package rocks.minestom.worldgen.feature;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Records data types the feature code does not know, warning once per kind and id. */
public final class UnknownTypes {
    private static final Logger LOGGER = LoggerFactory.getLogger(UnknownTypes.class);
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    private UnknownTypes() {}

    public static void report(String kind, String id) {
        if (REPORTED.add(kind + " " + id)) {
            LOGGER.warn("Unknown {} '{}' in worldgen data", kind, id);
        }
    }

    /** Every {@code "<kind> <id>"} reported so far in this process. */
    public static Set<String> reported() {
        return Set.copyOf(REPORTED);
    }
}
