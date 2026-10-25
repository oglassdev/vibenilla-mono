package dev.oglass.vibenilla.containers;

import net.kyori.adventure.key.Key;
import net.minestom.server.instance.block.BlockHandler;

final class FurnaceHandler implements BlockHandler {
    private final FurnaceKind kind;
    private final FurnaceSessions sessions;

    FurnaceHandler(FurnaceKind kind, FurnaceSessions sessions) {
        this.kind = kind;
        this.sessions = sessions;
    }

    @Override
    public Key getKey() {
        return kind.key();
    }

    @Override
    public boolean onInteract(Interaction interaction) {
        return ContainerHandler.interact(
                interaction,
                () ->
                        sessions.open(
                                interaction.getPlayer(),
                                interaction.getInstance(),
                                interaction.getBlockPosition().asBlockVec(),
                                kind));
    }

    @Override
    public void onPlace(Placement placement) {
        sessions.resume(
                placement.getInstance(),
                placement.getBlockPosition().asBlockVec(),
                kind,
                placement.getBlock());
    }

    @Override
    public void onDestroy(Destroy destroy) {
        var block = destroy.getBlock();

        if (!destroy.getNewBlock().compare(block)) {
            sessions.destroy(
                    destroy.getInstance(), destroy.getBlockPosition().asBlockVec(), kind, block);
        }
    }
}
