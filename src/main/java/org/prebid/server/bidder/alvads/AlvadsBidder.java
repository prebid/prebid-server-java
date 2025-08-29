package org.prebid.server.bidder.alvads;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.mgid.model.ExtBidMgid;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class AlvadsBidder implements Bidder<AlvadsRequestORTB> {

    private static final Logger logger = LoggerFactory.getLogger(AlvadsBidder.class);
    private final String endpointUrl;
    private final JacksonMapper mapper;

    public AlvadsBidder(String endpointUrl, JacksonMapper mapper) {
        logger.debug("AlvaAdsBidder");
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public final Result<List<HttpRequest<AlvadsRequestORTB>>> makeHttpRequests(BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();
        final List<HttpRequest<AlvadsRequestORTB>> httpRequests = new ArrayList<>();

        for (Imp imp : bidRequest.getImp()) {
            try {
                final AlvadsImpExt impExt = parseImpExt(imp);
                final HttpRequest<AlvadsRequestORTB> request = makeHttpRequest(bidRequest, imp, impExt);

                httpRequests.add(request);
            } catch (PreBidException e) {
                return Result.withError(BidderError.badInput(e.getMessage()));
            }
        }

        if (httpRequests.isEmpty()) {
            return Result.withError(BidderError.badInput("found no valid impressions"));
        }

        return Result.of(httpRequests, errors);
    }

    private HttpRequest<AlvadsRequestORTB> makeHttpRequest(BidRequest request, Imp imp, AlvadsImpExt impExt) {
        final AlvaRequestORTBBuilder builder = new AlvaRequestORTBBuilder(request, imp, impExt, mapper);
        final AlvadsRequestORTB alvadsRequest = imp.getVideo() != null ? builder.buildVideo() : builder.build();
        return createRequest(alvadsRequest, impExt);
    }

    private HttpRequest<AlvadsRequestORTB> createRequest(AlvadsRequestORTB request, AlvadsImpExt impExt) {
        return HttpRequest.<AlvadsRequestORTB>builder()
                .method(HttpMethod.POST)
                .uri(impExt.getEndPointUrl() != null ? impExt.getEndPointUrl() : endpointUrl)
                .headers(HttpUtil.headers())
                .payload(request)
                .body(mapper.encodeToBytes(request))
                .build();
    }

    private AlvadsImpExt parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt().get("bidder"), AlvadsImpExt.class);
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    @Override
    public final Result<List<BidderBid>> makeBids(BidderCall<AlvadsRequestORTB> httpCall,
                                                  BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidResponse, httpCall.getRequest().getPayload()));
        } catch (Exception e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse, AlvadsRequestORTB request) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidResponse, request);
    }

    private List<BidderBid> bidsFromResponse(BidResponse bidResponse, AlvadsRequestORTB request) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, getBidType(bid, request), bidResponse.getCur()))
                .toList();
    }

    private BidType getBidType(Bid bid, AlvadsRequestORTB request) {
        final ExtBidMgid bidExt = getBidExt(bid);
        if (bidExt == null) {
            return BidType.banner;
        }

        final BidType crtype = bidExt.getCrtype();
        return request.getImp().get(0).getVideo() != null ? BidType.video : crtype == null ? BidType.banner : crtype;
    }

    private ExtBidMgid getBidExt(Bid bid) {
        try {
            return mapper.mapper().convertValue(bid.getExt(), ExtBidMgid.class);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
