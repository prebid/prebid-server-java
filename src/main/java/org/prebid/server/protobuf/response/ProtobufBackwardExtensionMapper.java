package org.prebid.server.protobuf.response;

import com.google.protobuf.Extension;
import com.google.protobuf.Message;

/**
 * Generic interface for mapping protobuf extensions models to internal extension models.
 */
public interface ProtobufBackwardExtensionMapper<ContainingType extends Message, FromType, ToType> {

    ToType map(FromType fromType);

    Extension<ContainingType, FromType> extensionDescriptor();
}
