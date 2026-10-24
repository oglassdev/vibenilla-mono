package net.goldenstack.loot.util.nbt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.util.List;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.IntBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;
import org.junit.jupiter.api.Test;

final class NBTPathTest {

    @Test
    void createsMissingRootObjectsDuringTargetPreparation() throws Exception {
        var path = Parser.readPath(new StringReader("foo.bar"));
        var reference = NBTReference.of(CompoundBinaryTag.empty());

        var values = path.getWithDefaults(reference, () -> IntBinaryTag.intBinaryTag(1));

        assertEquals(1, values.size());
        assertEquals(
                IntBinaryTag.intBinaryTag(1),
                ((CompoundBinaryTag) ((CompoundBinaryTag) reference.get()).get("foo")).get("bar"));
    }

    @Test
    void createsMissingObjectFiltersWithoutReplacingMismatches() throws Exception {
        var path = Parser.readPath(new StringReader("foo{value: 1}"));
        var reference = NBTReference.of(CompoundBinaryTag.empty());

        assertEquals(1, path.getWithDefaults(reference, CompoundBinaryTag::empty).size());

        var mismatched =
                NBTReference.of(
                        CompoundBinaryTag.builder()
                                .put(
                                        "foo",
                                        CompoundBinaryTag.builder()
                                                .put("value", IntBinaryTag.intBinaryTag(2))
                                                .build())
                                .build());

        assertTrue(path.getWithDefaults(mismatched, CompoundBinaryTag::empty).isEmpty());
        assertEquals(
                IntBinaryTag.intBinaryTag(2),
                ((CompoundBinaryTag) ((CompoundBinaryTag) mismatched.get()).get("foo"))
                        .get("value"));
    }

    @Test
    void rootObjectFiltersDoNotModifyTheRootDuringPreparation() throws Exception {
        var path = Parser.readPath(new StringReader("{value: 1}"));
        var reference =
                NBTReference.of(
                        CompoundBinaryTag.builder()
                                .put("value", IntBinaryTag.intBinaryTag(2))
                                .build());

        assertTrue(path.getWithDefaults(reference, CompoundBinaryTag::empty).isEmpty());
        assertEquals(
                IntBinaryTag.intBinaryTag(2), ((CompoundBinaryTag) reference.get()).get("value"));
    }

    @Test
    void listFiltersRequireVanillaListOrdering() {
        var filter =
                CompoundBinaryTag.builder()
                        .put(
                                "values",
                                ListBinaryTag.builder()
                                        .add(IntBinaryTag.intBinaryTag(1))
                                        .add(IntBinaryTag.intBinaryTag(2))
                                        .build())
                        .build();
        var path = new NBTPath(List.of(new NBTPath.Selector.ListFilter(filter)));
        var source =
                ListBinaryTag.builder()
                        .add(
                                CompoundBinaryTag.builder()
                                        .put(
                                                "values",
                                                ListBinaryTag.builder()
                                                        .add(IntBinaryTag.intBinaryTag(2))
                                                        .add(IntBinaryTag.intBinaryTag(1))
                                                        .build())
                                        .build())
                        .build();

        assertTrue(path.get(source).isEmpty());
    }

    @Test
    void preservesQuotedKeysWhenFormattingPaths() throws Exception {
        var path = Parser.readPath(new StringReader("\"a.b\""));
        var source = CompoundBinaryTag.builder().put("a.b", IntBinaryTag.intBinaryTag(1)).build();

        assertEquals("\"a.b\"", path.toString());
        assertEquals(1, path.get(source).size());
    }

    @Test
    void rejectsUnclosedListSelectors() {
        assertThrows(
                IllegalArgumentException.class, () -> Parser.readPath(new StringReader("foo[0")));
    }
}
