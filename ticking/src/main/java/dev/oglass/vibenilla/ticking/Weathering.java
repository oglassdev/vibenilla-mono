package dev.oglass.vibenilla.ticking;

import java.util.HashMap;
import java.util.Map;
import java.util.random.RandomGenerator;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;

/**
 * Vanilla {@code WeatheringCopper} and {@code ChangeOverTimeBlock}: unwaxed copper oxidizes one
 * stage at a time, held back by less oxidized copper nearby.
 */
final class Weathering {
    private static final float CHANGE_CHANCE = 0.05688889F;
    private static final float UNAFFECTED_MODIFIER = 0.75F;
    private static final int RADIUS = 4;
    private static final String[] PREFIXES = {"", "exposed_", "weathered_", "oxidized_"};

    private static final Map<String, Integer> STAGES = new HashMap<>();
    private static final Map<String, Block> NEXT = new HashMap<>();

    static {
        for (var block : Block.values()) {
            var name = block.key().value();
            if (name.startsWith("waxed_") || hasStagePrefix(name)) {
                continue;
            }

            var base = name.equals("copper_block") ? "copper" : name;
            var stages = new Block[PREFIXES.length];
            for (var stage = 0; stage < stages.length; stage++) {
                stages[stage] =
                        Block.fromKey("minecraft:" + PREFIXES[stage] + (stage == 0 ? name : base));
            }
            if (stages[1] == null || stages[2] == null || stages[3] == null) {
                continue;
            }

            for (var stage = 0; stage < stages.length; stage++) {
                var stageName = stages[stage].key().value();
                STAGES.put(stageName, stage);
                if (stage + 1 < stages.length) {
                    NEXT.put(stageName, stages[stage + 1]);
                }
            }
        }
    }

    private Weathering() {}

    static void register(RandomTicks ticks) {
        ticks.register(block -> NEXT.containsKey(block.key().value()), Weathering::tick);
    }

    private static boolean hasStagePrefix(String name) {
        for (var stage = 1; stage < PREFIXES.length; stage++) {
            if (name.startsWith(PREFIXES[stage])) {
                return true;
            }
        }
        return false;
    }

    static void tick(Instance instance, BlockVec position, Block block, RandomGenerator random) {
        if (random.nextFloat() >= CHANGE_CHANCE) {
            return;
        }

        var name = block.key().value();
        var stage = STAGES.get(name);
        var same = 0;
        var higher = 0;
        for (var x = -RADIUS; x <= RADIUS; x++) {
            for (var y = -RADIUS; y <= RADIUS; y++) {
                for (var z = -RADIUS; z <= RADIUS; z++) {
                    var distance = Math.abs(x) + Math.abs(y) + Math.abs(z);
                    if (distance == 0 || distance > RADIUS) {
                        continue;
                    }

                    var other =
                            STAGES.get(Level.block(instance, position.add(x, y, z)).key().value());
                    if (other == null) {
                        continue;
                    }
                    if (other < stage) {
                        return;
                    }
                    if (other > stage) {
                        higher++;
                    } else {
                        same++;
                    }
                }
            }
        }

        var ratio = (float) (higher + 1) / (float) (higher + same + 1);
        var modifier = stage == 0 ? UNAFFECTED_MODIFIER : 1.0F;
        if (random.nextFloat() < ratio * ratio * modifier) {
            var next = NEXT.get(name).withProperties(block.properties());
            Level.setAndUpdate(
                    instance, position, next.withHandler(block.handler()).withNbt(block.nbt()));
        }
    }
}
