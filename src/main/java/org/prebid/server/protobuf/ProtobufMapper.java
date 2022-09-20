package org.prebid.server.protobuf;

import com.google.protobuf.Message;

/**
 * Generic interface for mapping internal models to protobuf models.
 * MapStruct is not used for implementations, because models have extensions,
 * for which different extension mappers are provided via constructor injection.
 * Sadly, for now MapStruct doesn't support this feature.
 * See <a href="https://github.com/mapstruct/mapstruct/issues/2257">corresponding issue</a>
 */
@FunctionalInterface
public interface ProtobufMapper<FromType, ToType extends Message> {

    ToType map(FromType value);
}
