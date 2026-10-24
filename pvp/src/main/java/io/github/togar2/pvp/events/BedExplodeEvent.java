package io.github.togar2.pvp.events;

import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.Player;
import net.minestom.server.event.trait.CancellableEvent;
import net.minestom.server.event.trait.PlayerInstanceEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Called when a player uses a bed in a dimension that triggers a bed explosion.
 */
public final class BedExplodeEvent implements PlayerInstanceEvent, CancellableEvent {

	private final Player player;
	private final Point blockPosition;

	private boolean cancelled;

	public BedExplodeEvent(@NotNull Player player, @NotNull Point blockPosition) {
		this.player = player;
		this.blockPosition = blockPosition;
	}

	@Override
	public @NotNull Player getPlayer() {
		return this.player;
	}

	public @NotNull Point getBlockPosition() {
		return this.blockPosition;
	}

	@Override
	public boolean isCancelled() {
		return this.cancelled;
	}

	@Override
	public void setCancelled(boolean cancel) {
		this.cancelled = cancel;
	}
}
