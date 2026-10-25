package dev.oglass.vibenilla.containers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.nbt.BinaryTagTypes;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.ItemEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.BlockHandler;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.client.play.ClientClickWindowPacket;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;
import rocks.minestom.crafting.CookingRecipes;

@EnvTest
final class FurnacesTest {
    private static final Path DATA = Path.of("data", "minecraft");
    private static final BlockVec POSITION = new BlockVec(8, 40, 8);

    private final List<Integer> experience = new ArrayList<>();

    private Instance instanceWith(Env env) {
        env.process()
                .eventHandler()
                .addChild(
                        Furnaces.register(
                                env.process().block(),
                                new CookingRecipes(DATA),
                                new CookingFuels(DATA),
                                (instance, position, points) -> experience.add(points)));
        return env.createFlatInstance();
    }

    private static Block furnace(Env env, Block block) {
        return block.withHandler(env.process().block().getHandlerOrDummy(block.name()));
    }

    private static void interact(Player player, BlockVec position) {
        var instance = player.getInstance();
        var block = instance.getBlock(position);
        block.handler()
                .onInteract(
                        new BlockHandler.Interaction(
                                block,
                                instance,
                                BlockFace.TOP,
                                position,
                                new Vec(0.5, 1, 0.5),
                                player,
                                PlayerHand.MAIN));
    }

    /** Sends a pickup click on a slot of the furnace window; button 1 is a right click. */
    private static void pickup(Player player, int slot, int button) {
        player.addPacketToQueue(
                new ClientClickWindowPacket(
                        player.getOpenInventory().getWindowId(),
                        0,
                        (short) slot,
                        (byte) button,
                        ClientClickWindowPacket.ClickType.PICKUP,
                        Map.of(),
                        ItemStack.Hash.AIR));
        player.interpretPacketQueue();
    }

    private static void tick(Env env, int ticks) {
        for (var i = 0; i < ticks; i++) {
            env.tick();
        }
    }

    private static ItemStack slot(Instance instance, int slot) {
        var items =
                instance.getBlock(POSITION).nbtOrEmpty().getList("Items", BinaryTagTypes.COMPOUND);

        for (var tag : items) {
            if (((CompoundBinaryTag) tag).getByte("Slot") == slot) {
                return ItemStack.fromItemNBT((CompoundBinaryTag) tag);
            }
        }
        return ItemStack.AIR;
    }

    private static CompoundBinaryTag item(int slot, Material material, int count) {
        return ItemStack.of(material, count).toItemNBT().putByte("Slot", (byte) slot);
    }

    /** Opens the furnace and fills the input and fuel slots. */
    private static Player load(
            Env env, Instance instance, Block block, ItemStack input, ItemStack fuel) {
        instance.setBlock(POSITION, furnace(env, block));
        var player = env.createPlayer(instance, new Pos(8, 41, 10));
        interact(player, POSITION);
        player.getOpenInventory().setItemStack(0, input);
        player.getOpenInventory().setItemStack(1, fuel);
        return player;
    }

    @Test
    void furnaceSmeltsIronOreIn200Ticks(Env env) {
        var instance = instanceWith(env);
        var player =
                load(
                        env,
                        instance,
                        Block.FURNACE,
                        ItemStack.of(Material.IRON_ORE, 2),
                        ItemStack.of(Material.COAL));

        tick(env, 199);
        assertTrue(player.getOpenInventory().getItemStack(2).isAir());
        assertEquals("true", instance.getBlock(POSITION).getProperty("lit"));

        tick(env, 1);
        assertEquals(ItemStack.of(Material.IRON_INGOT), player.getOpenInventory().getItemStack(2));
        assertEquals(ItemStack.of(Material.IRON_ORE), player.getOpenInventory().getItemStack(0));
        assertTrue(player.getOpenInventory().getItemStack(1).isAir());

        var nbt = instance.getBlock(POSITION).nbt();
        assertEquals(Material.IRON_INGOT, slot(instance, 2).material());
        assertEquals(1600, nbt.getInt("lit_total_time"));
        assertTrue(nbt.getInt("lit_time_remaining") > 1300);
        assertEquals(1, nbt.getCompound("RecipesUsed").size());
    }

