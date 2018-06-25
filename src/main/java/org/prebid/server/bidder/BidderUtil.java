package org.prebid.server.bidder;

import com.iab.openrtb.response.BidResponse;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Util class to help {@link Bidder}s implementation process responses and requests
 */
public class BidderUtil {

    private BidderUtil() {
    }

    private static final Logger logger = LoggerFactory.getLogger(BidderUtil.class);

    public static final String APPLICATION_JSON =
            HttpHeaderValues.APPLICATION_JSON.toString() + ";" + HttpHeaderValues.CHARSET.toString() + "=" + "utf-8";

    /**
     * Parses {@link HttpResponse} to {@link BidResponse} class and handles http status codes different to Ok 200
     */
    public static BidResponse parseResponse(HttpResponse httpResponse) {
        final int statusCode = httpResponse.getStatusCode();

        if (statusCode == HttpResponseStatus.NO_CONTENT.code()) {
            return null;
        }

        if (statusCode == HttpResponseStatus.BAD_REQUEST.code()) {
            throw new BadInputRequestException(String.format(
                    "Unexpected status code: %d. Run with request.test = 1 for more info", statusCode));
        }

        if (statusCode != HttpResponseStatus.OK.code()) {
            throw new BadServerResponseException(
                    String.format("Unexpected status code: %d. Run with request.test = 1 for more info", statusCode));
        }

        try {
            return Json.mapper.readValue(httpResponse.getBody(), BidResponse.class);
        } catch (IOException e) {
            logger.warn("Error occurred parsing bid response", e);
            throw new PreBidException(e.getMessage());
        }
    }

    /**
     * Creates shared headers for all bidders
     */
    public static MultiMap headers() {
        return MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
                .add(HttpHeaders.ACCEPT, HttpHeaderValues.APPLICATION_JSON);
    }

    /**
     * Creates {@link List<BidderError>} from list of errors with defined error type
     */
    public static List<BidderError> createBidderErrors(BidderError.Type errorsType, List<String> errors) {
        return errors.stream().map(error -> BidderError.create(error, errorsType)).collect(Collectors.toList());
    }

    /**
     * Creates {@link Result<List<BidderBid>>} with empty {@link List<BidderBid>} and error message with defined error
     * type
     */
    public static Result<List<BidderBid>> createEmptyResultWithError(BidderError.Type type,
                                                                     String error) {
        return Result.of(Collections.emptyList(), createBidderErrors(type, Collections.singletonList(error)));
    }

    public static class BadServerResponseException extends RuntimeException {

        public BadServerResponseException(String message) {
            super(message);
        }
    }

    public static class BadInputRequestException extends RuntimeException {
        public BadInputRequestException(String message) {
            super(message);
        }
    }
}
