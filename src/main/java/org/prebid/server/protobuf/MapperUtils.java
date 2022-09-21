package org.prebid.server.protobuf;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.ExtensionLite;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.Message;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class MapperUtils {

    private MapperUtils() {
    }

    public static <T, U> U mapNotNull(T value, Function<T, U> mapper) {
        return value != null
                ? mapper.apply(value)
                : null;
    }

    public static <T> void setNotNull(T value, Consumer<T> setter) {
        if (value != null) {
            setter.accept(value);
        }
    }

    public static <ContainingType extends GeneratedMessageV3.ExtendableMessage<ContainingType>, ExtensionType>
    ObjectNode extractAndMapExtension(
            ProtobufExtensionMapper<ContainingType, ExtensionType, ObjectNode> mapper,
            ContainingType value) {

        if (mapper == null || value == null) {
            return null;
        }

        return mapper.map(value.getExtension(mapper.extensionType()));
    }

    public static <ContainingType extends Message, FromType, ToType> void mapAndSetExtension(
            ProtobufExtensionMapper<ContainingType, FromType, ToType> mapper,
            FromType value,
            BiConsumer<ExtensionLite<ContainingType, ToType>, ToType> extensionSetter) {

        if (mapper == null || value == null) {
            return;
        }

        final ToType mappedExt = mapper.map(value);
        if (mappedExt != null) {
            extensionSetter.accept(mapper.extensionType(), mappedExt);
        }
    }

    public static <T, U> List<U> mapList(List<T> values, Function<T, U> mapper) {
        return CollectionUtils.isEmpty(values)
                ? Collections.emptyList()
                : values.stream().map(mapper).toList();
    }
}
