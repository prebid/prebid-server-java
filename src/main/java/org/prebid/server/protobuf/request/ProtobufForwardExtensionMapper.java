package org.prebid.server.protobuf.request;

import com.google.protobuf.Extension;
import com.google.protobuf.Message;

/**
 * Generic interface for mapping internal extension models to protobuf extensions.
 */
public interface ProtobufForwardExtensionMapper<ContainingType extends Message, FromType, ToType> {

    ToType map(FromType fromType);

    Extension<ContainingType, ToType> extensionDescriptor();
}
