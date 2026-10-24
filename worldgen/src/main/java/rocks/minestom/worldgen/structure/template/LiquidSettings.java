package rocks.minestom.worldgen.structure.template;

/**
 * Vanilla's liquid handling mode for template placement.
 *
 * <p>{@code APPLY_WATERLOGGING} (the default) waterlogs placed blocks that land in existing source
 * water; {@code IGNORE_WATERLOGGING} places template states verbatim (trial chambers).
 */
public enum LiquidSettings {
    IGNORE_WATERLOGGING,
    APPLY_WATERLOGGING;

    public static LiquidSettings fromName(String name) {
        return switch (name) {
            case "ignore_waterlogging" -> IGNORE_WATERLOGGING;
            case "apply_waterlogging" -> APPLY_WATERLOGGING;
            default -> throw new IllegalArgumentException("Unknown liquid settings: " + name);
        };
    }
}
