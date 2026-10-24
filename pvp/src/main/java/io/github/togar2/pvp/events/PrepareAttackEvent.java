package io.github.togar2.pvp.events;

import net.minestom.server.entity.Entity;
import net.minestom.server.event.trait.CancellableEvent;
import net.minestom.server.event.trait.EntityInstanceEvent;
import org.jetbrains.annotations.NotNull;

public class PrepareAttackEvent implements EntityInstanceEvent, CancellableEvent {

	private final Entity entity;
	private final Entity target;

	private boolean cancelled;

	public PrepareAttackEvent(@NotNull Entity entity, @NotNull Entity target) {
		this.entity = entity;
		this.target = target;
	}

	@Override
	public @NotNull Entity getEntity() {
		return this.entity;
	}

	public @NotNull Entity getTarget() {
		return this.target;
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
