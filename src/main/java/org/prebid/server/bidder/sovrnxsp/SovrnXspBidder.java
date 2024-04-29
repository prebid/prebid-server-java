package org.prebid.server.bidder.sovrnxsp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
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
import org.prebid.server.proto.openrtb.ext.request.sovrnxsp.ExtImpSovrnXsp;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class SovrnXspBidder implements Bidder<BidRequest> {

    private static final String X_OPENRTB_VERSION = "2.5";

    private static final TypeReference<ExtPrebid<?, ExtImpSovrnXsp>> SOVRNXSP_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public SovrnXspBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final App app = request.getApp();
        if (app == null) {
            return Result.withError(BidderError.badInput("bidrequest.app must be present"));
        }

        String appId = app.getId();
        String appPubId = Optional.ofNullable(app.getPublisher()).map(Publisher::getId).orElse(null);

        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> modifiedImps = new ArrayList<>();

        for (final Imp imp : request.getImp()) {
            try {
                if (imp.getBanner() == null && imp.getVideo() == null && imp.getXNative() == null) {
                    throw new PreBidException("Banner, video or native should be present");
                }

                final ExtImpSovrnXsp impExt = parseExtImp(imp.getExt());
                modifiedImps.add(modifyImp(imp, impExt.getZoneId()));
                appId = StringUtils.isBlank(impExt.getMedId()) ? appId : impExt.getMedId();
                appPubId = StringUtils.isBlank(impExt.getPubId()) ? appPubId : impExt.getPubId();
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (modifiedImps.isEmpty()) {
            errors.add(BidderError.badInput("No matching impression with ad format"));
            return Result.withErrors(errors);
        }

        final BidRequest modifiedRequest = modifyRequest(request, modifiedImps, appId, appPubId);
        return Result.of(Collections.singletonList(makeHttpRequest(modifiedRequest)), errors);
    }

    private ExtImpSovrnXsp parseExtImp(ObjectNode ext) {
        try {
            return mapper.mapper().convertValue(ext, SOVRNXSP_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private Imp modifyImp(Imp imp, String zoneId) {
        return imp.toBuilder().tagid(StringUtils.isBlank(zoneId) ? imp.getTagid() : zoneId).build();
    }

    private BidRequest modifyRequest(BidRequest originalRequest, List<Imp> imps, String appId, String appPubId) {
        final Publisher modifiedPublisher = Optional.ofNullable(originalRequest.getApp().getPublisher())
                .map(Publisher::toBuilder)
                .map(builder -> builder.id(appPubId))
                .map(Publisher.PublisherBuilder::build)
                .orElse(Publisher.builder().id(appPubId).build());

        final App modifiedApp = originalRequest.getApp().toBuilder()
                .id(appId)
                .publisher(modifiedPublisher)
                .build();

        return originalRequest.toBuilder()
                .imp(imps)
                .app(modifiedApp)
                .build();

    }

    private HttpRequest<BidRequest> makeHttpRequest(BidRequest request) {
        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .headers(makeHeaders())
                .impIds(BidderUtil.impIds(request))
                .body(mapper.encodeToBytes(request))
                .payload(request)
                .build();
    }

    private static MultiMap makeHeaders() {
        return HttpUtil.headers().add(HttpUtil.X_OPENRTB_VERSION_HEADER, X_OPENRTB_VERSION);
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final List<BidderError> errors = new ArrayList<>();
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(bidResponse, errors), errors);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse("Bad Server Response"));
        } catch (PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse, List<BidderError> errors) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            throw new PreBidException("Empty SeatBid array");
        }
        return bidResponse.getSeatbid()
                .stream()
                .flatMap(seatBid -> Optional.ofNullable(seatBid.getBid()).orElse(List.of()).stream())
                .map(bid -> makeBid(bid, bidResponse.getCur(), errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private BidderBid makeBid(Bid bid, String currency, List<BidderError> errors) {
        final int creativeType = Optional.ofNullable(bid.getExt())
                .map(ext -> ext.get("creative_type").asInt(-1))
                .orElse(-1);

        return switch (creativeType) {
            case 0 -> makeBidderBid(bid, currency, 1, BidType.banner);
            case 1 -> makeBidderBid(bid, currency, 2, BidType.video);
            case 2 -> makeBidderBid(bid, currency, 4, BidType.xNative);
            default -> {
                errors.add(BidderError.badServerResponse("Unsupported creative type: " + creativeType));
                yield null;
            }
        };
    }

    private static BidderBid makeBidderBid(Bid bid, String currency, Integer mtype, BidType bidType) {
        final Integer modifiedMtype = Optional.ofNullable(bid.getMtype()).orElse(mtype);
        return BidderBid.of(bid.toBuilder().mtype(modifiedMtype).build(), bidType, currency);
    }

}
