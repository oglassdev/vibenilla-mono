package dev.oglass.vibenilla.containers;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.goldenstack.loot.Trove;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.entity.Player;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.inventory.AbstractInventory;
import net.minestom.server.inventory.click.Click;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.timer.TaskSchedule;

/**
 * Tracks the furnaces that are open or have work to do, and ticks them. A furnace is added when a
 * player opens it or when a chunk with a lit or loaded furnace comes in, and dropped once it is
 * idle and unviewed, so ticking costs nothing for furnaces that do nothing.
 *
 * <p>Like {@link Sessions} this holds no locks and assumes one dispatcher thread.
 */
final class FurnaceSessions {
    private record FurnaceKey(Instance instance, BlockVec position) {}

    private static final int HOTBAR_END = 9;
    private static final int MAIN_END = 36;

    private final Map<FurnaceKey, Furnace> furnaces = new ConcurrentHashMap<>();
    private final Furnace.Cooking cooking;

    FurnaceSessions(Furnace.Cooking cooking) {
        this.cooking = cooking;
        MinecraftServer.getSchedulerManager()
                .buildTask(this::tick)
                .repeat(TaskSchedule.tick(1))
                .schedule();
    }

    void open(Player player, Instance instance, BlockVec position, FurnaceKind kind) {
        var key = new FurnaceKey(instance, position);
        var furnace = furnaces.get(key);

        if (furnace == null) {
            furnace = create(kind, instance, position, instance.getBlock(position));
            furnaces.put(key, furnace);
        }

        if (player.getOpenInventory() == furnace.view) {
            return;
        }

        if (!player.openInventory(furnace.view)
                && furnace.view.getViewers().isEmpty()
                && furnace.isIdle()) {
            furnaces.remove(key, furnace);
        }
    }

    /** Starts ticking a furnace that came in with a chunk, if it is lit or has work to do. */
    void resume(Instance instance, BlockVec position, FurnaceKind kind, Block placed) {
        if (placed.nbt() == null || furnaces.containsKey(new FurnaceKey(instance, position))) {
            return;
        }

        MinecraftServer.getSchedulerManager()
                .scheduleEndOfTick(
                        () -> {
                            var key = new FurnaceKey(instance, position);
                            var block =
                                    instance.isChunkLoaded(position)
                                            ? instance.getBlock(position)
                                            : Block.AIR;

                            if (!block.compare(kind.block())
                                    || block.nbt() == null
                                    || furnaces.containsKey(key)) {
                                return;
                            }

                            var furnace = create(kind, instance, position, block);

                            if (!furnace.isLit() && "true".equals(block.getProperty("lit"))) {
                                furnace.save();
                            }

                            if (!furnace.isIdle()) {
                                furnaces.put(key, furnace);
                            }
                        });
    }

    /** Closes the furnace's views, then drops its items and stored experience. */
    void destroy(Instance instance, BlockVec position, FurnaceKind kind, Block block) {
        var furnace = furnaces.remove(new FurnaceKey(instance, position));

        if (furnace == null) {
            furnace = create(kind, instance, position, block);
        }
        closeViews(furnace);

        for (var item : furnace.view.getItemStacks()) {
            if (!item.isAir()) {
                Trove.blockDrop(instance, item, position);
            }
        }

        var points = furnace.takeExperience();

        if (points > 0) {
            cooking.experience().spawn(instance, position.add(0.5, 0.5, 0.5), points);
        }
    }

    /** Closes the view the player has of a furnace, if any. */
    void closeView(Player player) {
        if (player.getOpenInventory() instanceof Furnace.View) {
            player.closeInventory();
        }
    }

    /** Releases the furnace the player is viewing, as if it was closed. */
    void release(Player player) {
        if (player.getOpenInventory() instanceof Furnace.View view) {
            release(view.furnace(), player);
        }
    }

    void closeChunk(Instance instance, int chunkX, int chunkZ) {
        unload(
                furnace ->
                        furnace.instance == instance
                                && furnace.position.chunkX() == chunkX
                                && furnace.position.chunkZ() == chunkZ);
    }

    void closeInstance(Instance instance) {
        unload(furnace -> furnace.instance == instance);
    }

