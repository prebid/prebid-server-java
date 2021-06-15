package org.prebid.server.bidder.bidmachine;

import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * InteractiveOffers {@link Bidder} implementation.
 */
public class BidmachineBidder implements Bidder<BidRequest> {
    private final String endpointUrl;
    private final JacksonMapper mapper;

    public BidmachineBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();

        MultiMap headers = HttpUtil.headers();
        headers.add("X-Openrtb-Version", "2.5");

        final List<Imp> imps = request.getImp();

        for (Imp imp : imps) {

            if (imp.getBanner() != null) {
                final Banner banner = imp.getBanner();

                if (banner.getW() == null && banner.getW() == null) {
                    final List<Format> format = banner.getFormat();
                    if (format == null) {
                        errors.add(BidderError.badInput("Impression with id: " + imp.getId()
                                + " has following error: Banner width and height is not provided and"
                                + " banner format is missing. At least one is required"));
                        continue;
                    }
                    if (format.isEmpty()) {
                        errors.add(BidderError.badInput("Impression with id: " + imp.getId() + " has following error:"
                                + " Banner width and height is not provided and banner format array is empty. "
                                + "At least one is required"));

                    }
                }

            }


        }

        return Result.withValue(HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .headers(headers)
                .payload(request)
                .body(mapper.encode(request))
                .build());
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(bidResponse), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }
}
