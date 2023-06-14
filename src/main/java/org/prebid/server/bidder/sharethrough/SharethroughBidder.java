package org.prebid.server.bidder.sharethrough;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
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
import org.prebid.server.proto.openrtb.ext.request.ExtSource;
import org.prebid.server.proto.openrtb.ext.request.sharethrough.ExtImpSharethrough;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ObjectUtil;
import org.prebid.server.version.PrebidVersionProvider;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class SharethroughBidder implements Bidder<BidRequest> {

    private static final String ADAPTER_VERSION = "10.0";
    private static final String BID_CURRENCY = "USD";
    private static final JsonPointer BID_TYPE_POINTER = JsonPointer.valueOf("/prebid/type");
    private static final TypeReference<ExtPrebid<?, ExtImpSharethrough>> SHARETHROUGH_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final CurrencyConversionService currencyConversionService;
    private final PrebidVersionProvider prebidVersionProvider;
    private final JacksonMapper mapper;

    public SharethroughBidder(String endpointUrl,
                              CurrencyConversionService currencyConversionService,
                              PrebidVersionProvider prebidVersionProvider,
                              JacksonMapper mapper) {

        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.currencyConversionService = Objects.requireNonNull(currencyConversionService);
        this.prebidVersionProvider = Objects.requireNonNull(prebidVersionProvider);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            final ExtImpSharethrough extImpSharethrough;
            final Price bidFloorPrice;
            try {
                extImpSharethrough = parseImpExt(imp);
                bidFloorPrice = resolveBidFloor(imp, request);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
                continue;
            }

            final Imp modifiedImp = modifyImp(imp, extImpSharethrough.getPkey(), bidFloorPrice);
            final BidRequest modifiedBidRequest = modifyRequest(request, modifiedImp, extImpSharethrough);

            httpRequests.add(makeHttpRequest(modifiedBidRequest));
        }

        return Result.of(httpRequests, errors);
    }

    private ExtImpSharethrough parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), SHARETHROUGH_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private Price resolveBidFloor(Imp imp, BidRequest bidRequest) {
        final Price initialBidFloorPrice = Price.of(imp.getBidfloorcur(), imp.getBidfloor());
        return BidderUtil.isValidPrice(initialBidFloorPrice)
                ? convertBidFloor(initialBidFloorPrice, bidRequest)
                : initialBidFloorPrice;
    }

    private Price convertBidFloor(Price bidFloorPrice, BidRequest bidRequest) {
        final BigDecimal convertedPrice = currencyConversionService.convertCurrency(
                bidFloorPrice.getValue(),
                bidRequest,
                bidFloorPrice.getCurrency(),
                BID_CURRENCY);

        return Price.of(BID_CURRENCY, convertedPrice);
    }

    private static Imp modifyImp(Imp imp, String tagId, Price bidFloorPrice) {
        return imp.toBuilder()
                .tagid(tagId)
                .bidfloor(bidFloorPrice.getValue())
                .bidfloorcur(bidFloorPrice.getCurrency())
                .build();
    }

    private BidRequest modifyRequest(BidRequest bidRequest, Imp imp, ExtImpSharethrough extImpSharethrough) {
        return bidRequest.toBuilder()
                .imp(Collections.singletonList(imp))
                .bcat(union(bidRequest.getBcat(), extImpSharethrough.getBcat()))
                .badv(union(bidRequest.getBadv(), extImpSharethrough.getBadv()))
                .source(modifySource(bidRequest.getSource()))
                .build();
    }

    private static List<String> union(List<String> first, List<String> second) {
        if (CollectionUtils.isEmpty(first)) {
            return second;
        }

        if (CollectionUtils.isEmpty(second)) {
            return first;
        }

        return ListUtils.union(first, second);
    }

    private Source modifySource(Source source) {
        return source != null
                ? source.toBuilder()
                .ext(ObjectUtil.getIfNotNullOrDefault(source.getExt(), this::modifyExtSource, this::createExtSource))
                .build()
                : Source.builder().ext(createExtSource()).build();
    }

    private ExtSource modifyExtSource(ExtSource ext) {
        final ExtSource copy = ExtSource.of(ext.getSchain());
        copy.addProperties(ext.getProperties());

        copy.addProperty("str", TextNode.valueOf(ADAPTER_VERSION));
        copy.addProperty("version", TextNode.valueOf(prebidVersionProvider.getNameVersionRecord()));

        return copy;
    }

    private ExtSource createExtSource() {
        final ExtSource extSource = ExtSource.of(null);
        extSource.addProperty("str", TextNode.valueOf(ADAPTER_VERSION));
        extSource.addProperty("version", TextNode.valueOf(prebidVersionProvider.getNameVersionRecord()));
        return extSource;
    }

    private HttpRequest<BidRequest> makeHttpRequest(BidRequest request) {
        return BidderUtil.defaultRequest(request, endpointUrl, mapper);
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        final BidResponse bidResponse;

        try {
            bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }

        final List<BidderError> errors = new ArrayList<>();
        return Result.of(extractBids(bidResponse, errors), errors);
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
                .map(bid -> constructBidderBid(bid, errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private BidderBid constructBidderBid(Bid bid, List<BidderError> errors) {
        final JsonNode extNode = bid.getExt();
        final JsonNode bidTypeNode = extNode != null ? extNode.at(BID_TYPE_POINTER) : null;

        if (bidTypeNode == null || !bidTypeNode.isTextual()) {
            errors.add(BidderError.badServerResponse(
                    "Failed to parse bid media type for impression " + bid.getImpid()));
            return null;
        }

        final BidType bidType = parseBidType(bidTypeNode, errors);
        return bidType != null
                ? BidderBid.of(bid, bidType, BID_CURRENCY)
                : null;
    }

    private BidType parseBidType(JsonNode bidTypeNode, List<BidderError> errors) {
        try {
            return mapper.mapper().convertValue(bidTypeNode, BidType.class);
        } catch (IllegalArgumentException ignore) {
            errors.add(BidderError.badServerResponse("invalid BidType: " + bidTypeNode.asText()));
            return null;
        }
    }
}