    void preClick(InventoryPreClickEvent event) {
        var player = event.getPlayer();

        if (!(player.getOpenInventory() instanceof Furnace.View view)) {
            return;
        }

        var furnace = view.furnace();
        var click = event.getClick();

        if (event.getInventory() == view) {
            if (click.slot() == Furnace.OUTPUT) {
                furnace.taking(player);

                if (click instanceof Click.Left || click instanceof Click.Right) {
                    mergeResult(furnace, player);
                }
            } else if (click.slot() == Furnace.FUEL) {
                moveOneBucket(furnace, player, click);
            }

            if (!allowed(furnace, player, click)) {
                event.setCancelled(true);
            }
        } else if (event.getInventory() == player.getInventory()
                && (click instanceof Click.LeftShift || click instanceof Click.RightShift)) {
            event.setCancelled(true);
            quickMove(furnace, player, event.getSlot(), event.getClickedItem());
        }
    }

    private Furnace create(FurnaceKind kind, Instance instance, BlockVec position, Block block) {
        return new Furnace(kind, instance, position, block, cooking, this::release);
    }

    private void tick() {
        for (var entry : furnaces.entrySet()) {
            var furnace = entry.getValue();
            furnace.tick();

            if (furnace.isIdle() && furnace.view.getViewers().isEmpty()) {
                furnace.save();
                furnaces.remove(entry.getKey(), furnace);
            }
        }
    }

    private void release(Furnace furnace, Player player) {
        var key = new FurnaceKey(furnace.instance, furnace.position);

        if (furnaces.get(key) == furnace
                && furnace.isIdle()
                && furnace.view.getViewers().stream().allMatch(viewer -> viewer == player)) {
            furnace.save();
            furnaces.remove(key, furnace);
        }
    }

    private void unload(Predicate<Furnace> selected) {
        for (var entry : List.copyOf(furnaces.entrySet())) {
            var furnace = entry.getValue();

            if (selected.test(furnace) && furnaces.remove(entry.getKey(), furnace)) {
                furnace.save();
                closeViews(furnace);
            }
        }
    }

    private static void closeViews(Furnace furnace) {
        for (var viewer : List.copyOf(furnace.view.getViewers())) {
            viewer.closeInventory();
        }
    }

    /** Whether a click in the furnace window may go ahead: the result can only be taken. */
    private boolean allowed(Furnace furnace, Player player, Click click) {
        var cursor = player.getInventory().getCursorItem();

        return switch (click) {
            case Click.LeftDrag drag -> dragAllowed(drag.slots(), cursor);
            case Click.RightDrag drag -> dragAllowed(drag.slots(), cursor);
            case Click.MiddleDrag drag -> dragAllowed(drag.slots(), cursor);
            default ->
                    switch (click.slot()) {
                        case Furnace.OUTPUT -> takesResult(player, click, cursor);
                        case Furnace.FUEL -> placesFuel(furnace, player, click, cursor);
                        default -> true;
                    };
        };
    }

    private boolean dragAllowed(List<Integer> slots, ItemStack cursor) {
        return !slots.contains(Furnace.OUTPUT)
                && (!slots.contains(Furnace.FUEL) || fitsFuelSlot(cursor));
    }

    private boolean takesResult(Player player, Click click, ItemStack cursor) {
        return switch (click) {
            case Click.Left left -> cursor.isAir();
            case Click.Right right -> cursor.isAir();
            case Click.LeftShift shift -> true;
            case Click.RightShift shift -> true;
            case Click.DropSlot drop -> true;
            case Click.HotbarSwap swap ->
                    player.getInventory().getItemStack(swap.hotbarSlot()).isAir();
            case Click.OffhandSwap swap -> player.getItemInOffHand().isAir();
            default -> false;
        };
    }

    private boolean placesFuel(Furnace furnace, Player player, Click click, ItemStack cursor) {
        return switch (click) {
            case Click.Left left -> cursorFits(furnace, cursor, false);
            case Click.Right right -> cursorFits(furnace, cursor, true);
            case Click.HotbarSwap swap ->
                    fitsFuelSlot(player.getInventory().getItemStack(swap.hotbarSlot()));
            case Click.OffhandSwap swap -> fitsFuelSlot(player.getItemInOffHand());
            default -> true;
        };
    }

