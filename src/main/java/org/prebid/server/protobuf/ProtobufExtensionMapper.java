package org.prebid.server.protobuf;

import com.google.protobuf.Extension;
import com.google.protobuf.Message;

public interface ProtobufExtensionMapper<ContainingType extends Message, FromType, ToType> {

    ToType map(FromType fromType);

    Extension<ContainingType, ToType> extensionType();
}
