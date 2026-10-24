package net.goldenstack.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;
import java.util.Map;
import net.goldenstack.loot.util.LootNumberRange;
import net.goldenstack.loot.util.RelevantEntity;
import net.goldenstack.loot.util.VanillaInterface;
import net.kyori.adventure.nbt.IntBinaryTag;
import net.minestom.server.MinecraftServer;
import net.minestom.server.adventure.MinestomAdventure;
import net.minestom.server.codec.Transcoder;
import org.junit.jupiter.api.Test;

class LootNumberTest {

    @Test
    void roundsConstantsAndFloorsSumsLikeVanilla() {
        MinecraftServer.init();

        var context = LootContext.from(VanillaInterface.defaults(), Map.of());
        var constant = new LootNumber.Constant(1.6);
        var sum =
                new LootNumber.Sum(
                        List.of(new LootNumber.Constant(1.2), new LootNumber.Constant(2.2)));
        var score =
                new LootNumber.Score(new LootScore.Context(RelevantEntity.THIS), "missing", 1.0);

        assertEquals(2, constant.getInt(context));
        assertEquals(3, sum.getInt(context));
        assertEquals(0.0, score.getDouble(context));
    }

    @Test
    void sumsUsingVanillaFloatPrecision() {
        MinecraftServer.init();

        var context = LootContext.from(VanillaInterface.defaults(), Map.of());
        var sum =
                new LootNumber.Sum(
                        List.of(
                                new LootNumber.Constant(16_777_216.0),
                                new LootNumber.Constant(1.0)));

        assertEquals(16_777_216, sum.getInt(context));
        assertEquals(16_777_216.0, sum.getDouble(context));
    }

    @Test
    void uniformReturnsMinimumWhenBoundsAreReversed() {
        MinecraftServer.init();

        var context = LootContext.from(VanillaInterface.defaults(), Map.of());
        var uniform =
                new LootNumber.Uniform(new LootNumber.Constant(4.0), new LootNumber.Constant(2.0));

        assertEquals(4, uniform.getInt(context));
        assertEquals(4.0, uniform.getDouble(context));
    }

    @Test
    void scoreScaleUsesVanillaFloatPrecision() throws Exception {
        MinecraftServer.init();

        var input =
                MinestomAdventure.tagStringIO()
                        .asTag(
                                """
                {
                  target: "this",
                  score: "missing",
                  scale: 16777217.0
                }
                """);
        var score = LootNumber.Score.CODEC.decode(Transcoder.NBT, input).orElseThrow();

        assertEquals(16_777_216.0, score.scale());
    }

    @Test
    void numberProviderCodecsUseVanillaFloatPrecision() throws Exception {
        MinecraftServer.init();

        var input = MinestomAdventure.tagStringIO().asTag("16777217");
        var number = LootNumber.CODEC.decode(Transcoder.NBT, input).orElseThrow();

        assertEquals(
                16_777_216.0,
                number.getDouble(LootContext.from(VanillaInterface.defaults(), Map.of())));
    }

    @Test
    void exactRangesUseIntegerScalarEncoding() throws Exception {
        MinecraftServer.init();

        var integer = MinestomAdventure.tagStringIO().asTag("3");
        var integerRange = LootNumberRange.CODEC.decode(Transcoder.NBT, integer).orElseThrow();
        var encoded = LootNumberRange.CODEC.encode(Transcoder.NBT, integerRange).orElseThrow();

        assertEquals(
                3.0,
                integerRange
                        .min()
                        .getDouble(LootContext.from(VanillaInterface.defaults(), Map.of())));
        assertInstanceOf(IntBinaryTag.class, encoded);
    }
}
