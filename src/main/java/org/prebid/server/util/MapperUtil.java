package org.prebid.server.util;

import io.vertx.core.MultiMap;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.DataFormat;

public class MapperUtil {

    private MapperUtil() {
    }

    public static String bodyAsString(byte[] body, MultiMap headers) {
        return bodyAsString(body, HttpUtil.resolveDataFormat(headers));
    }

    public static String bodyAsString(byte[] body, DataFormat dataFormat) {
        switch (dataFormat) {
            case JSON:
                return JacksonMapper.asString(body);
            default:
                throw new IllegalArgumentException(String.format("Unsupported data format: %s.", dataFormat));
        }
    }
}
