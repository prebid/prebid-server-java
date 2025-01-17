package org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.model;

import lombok.Getter;
import org.prebid.server.model.CaseInsensitiveMultiMap;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Getter
public class AuctionRequestHeadersContext {

    Map<String, String> headers;

    private AuctionRequestHeadersContext(Map<String, String> headers) {
        this.headers = headers;
    }

    public static AuctionRequestHeadersContext from(final CaseInsensitiveMultiMap headers) {
        final Map<String, String> headersMap = new HashMap<>();
        if (Objects.isNull(headers)) {
            return new AuctionRequestHeadersContext(headersMap);
        }

        for (String headerName : headers.names()) {
            headersMap.put(headerName, headers.getAll(headerName).getFirst());
        }
        return new AuctionRequestHeadersContext(headersMap);
    }

}