    /** Whether a stack may sit in the fuel slot: a fuel, or a single empty bucket. */
    private boolean fitsFuelSlot(ItemStack item) {
        return item.isAir()
                || (item.material() == Material.BUCKET
                        ? item.amount() == 1
                        : cooking.fuels().isFuel(item));
    }

    /** Whether a click with the cursor stack leaves the fuel slot with at most one empty bucket. */
    private boolean cursorFits(Furnace furnace, ItemStack cursor, boolean single) {
        if (cursor.material() != Material.BUCKET) {
            return fitsFuelSlot(cursor);
        }

        var fuel = furnace.view.getItemStack(Furnace.FUEL);
        return (single && fuel.isAir())
                || (cursor.amount() == 1 && fuel.material() != Material.BUCKET);
    }

    /** Puts one bucket of a carried stack into an empty fuel slot, as a left click would. */
    private static void moveOneBucket(Furnace furnace, Player player, Click click) {
        var cursor = player.getInventory().getCursorItem();

        if (click instanceof Click.Left
                && cursor.material() == Material.BUCKET
                && cursor.amount() > 1
                && furnace.view.getItemStack(Furnace.FUEL).isAir()) {
            player.getInventory().setCursorItem(cursor.withAmount(cursor.amount() - 1));
            furnace.view.setItemStack(Furnace.FUEL, cursor.withAmount(1));
        }
    }

    /** Moves the result into a carried stack of the same item, up to its maximum size. */
    private static void mergeResult(Furnace furnace, Player player) {
        var cursor = player.getInventory().getCursorItem();
        var output = furnace.view.getItemStack(Furnace.OUTPUT);

        if (cursor.isAir() || !cursor.isSimilar(output)) {
            return;
        }

        var moved = Math.min(output.amount(), cursor.maxStackSize() - cursor.amount());

        if (moved > 0) {
            player.getInventory().setCursorItem(cursor.withAmount(cursor.amount() + moved));
            furnace.view.setItemStack(
                    Furnace.OUTPUT,
                    moved == output.amount()
                            ? ItemStack.AIR
                            : output.withAmount(output.amount() - moved));
        }
    }

    /**
     * Moves a smeltable item to the input slot or a fuel to the fuel slot, and any other item
     * between the main inventory and the hotbar, like vanilla.
     */
    private void quickMove(Furnace furnace, Player player, int slot, ItemStack stack) {
        if (stack.isAir() || slot >= MAIN_END) {
            return;
        }

        var inventory = player.getInventory();
        var remaining =
                cooking.recipes().find(furnace.kind.recipeType(), stack) != null
                        ? insert(furnace.view, Furnace.INPUT, Furnace.INPUT + 1, stack)
                        : cooking.fuels().isFuel(stack)
                                ? insert(furnace.view, Furnace.FUEL, Furnace.FUEL + 1, stack)
                                : slot < HOTBAR_END
                                        ? insert(inventory, HOTBAR_END, MAIN_END, stack)
                                        : insert(inventory, 0, HOTBAR_END, stack);

        if (remaining.amount() != stack.amount()) {
            inventory.setItemStack(slot, remaining);
        }
    }

    /** Fills matching stacks in the range first, then empty slots, and returns what is left. */
    private static ItemStack insert(
            AbstractInventory inventory, int start, int end, ItemStack stack) {
        for (var merge = true; ; merge = false) {
            for (var slot = start; slot < end && !stack.isAir(); slot++) {
                var current = inventory.getItemStack(slot);

                if (current.isAir() == merge || (merge && !current.isSimilar(stack))) {
                    continue;
                }

                var moved = Math.min(stack.amount(), stack.maxStackSize() - current.amount());

                if (moved > 0) {
                    inventory.setItemStack(slot, stack.withAmount(current.amount() + moved));
                    stack =
                            moved == stack.amount()
                                    ? ItemStack.AIR
                                    : stack.withAmount(stack.amount() - moved);
                }
            }

            if (!merge) {
                return stack;
            }
        }
    }
}
