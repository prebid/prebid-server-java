package org.prebid.server.bidder.melozen;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
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
import org.prebid.server.proto.openrtb.ext.request.melozen.MeloZenImpExt;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class MeloZenBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, MeloZenImpExt>> TYPE_REFERENCE = new TypeReference<>() {
    };

    private static final String PUBLISHER_ID_MACRO = "{{PublisherID}}";
    private static final String BIDDER_CURRENCY = "USD";
    private static final String EXT_PREBID = "prebid";

    private final CurrencyConversionService currencyConversionService;
    private final String endpointUrl;
    private final JacksonMapper mapper;

    public MeloZenBidder(CurrencyConversionService currencyConversionService,
                         String endpoint,
                         JacksonMapper mapper) {

        this.currencyConversionService = Objects.requireNonNull(currencyConversionService);
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpoint));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<HttpRequest<BidRequest>> requests = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                final MeloZenImpExt impExt = parseImpExt(imp);
                final String url = resolveEndpoint(impExt);
                final Imp modifiedImp = modifyImp(request, imp);
                splitImpByMediaType(modifiedImp).forEach(splitImp ->
                        requests.add(BidderUtil.defaultRequest(modifyRequest(request, splitImp), url, mapper)));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.of(requests, errors);
    }

    private MeloZenImpExt parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private Imp modifyImp(BidRequest bidRequest, Imp imp) {
        final Price resolvedFloor = resolveBidFloor(bidRequest, imp);
        return imp.toBuilder()
                .bidfloor(resolvedFloor.getValue())
                .bidfloorcur(resolvedFloor.getCurrency())
                .build();
    }

    private Price resolveBidFloor(BidRequest bidRequest, Imp imp) {
        final BigDecimal bidFloor = imp.getBidfloor();
        final String bidFloorCurrency = imp.getBidfloorcur();

        if (BidderUtil.isValidPrice(bidFloor)
                && StringUtils.isNotBlank(bidFloorCurrency)
                && !StringUtils.equalsIgnoreCase(bidFloorCurrency, BIDDER_CURRENCY)) {

            final BigDecimal convertedFloor = currencyConversionService.convertCurrency(
                    bidFloor,
                    bidRequest,
                    bidFloorCurrency,
                    BIDDER_CURRENCY);

            return Price.of(BIDDER_CURRENCY, convertedFloor);
        }

        return Price.of(bidFloorCurrency, bidFloor);
    }

    private String resolveEndpoint(MeloZenImpExt impExt) {
        return endpointUrl
                .replace(PUBLISHER_ID_MACRO, HttpUtil.encodeUrl(StringUtils.defaultString(impExt.getPubId())));
    }

    private List<Imp> splitImpByMediaType(Imp imp) {
        final Banner banner = imp.getBanner();
        final Video video = imp.getVideo();
        final Native xNative = imp.getXNative();

        if (ObjectUtils.allNull(banner, video, xNative)) {
            throw new PreBidException("Invalid MediaType. MeloZen only supports Banner, Video and Native.");
        }

        final List<Imp> imps = new ArrayList<>();

        if (banner != null) {
            imps.add(imp.toBuilder().video(null).xNative(null).build());
        }

        if (video != null) {
            imps.add(imp.toBuilder().banner(null).xNative(null).build());
        }

        if (xNative != null) {
            imps.add(imp.toBuilder().banner(null).video(null).build());
        }

        return imps;
    }

    private BidRequest modifyRequest(BidRequest request, Imp imp) {
        return request.toBuilder()
                .imp(Collections.singletonList(imp))
                .build();
    }

    @Override
    public final Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final List<BidderError> errors = new ArrayList<>();
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(bidResponse, errors), errors);
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse, List<BidderError> errors) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> toBidderBid(bid, bidResponse.getCur(), errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private BidderBid toBidderBid(Bid bid, String currency, List<BidderError> errors) {
        try {
            return BidderBid.of(bid, getBidType(bid), currency);
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }
    }

    private BidType getBidType(Bid bid) {
        return Optional.ofNullable(bid.getExt())
                .map(ext -> ext.get(EXT_PREBID))
                .map(ObjectNode.class::cast)
                .map(this::parseExtBidPrebid)
                .map(ExtBidPrebid::getType)
                .orElseThrow(() -> new PreBidException(
                        "Failed to parse bid mediatype for impression \"%s\"".formatted(bid.getImpid())));
    }

    private ExtBidPrebid parseExtBidPrebid(ObjectNode prebid) {
        try {
            return mapper.mapper().treeToValue(prebid, ExtBidPrebid.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
