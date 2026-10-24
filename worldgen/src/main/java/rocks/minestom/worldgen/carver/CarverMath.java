package rocks.minestom.worldgen.carver;

/** Vanilla {@code Mth.sin}/{@code Mth.cos} lookup table (float parity matters for tunnel paths). */
final class CarverMath {
    private static final float[] SIN = new float[65536];

    static {
        for (var index = 0; index < SIN.length; index++) {
            SIN[index] = (float) Math.sin(index / 10430.378350470453);
        }
    }

    private CarverMath() {}

    static float sin(double value) {
        return SIN[(int) ((long) (value * 10430.378350470453) & 65535L)];
    }

    static float cos(double value) {
        return SIN[(int) ((long) (value * 10430.378350470453 + 16384.0) & 65535L)];
    }
}
