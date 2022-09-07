package org.prebid.server.protobuf;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.Message;

public interface JsonProtobufExtensionMapper<ContainingType extends Message, ExtType>
        extends ProtobufExtensionMapper<ContainingType, ObjectNode, ExtType> {
}
