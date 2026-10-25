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
            }

            if (!allowed(player, click)) {
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
    private boolean allowed(Player player, Click click) {
        var cursor = player.getInventory().getCursorItem();

        return switch (click) {
            case Click.LeftDrag drag -> dragAllowed(drag.slots(), cursor);
            case Click.RightDrag drag -> dragAllowed(drag.slots(), cursor);
            case Click.MiddleDrag drag -> dragAllowed(drag.slots(), cursor);
            default ->
                    switch (click.slot()) {
                        case Furnace.OUTPUT -> takesResult(player, click, cursor);
                        case Furnace.FUEL -> placesFuel(player, click, cursor);
                        default -> true;
                    };
        };
    }

    private boolean dragAllowed(List<Integer> slots, ItemStack cursor) {
        return !slots.contains(Furnace.OUTPUT)
                && (!slots.contains(Furnace.FUEL) || isFuelSlotItem(cursor));
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

    private boolean placesFuel(Player player, Click click, ItemStack cursor) {
        return switch (click) {
            case Click.Left left -> cursor.isAir() || isFuelSlotItem(cursor);
            case Click.Right right -> cursor.isAir() || isFuelSlotItem(cursor);
            case Click.HotbarSwap swap -> {
                var item = player.getInventory().getItemStack(swap.hotbarSlot());
                yield item.isAir() || isFuelSlotItem(item);
            }
            case Click.OffhandSwap swap -> {
                var item = player.getItemInOffHand();
                yield item.isAir() || isFuelSlotItem(item);
            }
            default -> true;
        };
    }

    private boolean isFuelSlotItem(ItemStack item) {
        return item.isAir() || cooking.fuels().isFuel(item) || item.material() == Material.BUCKET;
    }

    /** Moves a smeltable item to the input slot or a fuel to the fuel slot, like vanilla. */
    private void quickMove(Furnace furnace, Player player, int slot, ItemStack stack) {
        if (stack.isAir()) {
            return;
        }

        var target =
                cooking.recipes().find(furnace.kind.recipeType(), stack) != null
                        ? Furnace.INPUT
                        : cooking.fuels().isFuel(stack) ? Furnace.FUEL : -1;

        if (target < 0) {
            return;
        }

        var current = furnace.view.getItemStack(target);
        var space =
                current.isAir()
                        ? stack.maxStackSize()
                        : current.isSimilar(stack) ? current.maxStackSize() - current.amount() : 0;
        var moved = Math.min(space, stack.amount());

        if (moved > 0) {
            furnace.view.setItemStack(target, stack.withAmount(current.amount() + moved));
            player.getInventory()
                    .setItemStack(
                            slot,
                            moved == stack.amount()
                                    ? ItemStack.AIR
                                    : stack.withAmount(stack.amount() - moved));
        }
    }
}
