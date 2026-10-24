package dev.oglass.vibenilla.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ExperienceTest {
    @Test
    void pointsToNextLevelFollowsVanillaCurve() {
        assertEquals(7, Experience.pointsToNextLevel(0));
        assertEquals(37, Experience.pointsToNextLevel(15));
        assertEquals(42, Experience.pointsToNextLevel(16));
        assertEquals(112, Experience.pointsToNextLevel(30));
        assertEquals(121, Experience.pointsToNextLevel(31));
    }

    @Test
    void addCarriesIntoLevels() {
        var result = Experience.add(0, 0.0F, 17);
        assertEquals(2, result.level());
        assertEquals(1.0F / 11, result.progress(), 1.0E-5F);
    }

    @Test
    void deathRewardIsCapped() {
        assertEquals(35, Experience.deathReward(5));
        assertEquals(100, Experience.deathReward(30));
    }
}
