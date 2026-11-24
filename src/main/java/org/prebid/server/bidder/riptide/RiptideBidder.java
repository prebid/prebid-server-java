package org.prebid.server.bidder.riptide;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Dooh;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.riptide.ExtImpRiptide;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidVideo;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Riptide: OpenRTB 2.6 to the endpoint in the bidder's config (the parent's, or a white-label
 * alias's own), one request per publisher, {@code tagid} and {@code site/app/dooh.publisher.id}
 * taken from the imp params, an imp floor in another currency converted to USD, and bids typed by
 * {@code mtype} (the imp's media type when a bid has none), a video bid carrying its duration and
 * first category for pod dedup. Mirrors the Go adapter.
 * Riptide answers an uncoded no-bid as 204 and a coded one as 200 {@code {"id","nbr"}}; both are
 * an empty bid list.
 */
public class RiptideBidder implements Bidder<BidRequest> {

    private static final String OPENRTB_VERSION = "2.6";
    private static final String FLOOR_CURRENCY = "USD";
    private static final String DEFAULT_BID_CURRENCY = "USD";
    private static final List<String> PREBID_ONLY_IMP_EXT = List.of("bidder", "prebid");
    private static final Map<Integer, BidType> MTYPE_BID_TYPE = Map.of(
            1, BidType.banner,
            2, BidType.video,
            3, BidType.audio,
            4, BidType.xNative);

    private final String endpointUrl;
    private final CurrencyConversionService currencyConversionService;
    private final JacksonMapper mapper;

    public RiptideBidder(String endpointUrl, CurrencyConversionService currencyConversionService,
                         JacksonMapper mapper) {
        if (StringUtils.contains(endpointUrl, '{')) {
            throw new IllegalArgumentException("Riptide endpoint must be a literal URL without macros: " + endpointUrl);
        }
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.currencyConversionService = Objects.requireNonNull(currencyConversionService);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();
        final Map<String, List<Imp>> impsByPublisher = new TreeMap<>();
        for (Imp imp : bidRequest.getImp()) {
            try {
                final ExtImpRiptide params = parseImpExt(imp);
                final Imp modifiedImp = modifyImp(imp, params, bidRequest);
                impsByPublisher.computeIfAbsent(params.getPublisherId(), key -> new ArrayList<>()).add(modifiedImp);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        final List<HttpRequest<BidRequest>> requests = impsByPublisher.entrySet().stream()
                .map(entry -> makeRequest(bidRequest, entry.getKey(), entry.getValue()))
                .toList();
        return Result.of(requests, errors);
    }

    private ExtImpRiptide parseImpExt(Imp imp) {
        final JsonNode bidder = imp.getExt() != null ? imp.getExt().get("bidder") : null;
        if (bidder == null || !bidder.isObject()) {
            throw new PreBidException("imp %s: bidder params: missing".formatted(imp.getId()));
        }
        final ExtImpRiptide params;
        try {
            params = mapper.mapper().treeToValue(bidder, ExtImpRiptide.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new PreBidException("imp %s: bidder params: %s".formatted(imp.getId(), e.getMessage()));
        }
        if (StringUtils.isAnyEmpty(params.getPublisherId(), params.getPlacementId())) {
            throw new PreBidException("imp %s: publisherId and placementId are required".formatted(imp.getId()));
        }
        return params;
    }

    // Applies the imp's Riptide params (tagid, a floor when the imp has none), converts an imp floor
    // in another currency to USD with the host's rates, and drops the Prebid-only ext members,
    // keeping the rest (gpid, data).
    private Imp modifyImp(Imp imp, ExtImpRiptide params, BidRequest bidRequest) {
        final ObjectNode ext = imp.getExt().deepCopy();
        ext.remove(PREBID_ONLY_IMP_EXT);
        final BigDecimal paramFloor = params.getBidfloor();
        final boolean applyFloor = paramFloor != null && paramFloor.signum() > 0
                && (imp.getBidfloor() == null || imp.getBidfloor().signum() == 0);

        BigDecimal floor = applyFloor ? paramFloor : imp.getBidfloor();
        String floorCur = applyFloor ? FLOOR_CURRENCY : imp.getBidfloorcur();
        if (floor != null && floor.signum() > 0 && StringUtils.isNotBlank(floorCur)
                && !StringUtils.equalsIgnoreCase(floorCur, FLOOR_CURRENCY)) {
            try {
                floor = currencyConversionService.convertCurrency(floor, bidRequest, floorCur, FLOOR_CURRENCY);
            } catch (PreBidException e) {
                throw new PreBidException("imp %s: floor %s: %s".formatted(imp.getId(), floorCur, e.getMessage()));
            }
            floorCur = FLOOR_CURRENCY;
        }

        return imp.toBuilder()
                .tagid(params.getPlacementId())
                .bidfloor(floor)
                .bidfloorcur(floorCur)
                .ext(ext.isEmpty() ? null : ext)
                .build();
    }

    private HttpRequest<BidRequest> makeRequest(BidRequest bidRequest, String publisherId, List<Imp> imps) {
        final BidRequest.BidRequestBuilder outgoing = bidRequest.toBuilder().imp(imps);
        final App app = bidRequest.getApp();
        final Dooh dooh = bidRequest.getDooh();
        if (app != null) {
            outgoing.app(app.toBuilder().publisher(withId(app.getPublisher(), publisherId)).build());
        } else if (dooh != null) {
            outgoing.dooh(dooh.toBuilder().publisher(withId(dooh.getPublisher(), publisherId)).build());
        } else {
            final Site site = bidRequest.getSite() != null ? bidRequest.getSite() : Site.builder().build();
            outgoing.site(site.toBuilder().publisher(withId(site.getPublisher(), publisherId)).build());
        }
        return BidderUtil.defaultRequest(outgoing.build(), headers(), endpointUrl, mapper);
    }

    private static Publisher withId(Publisher existing, String publisherId) {
        return (existing != null ? existing.toBuilder() : Publisher.builder()).id(publisherId).build();
    }

    private static MultiMap headers() {
        return HttpUtil.headers().add(HttpUtil.X_OPENRTB_VERSION_HEADER, OPENRTB_VERSION);
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        final BidResponse bidResponse;
        try {
            bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse("bad server response: " + e.getMessage()));
        }
        if (bidResponse == null || bidResponse.getNbr() != null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Result.empty();
        }

        final Map<String, Imp> imps = bidRequest.getImp().stream()
                .collect(Collectors.toMap(Imp::getId, Function.identity(), (first, second) -> first));
        final String currency = StringUtils.defaultIfBlank(bidResponse.getCur(), DEFAULT_BID_CURRENCY);
        final List<BidderBid> bids = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();
        bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .forEach(bid -> {
                    try {
                        bids.add(toBidderBid(bid, bidType(bid, imps.get(bid.getImpid())), currency));
                    } catch (PreBidException e) {
                        errors.add(BidderError.badServerResponse(e.getMessage()));
                    }
                });
        return Result.of(bids, errors);
    }

    private static BidderBid toBidderBid(Bid bid, BidType type, String currency) {
        return BidderBid.builder()
                .bid(bid)
                .type(type)
                .bidCurrency(currency)
                .videoInfo(type == BidType.video
                        ? ExtBidPrebidVideo.of(Objects.requireNonNullElse(bid.getDur(), 0),
                                CollectionUtils.isNotEmpty(bid.getCat()) ? bid.getCat().getFirst() : null)
                        : null)
                .build();
    }

    // Types a bid by its mtype, or, for a bid without one, by the single media type its imp asks for.
    private static BidType bidType(Bid bid, Imp imp) {
        if (imp == null) {
            throw new PreBidException("bid %s answers unknown imp %s".formatted(bid.getId(), bid.getImpid()));
        }
        final Integer mtype = bid.getMtype();
        if (mtype != null && mtype != 0) {
            final BidType type = MTYPE_BID_TYPE.get(mtype);
            if (type == null) {
                throw new PreBidException("bid %s has unknown mtype %d".formatted(bid.getId(), mtype));
            }
            return type;
        }
        final List<BidType> present = Stream.of(
                        imp.getBanner() != null ? BidType.banner : null,
                        imp.getVideo() != null ? BidType.video : null,
                        imp.getAudio() != null ? BidType.audio : null,
                        imp.getXNative() != null ? BidType.xNative : null)
                .filter(Objects::nonNull)
                .toList();
        if (present.size() != 1) {
            throw new PreBidException("bid %s has no mtype and imp %s is not single-format"
                    .formatted(bid.getId(), bid.getImpid()));
        }
        return present.getFirst();
    }
}
