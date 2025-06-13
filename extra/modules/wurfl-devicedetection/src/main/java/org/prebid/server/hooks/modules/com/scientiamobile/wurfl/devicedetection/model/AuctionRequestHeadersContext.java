package org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.model;

import lombok.Value;
import org.prebid.server.model.CaseInsensitiveMultiMap;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Value
public class AuctionRequestHeadersContext {

    Map<String, String> headers;

    public static AuctionRequestHeadersContext from(CaseInsensitiveMultiMap headers) {
        if (headers == null) {
            return new AuctionRequestHeadersContext(Collections.emptyMap());
        }

        final Map<String, String> headersMap = new HashMap<>();
        for (String headerName : headers.names()) {
            headersMap.put(headerName, headers.getAll(headerName).getFirst());
        }
        return new AuctionRequestHeadersContext(Collections.unmodifiableMap(headersMap));
    }
}
