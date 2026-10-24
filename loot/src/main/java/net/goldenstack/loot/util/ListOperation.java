package net.goldenstack.loot.util;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.Result;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.registry.DynamicRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("UnstableApiUsage")
public sealed interface ListOperation {

    @NotNull
    StructCodec<ListOperation> CODEC =
            Codec.RegistryTaggedUnion(
                    registries -> {
                        class Holder {
                            static final @NotNull DynamicRegistry<
                                            StructCodec<? extends ListOperation>>
                                    CODEC = createDefaultRegistry();
                        }
                        return Holder.CODEC;
                    },
                    ListOperation::codec,
                    "mode");

    @NotNull
    Codec<Integer> NON_NEGATIVE_INT =
            Codec.INT.transform(
                    value -> {
                        if (value < 0) {
                            throw new IllegalArgumentException("Value must not be negative");
                        }

                        return value;
                    },
                    value -> value);

    static @NotNull DynamicRegistry<StructCodec<? extends ListOperation>> createDefaultRegistry() {
        final DynamicRegistry<StructCodec<? extends ListOperation>> registry =
                DynamicRegistry.create(Key.key("list_operations"));
        registry.register("append", Append.CODEC);
        registry.register("insert", Insert.CODEC);
        registry.register("replace_all", ReplaceAll.CODEC);
        registry.register("replace_section", ReplaceSection.CODEC);
        return registry;
    }

    static @NotNull StructCodec<ListOperation> codec(int maxSize) {
        return new StructCodec<>() {
            @Override
            public <D> Result<ListOperation> decodeFromMap(
                    Transcoder<D> coder, Transcoder.MapLike<D> map) {
                var result = ListOperation.CODEC.decodeFromMap(coder, map);

                if (!(result instanceof Result.Ok(ListOperation operation))) {
                    return result.cast();
                }

                if (operation instanceof ReplaceSection replaceSection
                        && replaceSection.size() != null
                        && replaceSection.size() > maxSize) {
                    return new Result.Error<>("Size value too large: " + replaceSection.size());
                }

                return new Result.Ok<>(operation);
            }

            @Override
            public <D> Result<D> encodeToMap(
                    Transcoder<D> coder, ListOperation value, Transcoder.MapBuilder<D> map) {
                return ListOperation.CODEC.encodeToMap(coder, value, map);
            }
        };
    }

    default <T> @NotNull List<T> apply(@NotNull List<T> values, @NotNull List<T> input) {
        return this.apply(values, input, Integer.MAX_VALUE);
    }

    <T> @NotNull List<T> apply(@NotNull List<T> values, @NotNull List<T> input, int maxSize);

    /**
     * @return the codec that can encode this list operation
     */
    @NotNull
    StructCodec<? extends ListOperation> codec();

    record WithValues<T>(@NotNull ListOperation operation, @NotNull List<T> values) {
        public static <T> Codec<WithValues<T>> codec(Codec<T> codec) {
            return codec(codec, Integer.MAX_VALUE);
        }

        public static <T> Codec<WithValues<T>> codec(Codec<T> codec, int maxSize) {
            return StructCodec.struct(
                    StructCodec.INLINE,
                    ListOperation.codec(maxSize),
                    WithValues::operation,
                    "values",
                    codec.list(maxSize),
                    WithValues::values,
                    WithValues::new);
        }
    }

    record Append() implements ListOperation {
        public static final @NotNull StructCodec<Append> CODEC = StructCodec.struct(Append::new);

        @Override
        public @NotNull <T> List<T> apply(
                @NotNull List<T> values, @NotNull List<T> input, int maxSize) {
            if (input.size() + values.size() > maxSize) {
                return input;
            }

            return Stream.concat(input.stream(), values.stream()).toList();
        }

        @Override
        public @NotNull StructCodec<? extends ListOperation> codec() {
            return CODEC;
        }
    }

    record Insert(int offset) implements ListOperation {
        public static final @NotNull StructCodec<Insert> CODEC =
                StructCodec.struct(
                        "offset", NON_NEGATIVE_INT.optional(0), Insert::offset, Insert::new);

        @Override
        public @NotNull <T> List<T> apply(
                @NotNull List<T> values, @NotNull List<T> input, int maxSize) {
            if (this.offset > input.size() || input.size() + values.size() > maxSize) {
                return input;
            }

            var items = new ArrayList<T>();
            items.addAll(input.subList(0, this.offset));
            items.addAll(values);
            items.addAll(input.subList(this.offset, input.size()));
            return items;
        }

        @Override
        public @NotNull StructCodec<? extends ListOperation> codec() {
            return CODEC;
        }
    }

    record ReplaceAll() implements ListOperation {
        public static final @NotNull StructCodec<ReplaceAll> CODEC =
                StructCodec.struct(ReplaceAll::new);

        @Override
        public @NotNull <T> List<T> apply(
                @NotNull List<T> values, @NotNull List<T> input, int maxSize) {
            return values;
        }

        @Override
        public @NotNull StructCodec<? extends ListOperation> codec() {
            return CODEC;
        }
    }

    record ReplaceSection(int offset, @Nullable Integer size) implements ListOperation {
        public static final @NotNull StructCodec<ReplaceSection> CODEC =
                StructCodec.struct(
                        "offset",
                        NON_NEGATIVE_INT.optional(0),
                        ReplaceSection::offset,
                        "size",
                        NON_NEGATIVE_INT.optional(),
                        ReplaceSection::size,
                        ReplaceSection::new);

        @Override
        public @NotNull <T> List<T> apply(
                @NotNull List<T> values, @NotNull List<T> input, int maxSize) {
            if (this.offset > input.size() || this.size != null && this.size < 0) {
                return input;
            }

            var items = new ArrayList<T>();
            items.addAll(input.subList(0, this.offset));
            items.addAll(values);

            var size = this.size != null ? this.size : values.size();
            var resumeIndex = this.offset + size;

            if (resumeIndex < input.size()) {
                items.addAll(input.subList(resumeIndex, input.size()));
            }

            if (items.size() > maxSize) {
                return input;
            }

            return items;
        }

        @Override
        public @NotNull StructCodec<? extends ListOperation> codec() {
            return CODEC;
        }
    }
}
