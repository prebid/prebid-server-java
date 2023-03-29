package org.prebid.server.protobuf.response;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.Message;

public interface ProtobufJsonExtensionMapper<ContainingType extends Message, ExtType>
        extends ProtobufBackwardExtensionMapper<ContainingType, ExtType, ObjectNode> {
}
