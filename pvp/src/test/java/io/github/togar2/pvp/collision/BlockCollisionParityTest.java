package io.github.togar2.pvp.collision;

import io.github.togar2.pvp.utils.ChunkBlockGetter;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.collision.CollisionUtils;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnvTest
public final class BlockCollisionParityTest {
	private static final List<Block> TEST_BLOCKS = List.of(
			Block.AIR,
			Block.STONE,
			Block.OAK_STAIRS,
			Block.STONE_SLAB,
			Block.OAK_FENCE,
			Block.COBBLESTONE_WALL,
			Block.CHEST,
			Block.ANVIL,
			Block.SOUL_SAND,
			Block.OAK_TRAPDOOR,
			Block.GLASS_PANE,
			Block.CAULDRON,
			Block.SNOW,
			Block.BREWING_STAND
	);

	private static final List<BoundingBox> TEST_BOXES = List.of(
			new BoundingBox(0.6, 1.8, 0.6),
			new BoundingBox(0.0, 0.0, 0.0),
			new BoundingBox(0.25, 0.25, 0.25),
			new BoundingBox(1.4, 1.4, 1.4)
	);

	@Test
	public void matchesMinestomAcrossRandomizedTerrain(Env env) {
		var instance = env.createFlatInstance();
		var random = new Random(20260728L);

		this.buildTerrain(instance, random);

		var blockGetter = new ChunkBlockGetter(instance, null, Block.STONE);
		var mismatches = 0;

		for (var iteration = 0; iteration < 20000; iteration++) {
			var position = new Pos(
					random.nextDouble() * 30.0 - 15.0,
					40.0 + random.nextDouble() * 6.0 - 1.0,
					random.nextDouble() * 30.0 - 15.0
			);
			var velocity = new Vec(
					(random.nextDouble() - 0.5) * this.velocityScale(random),
					(random.nextDouble() - 0.5) * this.velocityScale(random),
					(random.nextDouble() - 0.5) * this.velocityScale(random)
			);
			var boundingBox = TEST_BOXES.get(random.nextInt(TEST_BOXES.size()));
			var singleCollision = random.nextBoolean();

			var expected = CollisionUtils.handlePhysics(blockGetter, boundingBox,
					position, velocity, null, singleCollision);
			var actual = BlockCollision.handlePhysics(boundingBox, velocity,
					position, blockGetter, null, singleCollision);

			if (!this.matches(expected, actual)) mismatches++;
		}

		assertEquals(0, mismatches);
	}

	@Test
	public void matchesMinestomWhenChainingResults(Env env) {
		var instance = env.createFlatInstance();
		var random = new Random(76543L);

		this.buildTerrain(instance, random);

		var blockGetter = new ChunkBlockGetter(instance, null, Block.STONE);
		var mismatches = 0;

		for (var run = 0; run < 400; run++) {
			var position = new Pos(
					random.nextDouble() * 20.0 - 10.0,
					44.0 + random.nextDouble() * 3.0,
					random.nextDouble() * 20.0 - 10.0
			);
			var boundingBox = TEST_BOXES.get(random.nextInt(TEST_BOXES.size()));

			var expectedPosition = position;
			var actualPosition = position;
			net.minestom.server.collision.PhysicsResult expectedPrevious = null;
			PhysicsResult actualPrevious = null;
			var velocity = new Vec(0.0, -0.08, 0.0);

			for (var tick = 0; tick < 25; tick++) {
				var expected = CollisionUtils.handlePhysics(blockGetter, boundingBox,
						expectedPosition, velocity, expectedPrevious, false);
				var actual = BlockCollision.handlePhysics(boundingBox, velocity,
						actualPosition, blockGetter, actualPrevious, false);

				if (!this.matches(expected, actual)) {
					mismatches++;
					break;
				}

				expectedPrevious = expected;
				actualPrevious = actual;
				expectedPosition = expected.newPosition();
				actualPosition = actual.newPosition();
				velocity = expected.newVelocity().sub(0.0, 0.08, 0.0).mul(0.98);
			}
		}

		assertEquals(0, mismatches);
	}

	@Test
	public void matchesMinestomForZeroVelocity(Env env) {
		var instance = env.createFlatInstance();
		var blockGetter = new ChunkBlockGetter(instance, null, Block.STONE);
		var position = new Pos(0.5, 41.0, 0.5);
		var boundingBox = new BoundingBox(0.6, 1.8, 0.6);

		var expected = CollisionUtils.handlePhysics(blockGetter, boundingBox, position, Vec.ZERO, null, false);
		var actual = BlockCollision.handlePhysics(boundingBox, Vec.ZERO, position, blockGetter, null, false);

		assertEquals(true, this.matches(expected, actual));
	}

	private double velocityScale(Random random) {
		var roll = random.nextInt(3);

		if (roll == 0) return 0.2;
		if (roll == 1) return 2.0;

		return 12.0;
	}

	private void buildTerrain(Instance instance, Random random) {
		for (var blockX = -18; blockX <= 18; blockX++) {
			for (var blockZ = -18; blockZ <= 18; blockZ++) {
				for (var blockY = 39; blockY <= 47; blockY++) {
					var block = blockY <= 40
							? Block.STONE
							: TEST_BLOCKS.get(random.nextInt(TEST_BLOCKS.size()));
					instance.setBlock(blockX, blockY, blockZ, block);
				}
			}
		}
	}

	private boolean matches(net.minestom.server.collision.PhysicsResult expected, PhysicsResult actual) {
		return expected.newPosition().samePoint(actual.newPosition())
				&& expected.newVelocity().samePoint(actual.newVelocity())
				&& expected.isOnGround() == actual.isOnGround()
				&& expected.collisionX() == actual.collisionX()
				&& expected.collisionY() == actual.collisionY()
				&& expected.collisionZ() == actual.collisionZ()
				&& expected.hasCollision() == actual.hasCollision();
	}
}
