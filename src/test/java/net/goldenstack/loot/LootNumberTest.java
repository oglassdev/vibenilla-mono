package net.goldenstack.loot;

import net.goldenstack.loot.util.VanillaInterface;
import net.goldenstack.loot.util.RelevantEntity;
import net.minestom.server.MinecraftServer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LootNumberTest {

    @Test
    void roundsConstantsAndFloorsSumsLikeVanilla() {
        MinecraftServer.init();

        var context = LootContext.from(VanillaInterface.defaults(), Map.of());
        var constant = new LootNumber.Constant(1.6);
        var sum = new LootNumber.Sum(List.of(
                new LootNumber.Constant(1.2),
                new LootNumber.Constant(2.2)
        ));
        var score = new LootNumber.Score(new LootScore.Context(RelevantEntity.THIS), "missing", 1.0);

        assertEquals(2, constant.getInt(context));
        assertEquals(3, sum.getInt(context));
        assertEquals(0.0, score.getDouble(context));
    }

    @Test
    void sumsUsingVanillaFloatPrecision() {
        MinecraftServer.init();

        var context = LootContext.from(VanillaInterface.defaults(), Map.of());
        var sum = new LootNumber.Sum(List.of(
                new LootNumber.Constant(16_777_216.0),
                new LootNumber.Constant(1.0)
        ));

        assertEquals(16_777_216, sum.getInt(context));
        assertEquals(16_777_216.0, sum.getDouble(context));
    }
}
