package org.prebid.server.bidder.madsense;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.madsense.ExtImpMadsense;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidVideo;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class MadsenseBidder implements Bidder<BidRequest> {

    private static final String X_OPENRTB_VERSION_HEADER_VALUE = "2.6";
    private static final TypeReference<ExtPrebid<?, ExtImpMadsense>> TYPE_REFERENCE = new TypeReference<>() {
    };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public MadsenseBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> videoImps = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            if (imp.getBanner() != null) {
                try {
                    httpRequests.add(makeHttpRequest(request, Collections.singletonList(imp)));
                } catch (PreBidException e) {
                    errors.add(BidderError.badInput(e.getMessage()));
                }
            } else if (imp.getVideo() != null) {
                videoImps.add(imp);
            }
        }

        if (CollectionUtils.isNotEmpty(videoImps)) {
            try {
                httpRequests.add(makeHttpRequest(request, videoImps));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.of(httpRequests, errors);
    }

    private ExtImpMadsense parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Error parsing imp.ext parameters");
        }
    }

    private HttpRequest<BidRequest> makeHttpRequest(BidRequest request, List<Imp> imps) {
        final Imp firstImp = request.getImp().getFirst();
        final ExtImpMadsense extImp = parseImpExt(firstImp);
        final String companyId = Objects.equals(request.getTest(), 1) ? "test" : extImp.getCompanyId();
        return BidderUtil.defaultRequest(
                request.toBuilder().imp(imps).build(),
                makeHeaders(request),
                makeEndpoint(companyId),
                mapper);
    }

    private static MultiMap makeHeaders(BidRequest request) {
        final MultiMap headers = HttpUtil.headers()
                .set(HttpUtil.X_OPENRTB_VERSION_HEADER, X_OPENRTB_VERSION_HEADER_VALUE);

        final Device device = request.getDevice();
        if (device != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER, device.getUa());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIp());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIpv6());
        }

        final Site site = request.getSite();
        if (site != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.ORIGIN_HEADER, site.getDomain());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.REFERER_HEADER, site.getRef());
        }

        return headers;
    }

    private String makeEndpoint(String companyId) {
        return endpointUrl + "?company_id=" + HttpUtil.encodeUrl(companyId);
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(bidResponse, errors), errors);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse, List<BidderError> errors) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidResponse, errors);
    }

    private static List<BidderBid> bidsFromResponse(BidResponse bidResponse, List<BidderError> errors) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> makeBidderBid(bid, bidResponse.getCur(), errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private static BidderBid makeBidderBid(Bid bid, String currency, List<BidderError> errors) {
        try {
            final BidType bidType = getBidType(bid);
            return BidderBid.builder()
                    .bid(bid)
                    .bidCurrency(currency)
                    .videoInfo(bidType == BidType.video
                            ? ExtBidPrebidVideo.of(resolveDuration(bid), resolveCategory(bid))
                            : null)
                    .type(bidType)
                    .build();
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }
    }

    private static BidType getBidType(Bid bid) {
        return switch (bid.getMtype()) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            case null, default -> throw new PreBidException(
                    "Unsupported bid mediaType: %s for impression: %s".formatted(bid.getMtype(), bid.getImpid()));
        };
    }

    private static String resolveCategory(Bid bid) {
        final List<String> categories = bid.getCat();
        return CollectionUtils.isEmpty(categories) ? null : categories.getFirst();
    }

    private static Integer resolveDuration(Bid bid) {
        final Integer duration = bid.getDur();
        return duration != null && duration > 0 ? duration : null;
    }
}
