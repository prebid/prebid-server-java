package org.prebid.server.bidder.readpeak;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
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
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.readpeak.ExtImpReadPeak;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidMeta;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ReadPeakBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpReadPeak>> READPEAK_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final TypeReference<ExtPrebid<ExtBidPrebid, ObjectNode>> EXT_PREBID_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private static final String PRICE_MACRO = "${AUCTION_PRICE}";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public ReadPeakBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<Imp> modifiedImps = new ArrayList<>();
        final List<ExtImpReadPeak> extImpReadPeaks = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                final ExtImpReadPeak extImpReadPeak = parseImpExt(imp);
                final Imp modifiedImp = modifyImp(imp, extImpReadPeak);
                modifiedImps.add(modifiedImp);
                extImpReadPeaks.add(extImpReadPeak);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (modifiedImps.isEmpty()) {
            return Result.withError(BidderError
                    .badInput(String.format("Failed to find compatible impressions for request %s", request.getId())));
        }

        final BidRequest modifiedRequest = request.toBuilder().imp(modifiedImps).build();
        final HttpRequest<BidRequest> httpRequest = makeHttpRequest(modifiedRequest, extImpReadPeaks.get(0));

        return Result.of(Collections.singletonList(httpRequest), errors);
    }

    private ExtImpReadPeak parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), READPEAK_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Failed to deserialize ReadPeak extension: " + e.getMessage());
        }
    }

    private Imp modifyImp(Imp imp, ExtImpReadPeak extImpReadPeak) {
        return imp.toBuilder()
                .bidfloor(extImpReadPeak.getBidFloor() != null ? extImpReadPeak.getBidFloor() : imp.getBidfloor())
                .tagid(StringUtils.isNotBlank(extImpReadPeak.getTagId()) ? extImpReadPeak.getTagId() : null)
                .build();
    }

    private HttpRequest<BidRequest> makeHttpRequest(BidRequest request, ExtImpReadPeak extImpReadPeak) {
        final Publisher publisher = Publisher.builder()
                .id(extImpReadPeak.getPublisherId())
                .build();

        final BidRequest.BidRequestBuilder requestBuilder = request.toBuilder();

        if (request.getSite() != null) {
            final Site site = request.getSite().toBuilder()
                    .id(StringUtils.isNotBlank(extImpReadPeak.getSiteId())
                            ? extImpReadPeak.getSiteId() : request.getSite().getId())
                    .publisher(publisher)
                    .build();
            requestBuilder.site(site);
        } else if (request.getApp() != null) {
            final App app = request.getApp().toBuilder()
                    .id(StringUtils.isNotBlank(extImpReadPeak.getSiteId())
                            ? extImpReadPeak.getSiteId() : request.getApp().getId())
                    .publisher(publisher)
                    .build();
            requestBuilder.app(app);
        }

        final BidRequest outgoingRequest = requestBuilder.build();

        return BidderUtil.defaultRequest(outgoingRequest, endpointUrl, mapper);
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            final List<BidderBid> bids = extractBids(bidResponse);
            return Result.withValues(bids);
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidResponse);
    }

    private List<BidderBid> bidsFromResponse(BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .map(bid -> makeBid(bid, bidResponse))
                .filter(Objects::nonNull)
                .toList();
    }

    private BidderBid makeBid(Bid bid, BidResponse bidResponse) {
        final Bid resolvedBid = resolveMacros(bid);
        final BidType bidType = getBidType(bid);
        final Bid updatedBid = addBidMeta(resolvedBid);
        return BidderBid.of(updatedBid, bidType, bidResponse.getCur());
    }

    private Bid resolveMacros(Bid bid) {
        final BigDecimal price = bid.getPrice();
        final String priceAsString = price != null ? price.toPlainString() : "0";

        return bid.toBuilder()
                .nurl(StringUtils.replace(bid.getNurl(), PRICE_MACRO, priceAsString))
                .adm(StringUtils.replace(bid.getAdm(), PRICE_MACRO, priceAsString))
                .burl(StringUtils.replace(bid.getBurl(), PRICE_MACRO, priceAsString))
                .build();
    }

    private BidType getBidType(Bid bid) {
        final Integer markupType = ObjectUtils.defaultIfNull(bid.getMtype(), 0);

        return switch (markupType) {
            case 1 -> BidType.banner;
            case 4 -> BidType.xNative;
            default -> throw new PreBidException(
                    "Unable to fetch mediaType " + bid.getMtype() + " in multi-format: " + bid.getImpid());
        };
    }

    private Bid addBidMeta(Bid bid) {
        final ExtBidPrebidMeta extBidPrebidMeta = ExtBidPrebidMeta.builder()
                .advertiserDomains(bid.getAdomain())
                .build();

        final ObjectNode bidExt = bid.getExt() != null ? bid.getExt().deepCopy()
                : JsonNodeFactory.instance.objectNode();
        final ObjectNode prebidNode = bidExt
                .has("prebid") ? (ObjectNode) bidExt.get("prebid") : JsonNodeFactory.instance.objectNode();
        prebidNode.set("meta", mapper.mapper().valueToTree(extBidPrebidMeta));
        bidExt.set("prebid", prebidNode);

        return bid.toBuilder()
                .ext(bidExt)
                .build();
    }
}
