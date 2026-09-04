package net.goldenstack.loot.util.nbt;

import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.IntBinaryTag;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NBTPathTest {

    @Test
    void createsMissingRootObjectsDuringTargetPreparation() throws Exception {
        var path = Parser.readPath(new StringReader("foo.bar"));
        var reference = NBTReference.of(CompoundBinaryTag.empty());

        var values = path.getWithDefaults(reference, () -> IntBinaryTag.intBinaryTag(1));

        assertEquals(1, values.size());
        assertEquals(IntBinaryTag.intBinaryTag(1), ((CompoundBinaryTag) ((CompoundBinaryTag) reference.get()).get("foo")).get("bar"));
    }

    @Test
    void createsMissingObjectFiltersWithoutReplacingMismatches() throws Exception {
        var path = Parser.readPath(new StringReader("foo{value: 1}"));
        var reference = NBTReference.of(CompoundBinaryTag.empty());

        assertEquals(1, path.getWithDefaults(reference, CompoundBinaryTag::empty).size());

        var mismatched = NBTReference.of(CompoundBinaryTag.builder()
                .put("foo", CompoundBinaryTag.builder().put("value", IntBinaryTag.intBinaryTag(2)).build())
                .build());

        assertTrue(path.getWithDefaults(mismatched, CompoundBinaryTag::empty).isEmpty());
        assertEquals(IntBinaryTag.intBinaryTag(2), ((CompoundBinaryTag) ((CompoundBinaryTag) mismatched.get()).get("foo")).get("value"));
    }

    @Test
    void rootObjectFiltersDoNotModifyTheRootDuringPreparation() throws Exception {
        var path = Parser.readPath(new StringReader("{value: 1}"));
        var reference = NBTReference.of(CompoundBinaryTag.builder().put("value", IntBinaryTag.intBinaryTag(2)).build());

        assertTrue(path.getWithDefaults(reference, CompoundBinaryTag::empty).isEmpty());
        assertEquals(IntBinaryTag.intBinaryTag(2), ((CompoundBinaryTag) reference.get()).get("value"));
    }

    @Test
    void rejectsUnclosedListSelectors() {
        assertThrows(IllegalArgumentException.class, () -> Parser.readPath(new StringReader("foo[0")));
    }
}
