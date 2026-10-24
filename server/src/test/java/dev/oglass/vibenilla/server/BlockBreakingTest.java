package dev.oglass.vibenilla.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class BlockBreakingTest {
    private static int ticks(float hardness, boolean canHarvest, float toolSpeed, int efficiency) {
        var speed =
                BlockBreaking.destroySpeed(
                        toolSpeed, BlockBreaking.efficiencyBonus(efficiency), -1, -1, 1, 1, true);
        return (int) Math.ceil(1 / BlockBreaking.progressPerTick(hardness, canHarvest, speed));
    }

    @Test
    void matchesVanillaBreakTimes() {
        assertEquals(150, ticks(1.5f, false, 1, 0)); // stone by hand: 7.5s
        assertEquals(6, ticks(1.5f, true, 8, 0)); // stone, diamond pickaxe: 0.3s
        assertEquals(188, ticks(50, true, 8, 0)); // obsidian, diamond pickaxe: 9.4s
        assertEquals(2, ticks(1.5f, true, 8, 5)); // stone, diamond pickaxe, efficiency V: 0.1s
    }

    @Test
    void penalisesAirborneAndFatigue() {
        assertEquals(1f, BlockBreaking.destroySpeed(5, 0, -1, -1, 1, 1, false), 1e-6);
        assertEquals(1.5f, BlockBreaking.destroySpeed(5, 0, -1, 0, 1, 1, true), 1e-6);
        assertEquals(6f, BlockBreaking.destroySpeed(5, 0, 0, -1, 1, 1, true), 1e-6);
    }

    @Test
    void rejectsBreaksThatFinishTooEarly() {
        var progress = BlockBreaking.progressPerTick(1.5f, true, 8);
        assertFalse(BlockBreaking.isFinished(progress, 0));
        assertTrue(BlockBreaking.isFinished(progress, 3));
    }
}
