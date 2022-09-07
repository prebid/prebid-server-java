package org.prebid.server.protobuf;

import com.google.protobuf.Message;

@FunctionalInterface
public interface ProtobufMapper<FromType, ToType extends Message> {

    ToType map(FromType value);
}
