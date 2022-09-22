package org.prebid.server.protobuf.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.Message;

public interface JsonProtobufExtensionMapper<ContainingType extends Message, ExtType>
        extends ProtobufForwardExtensionMapper<ContainingType, ObjectNode, ExtType> {
}
