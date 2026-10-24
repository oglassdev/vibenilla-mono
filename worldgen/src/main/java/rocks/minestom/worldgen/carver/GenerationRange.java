package rocks.minestom.worldgen.carver;

/**
 * The vertical generation range carvers sample against (vanilla {@code WorldGenerationContext}).
 */
public record GenerationRange(int minGenY, int genDepth) {
    public int maxGenYInclusive() {
        return this.minGenY + this.genDepth - 1;
    }
}
