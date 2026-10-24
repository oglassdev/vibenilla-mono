package net.goldenstack.loot.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import net.minestom.server.MinecraftServer;
import net.minestom.server.adventure.MinestomAdventure;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.registry.RegistryTranscoder;
import org.junit.jupiter.api.Test;

class ListOperationTest {

    @Test
    void decodesVanillaModeAndRejectsNegativeOffsets() throws Exception {
        MinecraftServer.init();

        var appendInput = MinestomAdventure.tagStringIO().asTag("{mode: \"append\"}");
        var invalidInput = MinestomAdventure.tagStringIO().asTag("{mode: \"insert\", offset: -1}");
        var transcoder = new RegistryTranscoder<>(Transcoder.NBT, MinecraftServer.process());

        var append = ListOperation.CODEC.decode(transcoder, appendInput).orElseThrow();

        assertInstanceOf(ListOperation.Append.class, append);
        assertThrows(
                IllegalArgumentException.class,
                () -> ListOperation.CODEC.decode(transcoder, invalidInput).orElseThrow());
    }

    @Test
    void preservesOriginalListWhenVanillaOperationCannotApply() {
        var input = List.of(1, 2);
        var replacement = List.of(3, 4);

        assertEquals(input, new ListOperation.Append().apply(replacement, input, 3));
        assertEquals(input, new ListOperation.Insert(3).apply(replacement, input, 10));
        assertEquals(input, new ListOperation.ReplaceSection(0, 0).apply(replacement, input, 2));
    }
}
