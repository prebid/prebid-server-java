package org.prebid.server.bidder.adverxo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
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
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.adverxo.ExtImpAdverxo;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class AdverxoBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpAdverxo>> ADVERXO_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final String DEFAULT_BID_CURRENCY = "USD";
    private static final String ADUNIT_MACROS_ENDPOINT = "{{adUnitId}}";
    private static final String AUTH_MACROS_ENDPOINT = "{{auth}}";
    private static final String PRICE_MACRO = "${AUCTION_PRICE}";

    private final String endpointUrl;
    private final JacksonMapper mapper;
    private final CurrencyConversionService currencyConversionService;

    public AdverxoBidder(String endpointUrl, JacksonMapper mapper,
                         CurrencyConversionService currencyConversionService) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = mapper;
        this.currencyConversionService = currencyConversionService;
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<HttpRequest<BidRequest>> requests = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                final ExtImpAdverxo extImp = parseImpExt(imp);
                final String endpoint = resolveEndpoint(extImp);
                final Imp modifiedImp = modifyImp(imp, request);
                final BidRequest outgoingRequest = createRequest(request, modifiedImp);

                requests.add(createHttpRequest(outgoingRequest, endpoint, imp.getId()));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.of(requests, errors);
    }

    private ExtImpAdverxo parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), ADVERXO_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Error parsing ext.imp.bidder: " + e.getMessage());
        }
    }

    private String resolveEndpoint(ExtImpAdverxo extImpAdverxo) {
        final String adUnitAsString = Optional.of(extImpAdverxo.getAdUnitId())
                .map(Object::toString)
                .orElse(StringUtils.EMPTY);
        final String authAsString = Optional.ofNullable(extImpAdverxo.getAuth())
                .map(Object::toString)
                .orElse(StringUtils.EMPTY);

        return endpointUrl
                .replace(ADUNIT_MACROS_ENDPOINT, adUnitAsString)
                .replace(AUTH_MACROS_ENDPOINT, authAsString);
    }

    private Imp modifyImp(Imp imp, BidRequest request) {
        final BigDecimal bidFloor = imp.getBidfloor();
        final String bidFloorCur = imp.getBidfloorcur();

        if (bidFloor != null && bidFloor.compareTo(BigDecimal.ZERO) > 0
                && StringUtils.isNotBlank(bidFloorCur)
                && !StringUtils.equalsIgnoreCase(bidFloorCur, DEFAULT_BID_CURRENCY)) {

            final BigDecimal convertedPrice = currencyConversionService.convertCurrency(
                    bidFloor,
                    request,
                    bidFloorCur,
                    DEFAULT_BID_CURRENCY
            );

            return imp.toBuilder()
                    .bidfloor(convertedPrice)
                    .bidfloorcur(DEFAULT_BID_CURRENCY)
                    .build();
        }
        return imp;
    }

    private BidRequest createRequest(BidRequest originalRequest, Imp modifiedImp) {
        return originalRequest.toBuilder()
                .imp(Collections.singletonList(modifiedImp))
                .build();
    }

    private HttpRequest<BidRequest> createHttpRequest(BidRequest outgoingRequest,
                                                      String endpoint,
                                                      String impId) {
        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpoint)
                .headers(HttpUtil.headers())
                .body(mapper.encodeToBytes(outgoingRequest))
                .impIds(Collections.singleton(impId))
                .payload(outgoingRequest)
                .build();
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final List<BidderError> bidderErrors = new ArrayList<>();
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidResponse, bidderErrors));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse,
                                        List<BidderError> bidderErrors) {

        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        final String currency = bidResponse.getCur();
        final List<BidderBid> bidderBids = new ArrayList<>();

        for (SeatBid seatBid : bidResponse.getSeatbid()) {
            if (CollectionUtils.isEmpty(seatBid.getBid())) {
                continue;
            }

            for (Bid bid : seatBid.getBid()) {
                final BidType bidType = getBidType(bid);
                final String resolvedAdm = resolveAdmForBidType(bid, bidType);
                final Bid processedBid = processBidMacros(bid, resolvedAdm);

                bidderBids.add(BidderBid.of(processedBid, bidType, currency));
            }
        }

        return bidderBids;
    }

    private BidType getBidType(Bid bid) {
        final Integer markupType = ObjectUtils.defaultIfNull(bid.getMtype(), 0);

        return switch (markupType) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            case 4 -> BidType.xNative;
            default -> throw new PreBidException(
                    "could not define media type for impression: " + bid.getImpid());
        };
    }

    private String resolveAdmForBidType(Bid bid, BidType bidType) {
        if (bidType != BidType.xNative) {
            return bid.getAdm();
        }

        try {
            final JsonNode admNode = mapper.mapper().readTree(bid.getAdm());
            final JsonNode nativeNode = admNode.get("native");
            return nativeNode != null ? nativeNode.toString() : bid.getAdm();
        } catch (JsonProcessingException e) {
            throw new PreBidException("Error parsing native ADM: " + e.getMessage());
        }
    }

    private Bid processBidMacros(Bid bid, String adm) {
        final String price = bid.getPrice() != null ? bid.getPrice().toPlainString() : "0";

        return bid.toBuilder()
                .adm(replaceMacro(adm, price))
                .nurl(replaceMacro(bid.getNurl(), price))
                .build();
    }

    private static String replaceMacro(String input, String value) {
        return input != null ? input.replace(PRICE_MACRO, value) : null;
    }
}
