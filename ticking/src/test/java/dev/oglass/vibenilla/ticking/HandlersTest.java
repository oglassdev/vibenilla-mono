package dev.oglass.vibenilla.ticking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import java.util.Set;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.event.EventListener;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

@EnvTest
final class HandlersTest {
    private static final BlockVec GROUND = new BlockVec(8, TestWorld.GROUND, 8);
    private static final BlockVec ABOVE = GROUND.add(0, 1, 0);

    private static Instance lit(Env env) {
        var instance = env.createFlatInstance();
        TestWorld.load(instance, 0, 0);
        return instance;
    }

    private static int age(Instance instance, BlockVec position) {
        return Integer.parseInt(instance.getBlock(position).getProperty("age"));
    }

    @Test
    void wheatGrowsWithLightAndStaysPutInTheDark(Env env) {
        var instance = lit(env);
        instance.setBlock(GROUND, Block.FARMLAND.withProperty("moisture", "7"));
        instance.setBlock(ABOVE, Block.WHEAT);
        var random = new Random(1);

        for (var tick = 0; tick < 100; tick++) {
            Crops.grow(instance, ABOVE, instance.getBlock(ABOVE), 7, random);
        }
        assertEquals(7, age(instance, ABOVE));

        instance.setBlock(ABOVE, Block.WHEAT);
        for (var section : instance.getChunk(0, 0).getSections()) {
            section.skyLight().set(new byte[2048]);
        }
        for (var tick = 0; tick < 100; tick++) {
            Crops.grow(instance, ABOVE, instance.getBlock(ABOVE), 7, random);
        }
        assertEquals(0, age(instance, ABOVE));
    }

    @Test
    void farmlandDriesThenTurnsToDirtUnlessPlanted(Env env) {
        var instance = lit(env);
        instance.setBlock(GROUND, Block.FARMLAND.withProperty("moisture", "1"));
        var random = new Random(1);

        Farmland.tick(instance, GROUND, instance.getBlock(GROUND), random);
        assertEquals("0", instance.getBlock(GROUND).getProperty("moisture"));

        instance.setBlock(ABOVE, Block.WHEAT);
        Farmland.tick(instance, GROUND, instance.getBlock(GROUND), random);
        assertTrue(instance.getBlock(GROUND).compare(Block.FARMLAND));

        instance.setBlock(ABOVE, Block.AIR);
        Farmland.tick(instance, GROUND, instance.getBlock(GROUND), random);
        assertTrue(instance.getBlock(GROUND).compare(Block.DIRT));
    }

    @Test
    void farmlandNearWaterBecomesFullyMoist(Env env) {
        var instance = lit(env);
        instance.setBlock(GROUND, Block.FARMLAND);
        instance.setBlock(GROUND.add(4, 0, 4), Block.WATER);

        Farmland.tick(instance, GROUND, instance.getBlock(GROUND), new Random(1));
        assertEquals("7", instance.getBlock(GROUND).getProperty("moisture"));
    }

    @Test
    void leavesCutOffFromLogsDecayAndAnnounceIt(Env env) {
        var instance = lit(env);
        var decaying =
                Block.OAK_LEAVES.withProperty("persistent", "false").withProperty("distance", "7");
        var supported = decaying.withProperty("distance", "3");
        var placed = decaying.withProperty("persistent", "true");
        assertTrue(Leaves.isDecaying(decaying));
        assertFalse(Leaves.isDecaying(supported));
        assertFalse(Leaves.isDecaying(placed));

        var announced = new Block[1];
        var listener =
                EventListener.of(LeavesDecayEvent.class, event -> announced[0] = event.getBlock());
        env.process().eventHandler().addListener(listener);
        instance.setBlock(ABOVE, decaying);
        Leaves.decay(instance, ABOVE, decaying, new Random(1));

        assertEquals(decaying, announced[0]);
        assertTrue(instance.getBlock(ABOVE).air());
    }