    @Test
    void blastFurnaceSmeltsTwiceAsFastAndBurnsFuelTwiceAsFast(Env env) {
        var instance = instanceWith(env);
        var player =
                load(
                        env,
                        instance,
                        Block.BLAST_FURNACE,
                        ItemStack.of(Material.IRON_ORE),
                        ItemStack.of(Material.COAL));

        tick(env, 99);
        assertTrue(player.getOpenInventory().getItemStack(2).isAir());

        tick(env, 1);
        assertEquals(ItemStack.of(Material.IRON_INGOT), player.getOpenInventory().getItemStack(2));
        assertEquals(800, instance.getBlock(POSITION).nbt().getInt("lit_total_time"));
    }

    @Test
    void smokerOnlyCooksFood(Env env) {
        var instance = instanceWith(env);
        var player =
                load(
                        env,
                        instance,
                        Block.SMOKER,
                        ItemStack.of(Material.IRON_ORE),
                        ItemStack.of(Material.COAL));

        tick(env, 250);
        assertTrue(player.getOpenInventory().getItemStack(2).isAir());
        assertEquals(ItemStack.of(Material.COAL), player.getOpenInventory().getItemStack(1));
        assertEquals("false", instance.getBlock(POSITION).getProperty("lit"));

        player.getOpenInventory().setItemStack(0, ItemStack.of(Material.BEEF));
        tick(env, 100);
        assertEquals(ItemStack.of(Material.COOKED_BEEF), player.getOpenInventory().getItemStack(2));
    }

    @Test
    void lavaBucketLeavesAnEmptyBucket(Env env) {
        var instance = instanceWith(env);
        var player =
                load(
                        env,
                        instance,
                        Block.FURNACE,
                        ItemStack.of(Material.IRON_ORE),
                        ItemStack.of(Material.LAVA_BUCKET));

        tick(env, 1);
        assertEquals(ItemStack.of(Material.BUCKET), player.getOpenInventory().getItemStack(1));
        assertEquals(20000, instance.getBlock(POSITION).nbt().getInt("lit_total_time"));
    }

    @Test
    void litFurnaceResumesFromBlockEntityData(Env env) {
        var instance = instanceWith(env);
        var items =
                ListBinaryTag.builder(BinaryTagTypes.COMPOUND)
                        .add(item(0, Material.IRON_ORE, 1))
                        .build();
        var block =
                furnace(env, Block.FURNACE)
                        .withProperty("lit", "true")
                        .withNbt(
                                CompoundBinaryTag.builder()
                                        .put("Items", items)
                                        .putShort("lit_time_remaining", (short) 500)
                                        .putShort("lit_total_time", (short) 1600)
                                        .putShort("cooking_time_spent", (short) 190)
                                        .putShort("cooking_total_time", (short) 200)
                                        .build());
        instance.setBlock(POSITION, block);

        tick(env, 12);

        var nbt = instance.getBlock(POSITION).nbt();
        assertEquals(Material.IRON_INGOT, slot(instance, 2).material());
        assertTrue(slot(instance, 0).isAir());
        assertEquals(0, nbt.getInt("cooking_time_spent"));
        assertTrue(nbt.getInt("lit_time_remaining") < 500);

        tick(env, 600);
        assertEquals("false", instance.getBlock(POSITION).getProperty("lit"));
        assertEquals(0, instance.getBlock(POSITION).nbt().getInt("lit_time_remaining"));
    }

    @Test
    void takingTheResultAwardsStoredExperience(Env env) {
        var instance = instanceWith(env);
        var items =
                ListBinaryTag.builder(BinaryTagTypes.COMPOUND)
                        .add(item(2, Material.IRON_INGOT, 3))
                        .build();
        instance.setBlock(
                POSITION,
                furnace(env, Block.FURNACE)
                        .withNbt(
                                CompoundBinaryTag.builder()
                                        .put("Items", items)
                                        .put(
                                                "RecipesUsed",
                                                CompoundBinaryTag.builder()
                                                        .putInt(
                                                                "minecraft:iron_ingot_from_smelting_iron_ore",
                                                                10)
                                                        .build())
                                        .build()));
        var player = env.createPlayer(instance, new Pos(8, 41, 10));

        interact(player, POSITION);
        player.getOpenInventory().leftClick(player, 2);

        assertEquals(1, experience.size());
        assertTrue(experience.getFirst() >= 6 && experience.getFirst() <= 7);
        assertEquals(ItemStack.of(Material.IRON_INGOT, 3), player.getInventory().getCursorItem());
        assertFalse(instance.getBlock(POSITION).nbt().contains("RecipesUsed"));
    }

