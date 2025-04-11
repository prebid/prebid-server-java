package org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.model;

import lombok.Getter;
import org.prebid.server.model.CaseInsensitiveMultiMap;

import java.util.HashMap;
import java.util.Map;

@Getter
public class AuctionRequestHeadersContext {

    Map<String, String> headers;

    private AuctionRequestHeadersContext(Map<String, String> headers) {
        this.headers = headers;
    }

    public static AuctionRequestHeadersContext from(CaseInsensitiveMultiMap headers) {
        final Map<String, String> headersMap = new HashMap<>();
        if (headers == null) {
            return new AuctionRequestHeadersContext(headersMap);
        }

        for (String headerName : headers.names()) {
            headersMap.put(headerName, headers.getAll(headerName).getFirst());
        }
        return new AuctionRequestHeadersContext(headersMap);
    }

}