    @Test
    void grassDiesUnderStoneAndSpreadsToDirt(Env env) {
        var instance = lit(env);
        instance.setBlock(GROUND, Block.GRASS_BLOCK);
        instance.setBlock(ABOVE, Block.STONE);
        SpreadingGrass.tick(instance, GROUND, Block.GRASS_BLOCK, new Random(1));
        assertTrue(instance.getBlock(GROUND).compare(Block.DIRT));

        instance.setBlock(GROUND, Block.GRASS_BLOCK);
        instance.setBlock(ABOVE, Block.OAK_SLAB.withProperty("type", "bottom"));
        SpreadingGrass.tick(instance, GROUND, Block.GRASS_BLOCK, new Random(1));
        assertTrue(instance.getBlock(GROUND).compare(Block.DIRT));

        instance.setBlock(GROUND, Block.GRASS_BLOCK);
        instance.setBlock(ABOVE, Block.AIR);
        instance.setBlock(GROUND.add(1, 0, 0), Block.DIRT);
        var random = new Random(1);
        for (var tick = 0; tick < 50; tick++) {
            SpreadingGrass.tick(instance, GROUND, Block.GRASS_BLOCK, random);
        }
        assertTrue(instance.getBlock(GROUND.add(1, 0, 0)).compare(Block.GRASS_BLOCK));
    }

    @Test
    void fullyAgedSugarCaneGrowsUpToThreeHigh(Env env) {
        var instance = lit(env);
        instance.setBlock(GROUND, Block.SAND);
        instance.setBlock(ABOVE, Block.SUGAR_CANE.withProperty("age", "15"));

        GrowingPlants.sugarCane(instance, ABOVE, instance.getBlock(ABOVE), new Random(1));
        assertTrue(instance.getBlock(ABOVE.add(0, 1, 0)).compare(Block.SUGAR_CANE));

        instance.setBlock(ABOVE.add(0, 1, 0), Block.SUGAR_CANE.withProperty("age", "15"));
        GrowingPlants.sugarCane(
                instance, ABOVE.add(0, 1, 0), instance.getBlock(ABOVE.add(0, 1, 0)), new Random(1));
        assertTrue(instance.getBlock(ABOVE.add(0, 2, 0)).compare(Block.SUGAR_CANE));

        instance.setBlock(ABOVE.add(0, 2, 0), Block.SUGAR_CANE.withProperty("age", "15"));
        GrowingPlants.sugarCane(
                instance, ABOVE.add(0, 2, 0), instance.getBlock(ABOVE.add(0, 2, 0)), new Random(1));
        assertTrue(instance.getBlock(ABOVE.add(0, 3, 0)).air());
    }

    @Test
    void fireOnlyBurnsWhileAPlayerIsNear(Env env) {
        var instance = lit(env);
        var fire = Fire.register(new RandomTicks(), Set.of());
        var planks = ABOVE.add(1, 0, 0);
        instance.setBlock(ABOVE, Block.FIRE);
        instance.setBlock(planks, Block.OAK_PLANKS);
        var random = new Random(1);

        for (var tick = 0; tick < 300; tick++) {
            fire.tick(instance, ABOVE, random);
        }
        assertTrue(instance.getBlock(planks).compare(Block.OAK_PLANKS));

        env.createPlayer(instance, new Pos(8, TestWorld.GROUND + 1, 8));
        for (var tick = 0;
                tick < 300 && instance.getBlock(planks).compare(Block.OAK_PLANKS);
                tick++) {
            if (!instance.getBlock(ABOVE).compare(Block.FIRE)) {
                instance.setBlock(ABOVE, Block.FIRE);
            }
            fire.tick(instance, ABOVE, random);
        }
        assertFalse(instance.getBlock(planks).compare(Block.OAK_PLANKS));
    }

    @Test
    void cactusGrownNextToASolidBlockBreaks(Env env) {
        var instance = lit(env);
        instance.setBlock(GROUND, Block.SAND);
        instance.setBlock(ABOVE, Block.CACTUS.withProperty("age", "15"));
        instance.setBlock(ABOVE.add(1, 1, 0), Block.STONE);

        GrowingPlants.cactus(instance, ABOVE, instance.getBlock(ABOVE), new Random(1));
        assertTrue(instance.getBlock(ABOVE.add(0, 1, 0)).air());

        instance.setBlock(ABOVE.add(1, 1, 0), Block.AIR);
        instance.setBlock(ABOVE, Block.CACTUS.withProperty("age", "15"));
        GrowingPlants.cactus(instance, ABOVE, instance.getBlock(ABOVE), new Random(1));
        assertTrue(instance.getBlock(ABOVE.add(0, 1, 0)).compare(Block.CACTUS));
    }

    @Test
    void waterloggedBlocksDoNotCatchFire() {
        assertTrue(Flammability.igniteOdds(Block.OAK_SLAB) > 0);
        assertEquals(
                0, Flammability.igniteOdds(Block.OAK_SLAB.withProperty("waterlogged", "true")));
    }
}
