package rocks.minestom.worldgen.structure.pool;

/** Vanilla {@code StructureTemplatePool.Projection}. */
public enum Projection {
    RIGID,
    TERRAIN_MATCHING;

    public static Projection fromName(String name) {
        return switch (name) {
            case "rigid" -> RIGID;
            case "terrain_matching" -> TERRAIN_MATCHING;
            default -> throw new IllegalArgumentException("Unknown projection: " + name);
        };
    }
}
