package rocks.minestom.worldgen.structure.processor;

import java.util.List;
import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.random.RandomSource;
import rocks.minestom.worldgen.structure.context.BlockTagManager;

/**
 * Vanilla {@code RuleTest}: predicates over a block state, drawing from the per-block rule random
 * exactly like vanilla (short-circuit order matters for random-draw parity).
 */
public interface RuleTest {
    boolean test(Block state, BlockVec pos, RandomSource random, BlockTagManager blockTags);

    record AlwaysTrueTest() implements RuleTest {
        public static final AlwaysTrueTest INSTANCE = new AlwaysTrueTest();

        @Override
        public boolean test(
                Block state, BlockVec pos, RandomSource random, BlockTagManager blockTags) {
            return true;
        }
    }

    record BlockMatchTest(Key block) implements RuleTest {
        @Override
        public boolean test(
                Block state, BlockVec pos, RandomSource random, BlockTagManager blockTags) {
            return state.key().equals(this.block);
        }
    }

    record BlockStateMatchTest(Block state) implements RuleTest {
        @Override
        public boolean test(
                Block block, BlockVec pos, RandomSource random, BlockTagManager blockTags) {
            return block.stateId() == this.state.stateId();
        }
    }

    record RandomBlockMatchTest(Key block, float probability) implements RuleTest {
        @Override
        public boolean test(
                Block state, BlockVec pos, RandomSource random, BlockTagManager blockTags) {
            // The nextFloat draw only happens when the block matches (vanilla
            // short-circuits), keeping the shared rule random in sync.
            return state.key().equals(this.block) && random.nextFloat() < this.probability;
        }
    }

    record RandomBlockStateMatchTest(Block state, float probability) implements RuleTest {
        @Override
        public boolean test(
                Block block, BlockVec pos, RandomSource random, BlockTagManager blockTags) {
            return block.stateId() == this.state.stateId() && random.nextFloat() < this.probability;
        }
    }

    record TagMatchTest(Key tag) implements RuleTest {
        @Override
        public boolean test(
                Block state, BlockVec pos, RandomSource random, BlockTagManager blockTags) {
            return blockTags.blocks(this.tag).contains(state.key());
        }
    }

    record AllOfRuleTest(List<RuleTest> rules) implements RuleTest {
        @Override
        public boolean test(
                Block state, BlockVec pos, RandomSource random, BlockTagManager blockTags) {
            for (var rule : this.rules) {
                if (!rule.test(state, pos, random, blockTags)) {
                    return false;
                }
            }
            return true;
        }
    }

    record AnyOfRuleTest(List<RuleTest> rules) implements RuleTest {
        @Override
        public boolean test(
                Block state, BlockVec pos, RandomSource random, BlockTagManager blockTags) {
            for (var rule : this.rules) {
                if (rule.test(state, pos, random, blockTags)) {
                    return true;
                }
            }
            return false;
        }
    }

    record NotRuleTest(RuleTest rule) implements RuleTest {
        @Override
        public boolean test(
                Block state, BlockVec pos, RandomSource random, BlockTagManager blockTags) {
            return !this.rule.test(state, pos, random, blockTags);
        }
    }

    record HeightMatchTest(int minInclusive, int maxInclusive) implements RuleTest {
        @Override
        public boolean test(
                Block state, BlockVec pos, RandomSource random, BlockTagManager blockTags) {
            return this.minInclusive <= pos.blockY() && pos.blockY() <= this.maxInclusive;
        }
    }
}