    @Test
    void breakingDropsContentsAndExperienceAndClosesViews(Env env) {
        var instance = instanceWith(env);
        var player =
                load(
                        env,
                        instance,
                        Block.FURNACE,
                        ItemStack.of(Material.IRON_ORE, 4),
                        ItemStack.of(Material.COAL, 2));
        var inventory = player.getOpenInventory();
        inventory.setItemStack(2, ItemStack.of(Material.IRON_INGOT, 5));
        tick(env, 200);

        instance.setBlock(POSITION, Block.AIR);

        var drops =
                instance.getEntities().stream()
                        .filter(ItemEntity.class::isInstance)
                        .map(entity -> ((ItemEntity) entity).getItemStack())
                        .toList();
        assertEquals(3, drops.size());
        assertTrue(drops.contains(ItemStack.of(Material.COAL)));
        assertTrue(drops.contains(ItemStack.of(Material.IRON_ORE, 3)));
        assertTrue(drops.contains(ItemStack.of(Material.IRON_INGOT, 6)));
        assertNotSame(inventory, player.getOpenInventory());
        assertTrue(experience.stream().allMatch(points -> points == 1));
    }

    @Test
    void clickingABucketStackIntoTheFuelSlotMovesOneAndBreakingDropsNoMore(Env env) {
        var instance = instanceWith(env);
        var player = load(env, instance, Block.FURNACE, ItemStack.AIR, ItemStack.AIR);
        player.getInventory().setCursorItem(ItemStack.of(Material.BUCKET, 16));

        pickup(player, Furnace.FUEL, 0);

        assertEquals(ItemStack.of(Material.BUCKET), player.getOpenInventory().getItemStack(1));
        assertEquals(ItemStack.of(Material.BUCKET, 15), player.getInventory().getCursorItem());

        instance.setBlock(POSITION, Block.AIR);

        var dropped =
                instance.getEntities().stream()
                        .filter(ItemEntity.class::isInstance)
                        .map(entity -> ((ItemEntity) entity).getItemStack())
                        .toList();
        assertEquals(List.of(ItemStack.of(Material.BUCKET)), dropped);
    }

    @Test
    void swappingABucketStackIntoTheFuelSlotIsCancelled(Env env) {
        var instance = instanceWith(env);
        var player = load(env, instance, Block.FURNACE, ItemStack.AIR, ItemStack.AIR);
        player.getInventory().setItemStack(0, ItemStack.of(Material.BUCKET, 16));

        player.addPacketToQueue(
                new ClientClickWindowPacket(
                        player.getOpenInventory().getWindowId(),
                        0,
                        (short) Furnace.FUEL,
                        (byte) 0,
                        ClientClickWindowPacket.ClickType.SWAP,
                        Map.of(),
                        ItemStack.Hash.AIR));
        player.interpretPacketQueue();

        assertEquals(ItemStack.AIR, player.getOpenInventory().getItemStack(1));
        assertEquals(ItemStack.of(Material.BUCKET, 16), player.getInventory().getItemStack(0));
    }

    @Test
    void resultMergesIntoMatchingCursorButNeverTakesItems(Env env) {
        var instance = instanceWith(env);
        var player = load(env, instance, Block.FURNACE, ItemStack.AIR, ItemStack.AIR);
        var inventory = player.getOpenInventory();
        inventory.setItemStack(2, ItemStack.of(Material.IRON_INGOT, 5));
        player.getInventory().setCursorItem(ItemStack.of(Material.IRON_INGOT, 62));

        pickup(player, Furnace.OUTPUT, 1);

        assertEquals(ItemStack.of(Material.IRON_INGOT, 64), player.getInventory().getCursorItem());
        assertEquals(ItemStack.of(Material.IRON_INGOT, 3), inventory.getItemStack(2));

        player.getInventory().setCursorItem(ItemStack.of(Material.GOLD_INGOT));
        pickup(player, Furnace.OUTPUT, 0);

        assertEquals(ItemStack.of(Material.GOLD_INGOT), player.getInventory().getCursorItem());
        assertEquals(ItemStack.of(Material.IRON_INGOT, 3), inventory.getItemStack(2));
    }
}
