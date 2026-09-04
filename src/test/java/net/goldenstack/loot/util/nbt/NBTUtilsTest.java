package net.goldenstack.loot.util.nbt;

import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.IntBinaryTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NBTUtilsTest {

    @Test
    void comparesExpectedCompoundFieldsAgainstTheCandidate() {
        var expected = CompoundBinaryTag.builder()
                .put("nested", CompoundBinaryTag.builder().put("value", IntBinaryTag.intBinaryTag(1)).build())
                .build();
        var matching = CompoundBinaryTag.builder()
                .put("nested", CompoundBinaryTag.builder()
                        .put("value", IntBinaryTag.intBinaryTag(1))
                        .put("extra", IntBinaryTag.intBinaryTag(2))
                        .build())
                .build();
        var missing = CompoundBinaryTag.builder()
                .put("nested", CompoundBinaryTag.empty())
                .build();
        var mismatched = CompoundBinaryTag.builder()
                .put("nested", CompoundBinaryTag.builder().put("value", IntBinaryTag.intBinaryTag(2)).build())
                .build();

        assertTrue(NBTUtils.compareNBT(expected, matching, false));
        assertFalse(NBTUtils.compareNBT(expected, missing, false));
        assertFalse(NBTUtils.compareNBT(expected, mismatched, false));
    }
}
