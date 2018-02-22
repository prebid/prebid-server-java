package org.prebid.server.bidder;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.model.openrtb.ext.response.BidType;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Basic {@link Bidder} implementation containing common logic functionality and helper methods.
 */
public abstract class OpenRtbBidder implements Bidder {

    private static final Logger logger = LoggerFactory.getLogger(OpenRtbBidder.class);

    public static final String APPLICATION_JSON =
            HttpHeaderValues.APPLICATION_JSON.toString() + ";" + HttpHeaderValues.CHARSET.toString() + "=" + "utf-8";

    protected static Map<String, BidType> impidToBidType(BidRequest bidRequest) {
        return bidRequest.getImp().stream()
                .collect(Collectors.toMap(Imp::getId, imp -> imp.getVideo() != null ? BidType.video : BidType.banner));
    }

    protected static BidResponse parseResponse(HttpResponse httpResponse) {
        final int statusCode = httpResponse.getStatusCode();

        if (statusCode == 204) {
            return null;
        }

        if (statusCode != 200) {
            throw new PreBidException(
                    String.format("Unexpected status code: %d. Run with request.test = 1 for more info", statusCode));
        }

        try {
            return Json.mapper.readValue(httpResponse.getBody(), BidResponse.class);
        } catch (IOException e) {
            logger.warn("Error occurred parsing bid response", e);
            throw new PreBidException(e.getMessage());
        }
    }

    protected static MultiMap headers() {
        return MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
                .add(HttpHeaders.ACCEPT, HttpHeaderValues.APPLICATION_JSON);
    }

    protected static List<BidderError> errors(List<String> errors) {
        return errors.stream().map(BidderError::create).collect(Collectors.toList());
    }

    protected static BidType bidType(Bid bid, Map<String, BidType> impidToBidType) {
        return impidToBidType.getOrDefault(bid.getImpid(), BidType.banner);
    }

    protected static String validateUrl(String url) {
        Objects.requireNonNull(url);
        try {
            return new URL(url).toString();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(String.format("URL supplied is not valid: %s", url), e);
        }
    }
}
