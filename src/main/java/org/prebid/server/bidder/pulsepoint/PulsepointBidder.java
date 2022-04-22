package org.prebid.server.bidder.pulsepoint;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.pulsepoint.ExtImpPulsepoint;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class PulsepointBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpPulsepoint>> PULSEPOINT_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public PulsepointBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> modifiedImps = new ArrayList<>();
        String publisherId = null;

        for (Imp imp : bidRequest.getImp()) {
            final ExtImpPulsepoint extImpPulsepoint;
            try {
                extImpPulsepoint = parseImpExt(imp);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
                continue;
            }

            modifiedImps.add(modifyImp(imp, extImpPulsepoint));

            final Integer extPublisherId = extImpPulsepoint.getPublisherId();
            if (publisherId == null && extPublisherId != null && extPublisherId > 0) {
                publisherId = extPublisherId.toString();
            }
        }

        if (modifiedImps.isEmpty()) {
            return Result.withErrors(errors);
        }
        publisherId = StringUtils.defaultString(publisherId);

        final BidRequest modifiedRequest = modifyRequest(bidRequest, publisherId, modifiedImps);
        return Result.of(Collections.singletonList(createHttpRequest(modifiedRequest)), errors);
    }

    private ExtImpPulsepoint parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), PULSEPOINT_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private static Imp modifyImp(Imp imp, ExtImpPulsepoint extImpPulsepoint) {
        return imp.toBuilder().tagid(extImpPulsepoint.getTagId().toString()).build();
    }

    private static BidRequest modifyRequest(BidRequest request, String publisherId, List<Imp> imps) {
        return request.toBuilder()
                .site(modifySite(request.getSite(), publisherId))
                .app(modifyApp(request.getApp(), publisherId))
                .imp(imps)
                .build();
    }

    private static Site modifySite(Site site, String publisherId) {
        return site != null
                ? site.toBuilder().publisher(modifyPublisher(site.getPublisher(), publisherId)).build()
                : null;
    }

    private static App modifyApp(App app, String publisherId) {
        return app != null
                ? app.toBuilder().publisher(modifyPublisher(app.getPublisher(), publisherId)).build()
                : null;
    }

    private static Publisher modifyPublisher(Publisher publisher, String publisherId) {
        return publisher != null
                ? publisher.toBuilder().id(publisherId).build()
                : Publisher.builder().id(publisherId).build();
    }

    private HttpRequest<BidRequest> createHttpRequest(BidRequest request) {
        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .headers(HttpUtil.headers())
                .uri(endpointUrl)
                .body(mapper.encodeToBytes(request))
                .payload(request)
                .build();
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(httpCall.getRequest().getPayload(), bidResponse));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> makeBidderBid(bid, bidRequest.getImp(), bidResponse.getCur()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private static BidderBid makeBidderBid(Bid bid, List<Imp> imps, String currency) {
        final BidType bidType = resolveBidType(bid.getImpid(), imps);
        return bidType != null ? BidderBid.of(bid, bidType, currency) : null;
    }

    private static BidType resolveBidType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (Objects.equals(impId, imp.getId())) {
                if (imp.getBanner() != null) {
                    return BidType.banner;
                } else if (imp.getVideo() != null) {
                    return BidType.video;
                } else if (imp.getAudio() != null) {
                    return BidType.audio;
                } else if (imp.getXNative() != null) {
                    return BidType.xNative;
                }
                break;
            }
        }

        return null;
    }
}
