package org.prebid.server.bidder.bidwave;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Price;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.bidwave.ExtImpBidwave;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class BidwaveBidder implements Bidder<BidRequest> {

    private static final String BIDWAVE_EXT = "bidwave";
    private static final String PUBLISHER_ID_EXT = "pid";
    private static final String BIDDER_CURRENCY = "USD";
    private static final List<String> DEFAULT_CURRENCY = List.of(BIDDER_CURRENCY);
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
            Pattern.CASE_INSENSITIVE);
    private static final TypeReference<ExtPrebid<?, ExtImpBidwave>> BIDWAVE_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final CurrencyConversionService currencyConversionService;
    private final JacksonMapper mapper;

    public BidwaveBidder(String endpointUrl,
                         CurrencyConversionService currencyConversionService,
                         JacksonMapper mapper) {

        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.currencyConversionService = Objects.requireNonNull(currencyConversionService);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();
        final Map<String, List<Imp>> impsByPublisherId = new LinkedHashMap<>();

        for (Imp imp : bidRequest.getImp()) {
            try {
                final String publisherId = parsePublisherId(imp);
                final Imp modifiedImp = modifyImpCurrency(imp, bidRequest);
                impsByPublisherId.computeIfAbsent(publisherId, ignored -> new ArrayList<>()).add(modifiedImp);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        final List<HttpRequest<BidRequest>> requests = impsByPublisherId.entrySet().stream()
                .map(entry -> BidderUtil.defaultRequest(
                        createRequest(bidRequest, entry.getValue(), entry.getKey()), endpointUrl, mapper))
                .toList();

        return Result.of(requests, errors);
    }

    private String parsePublisherId(Imp imp) {
        final ExtImpBidwave extImpBidwave;
        try {
            extImpBidwave = mapper.mapper().convertValue(imp.getExt(), BIDWAVE_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Invalid imp.ext for impression %s. Error Information: %s"
                    .formatted(imp.getId(), e.getMessage()));
        }

        final String publisherId = extImpBidwave != null ? extImpBidwave.getPublisherId() : null;
        if (publisherId == null || !UUID_PATTERN.matcher(publisherId).matches()) {
            throw new PreBidException("Invalid publisherId for impression %s".formatted(imp.getId()));
        }

        return publisherId;
    }

    private Imp modifyImpCurrency(Imp imp, BidRequest bidRequest) {
        final Price initialBidFloorPrice = Price.of(imp.getBidfloorcur(), imp.getBidfloor());
        if (!BidderUtil.shouldConvertBidFloor(initialBidFloorPrice, BIDDER_CURRENCY)) {
            return imp;
        }

        try {
            final BigDecimal convertedBidFloor = currencyConversionService.convertCurrency(
                    initialBidFloorPrice.getValue(),
                    bidRequest,
                    initialBidFloorPrice.getCurrency(),
                    BIDDER_CURRENCY);
            return imp.toBuilder()
                    .bidfloor(convertedBidFloor)
                    .bidfloorcur(BIDDER_CURRENCY)
                    .build();
        } catch (PreBidException e) {
            throw new PreBidException(
                    "expected currency USD for bid floor; unable to convert from %s for imp `%s`"
                            .formatted(initialBidFloorPrice.getCurrency(), imp.getId()));
        }
    }

    private BidRequest createRequest(BidRequest bidRequest, List<Imp> imps, String publisherId) {
        return bidRequest.toBuilder()
                .imp(imps)
                .cur(DEFAULT_CURRENCY)
                .ext(createRequestExt(bidRequest.getExt(), publisherId))
                .build();
    }

    private ExtRequest createRequestExt(ExtRequest requestExt, String publisherId) {
        final ExtRequest updatedExt = ExtRequest.of(requestExt != null ? requestExt.getPrebid() : null);
        if (requestExt != null) {
            updatedExt.addProperties(requestExt.getProperties());
        }

        final ObjectNode bidwaveExt = mapper.mapper().createObjectNode().put(PUBLISHER_ID_EXT, publisherId);
        updatedExt.addProperty(BIDWAVE_EXT, bidwaveExt);
        return updatedExt;
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        final BidResponse bidResponse;
        try {
            bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }

        final List<BidderError> errors = new ArrayList<>();
        final List<BidderBid> bids = extractBids(httpCall.getRequest().getPayload(), bidResponse, errors);
        return Result.of(bids, errors);
    }

    private static List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse,
                                               List<BidderError> errors) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        final Map<String, Imp> impsById = bidRequest.getImp().stream()
                .collect(Collectors.toMap(Imp::getId, Function.identity(), (first, second) -> first));
        final String currency = bidResponse.getCur();

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> makeBidderBid(bid, impsById, currency, errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private static BidderBid makeBidderBid(Bid bid, Map<String, Imp> impsById, String currency,
                                           List<BidderError> errors) {
        final BidType bidType;
        try {
            bidType = resolveBidType(bid, impsById);
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }

        return BidderBid.of(bid, bidType, currency);
    }

    private static BidType resolveBidType(Bid bid, Map<String, Imp> impsById) {
        final Integer markupType = bid.getMtype();
        if (markupType == null) {
            return resolveBidTypeFromImp(bid.getImpid(), impsById);
        }

        return switch (markupType) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            default -> throw new PreBidException("Unsupported mtype %d for imp %s"
                    .formatted(markupType, bid.getImpid()));
        };
    }

    private static BidType resolveBidTypeFromImp(String impId, Map<String, Imp> impsById) {
        final Imp imp = impsById.get(impId);
        if (imp == null) {
            throw new PreBidException("Failed to find impression for ID: \"%s\"".formatted(impId));
        }
        if (isMultiFormat(imp)) {
            throw new PreBidException(
                    "Bid must have non-null mtype for multi format impression with ID: \"%s\"".formatted(impId));
        }
        if (imp.getBanner() != null) {
            return BidType.banner;
        }
        if (imp.getVideo() != null) {
            return BidType.video;
        }
        throw new PreBidException("Could not determine bid type for impression with ID: \"%s\"".formatted(impId));
    }

    private static boolean isMultiFormat(Imp imp) {
        int formatCount = 0;
        formatCount += imp.getBanner() == null ? 0 : 1;
        formatCount += imp.getVideo() == null ? 0 : 1;
        formatCount += imp.getAudio() == null ? 0 : 1;
        formatCount += imp.getXNative() == null ? 0 : 1;
        return formatCount > 1;
    }
}
