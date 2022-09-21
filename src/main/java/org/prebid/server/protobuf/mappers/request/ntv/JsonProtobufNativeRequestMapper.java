package org.prebid.server.protobuf.mappers.request.ntv;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iab.openrtb.request.Request;
import com.iabtechlab.openrtb.v2.OpenRtb;
import org.prebid.server.protobuf.ProtobufMapper;

import java.util.Objects;

public class JsonProtobufNativeRequestMapper implements ProtobufMapper<String, OpenRtb.NativeRequest> {

    private final ObjectMapper objectMapper;
    private final ProtobufMapper<Request, OpenRtb.NativeRequest> nativeRequestMapper;

    public JsonProtobufNativeRequestMapper(ObjectMapper objectMapper,
                                           ProtobufMapper<Request, OpenRtb.NativeRequest> nativeRequestMapper) {

        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.nativeRequestMapper = Objects.requireNonNull(nativeRequestMapper);
    }

    @Override
    public OpenRtb.NativeRequest map(String value) {
        try {
            final Request request = objectMapper.readValue(value, Request.class);
            return nativeRequestMapper.map(request);
        } catch (Throwable e) {
            return null;
        }
    }
}
