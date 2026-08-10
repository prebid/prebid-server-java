package org.prebid.server.bidder.scalibur;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
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
import org.prebid.server.bidder.scalibur.proto.request.ExtImpScalibur;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ScaliburBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpScalibur>> TYPE_REFERENCE = new TypeReference<>() {
    };
    private static final String DEFAULT_BID_CURRENCY = "USD";
    private static final String VAST_XML = """
            <VAST version=\"3.0\"><Ad><Wrapper><VASTAdTagURI><![CDATA[%s]]></VASTAdTagURI></Wrapper></Ad></VAST>
            """;

    private final String endpointUrl;
    private final CurrencyConversionService currencyConversionService;
    private final JacksonMapper mapper;

    public ScaliburBidder(String endpointUrl,
                          CurrencyConversionService currencyConversionService,
                          JacksonMapper mapper) {

        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.currencyConversionService = Objects.requireNonNull(currencyConversionService);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> validImps = new ArrayList<>();

        for (Imp imp : bidRequest.getImp()) {
            try {
                final ExtImpScalibur scaliburExt = parseImpExt(imp);
                validImps.add(modifyImp(imp, scaliburExt, bidRequest));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (validImps.isEmpty()) {
            return Result.withErrors(errors);
        }

        final BidRequest modifiedBidRequest = bidRequest.toBuilder()
                .imp(validImps)
                .cur(null)
                .ext(isDebugEnabled(bidRequest)
                        ? createDebugExt()
                        : null)
                .build();

        final HttpRequest<BidRequest> httpRequest = BidderUtil.defaultRequest(modifiedBidRequest, endpointUrl, mapper);
        return Result.of(Collections.singletonList(httpRequest), errors);
    }

    private ExtImpScalibur parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private Imp modifyImp(Imp imp,
                          ExtImpScalibur extImpScalibur,
                          BidRequest bidRequest) {

        final Price resolvedBidFloor = resolveBidFloor(imp, extImpScalibur, bidRequest);
        final JsonNode gpidNode = imp.getExt().get("gpid");

        return imp.toBuilder()
                .bidfloor(resolvedBidFloor.getValue())
                .bidfloorcur(resolvedBidFloor.getCurrency())
                .video(resolveVideo(imp.getVideo()))
                .ext(resolveImpExt(extImpScalibur, resolvedBidFloor, gpidNode))
                .build();
    }

    private Price resolveBidFloor(Imp imp,
                                  ExtImpScalibur extImpScalibur,
                                  BidRequest bidRequest) {

        final BigDecimal extPrice = extImpScalibur.getBidFloor();
        final Price price = BidderUtil.isValidPrice(extPrice)
                ? Price.of(StringUtils.defaultIfBlank(extImpScalibur.getBidFloorCur(), imp.getBidfloorcur()), extPrice)
                : Price.of(imp.getBidfloorcur(), imp.getBidfloor());
        if (BidderUtil.shouldConvertBidFloor(price, DEFAULT_BID_CURRENCY)) {
            return convertBidFloor(price, bidRequest);
        }
        return Price.of(
                StringUtils.defaultIfBlank(price.getCurrency(), DEFAULT_BID_CURRENCY),
                price.getValue());
    }

    private Price convertBidFloor(Price bidFloorPrice, BidRequest bidRequest) {
        final BigDecimal convertedPrice = currencyConversionService.convertCurrency(bidFloorPrice.getValue(),
                bidRequest,
                bidFloorPrice.getCurrency(),
                DEFAULT_BID_CURRENCY);

        return Price.of(DEFAULT_BID_CURRENCY, convertedPrice);
    }

    private ObjectNode resolveImpExt(ExtImpScalibur extImpScalibur,
                                     Price bidFloor,
                                     JsonNode gpidNode) {

        final ObjectNode ext = mapper.mapper().createObjectNode();
        ext.set("placementId", TextNode.valueOf(extImpScalibur.getPlacementId()));
        if (BidderUtil.isValidPrice(bidFloor)) {
            ext.set("bidfloor", mapper.mapper().valueToTree(bidFloor.getValue()));
        }
        ext.set("bidfloorcur", TextNode.valueOf(bidFloor.getCurrency()));
        if (gpidNode != null && !gpidNode.isNull()) {
            ext.set("gpid", gpidNode);
        }
        return ext;
    }

    private Video resolveVideo(Video video) {
        if (video == null) {
            return null;
        }

        final Video.VideoBuilder builder = video.toBuilder();

        if (CollectionUtils.isEmpty(video.getMimes())) {
            builder.mimes(Collections.singletonList("video/mp4"));
        }
        if (BidderUtil.isNullOrZero(video.getMinduration())) {
            builder.minduration(1);
        }
        if (BidderUtil.isNullOrZero(video.getMaxduration())) {
            builder.maxduration(180);
        }
        if (BidderUtil.isNullOrZero(video.getMaxbitrate())) {
            builder.maxbitrate(30_000);
        }
        if (CollectionUtils.isEmpty(video.getProtocols())) {
            builder.protocols(List.of(2, 3, 5, 6));
        }
        if (BidderUtil.isNullOrZero(video.getW())) {
            builder.w(640);
        }
        if (BidderUtil.isNullOrZero(video.getH())) {
            builder.h(480);
        }
        if (BidderUtil.isNullOrZero(video.getPlacement())) {
            builder.placement(1);
        }
        if (BidderUtil.isNullOrZero(video.getLinearity())) {
            builder.linearity(1);
        }

        return builder.build();
    }

    private boolean isDebugEnabled(BidRequest bidRequest) {
        if (Objects.equals(bidRequest.getTest(), 1)) {
            return true;
        }
        final ExtRequest ext = bidRequest.getExt();
        if (ext == null || ext.getPrebid() == null) {
            return false;
        }
        return Objects.equals(ext.getPrebid().getDebug(), 1);
    }

    private ExtRequest createDebugExt() {
        final ExtRequest extRequest = ExtRequest.empty();
        extRequest.addProperty("isDebug", IntNode.valueOf(1));
        return extRequest;
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return extractBids(httpCall.getRequest().getPayload(), bidResponse);
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private Result<List<BidderBid>> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Result.empty();
        }
        final List<BidderError> errors = new ArrayList<>();
        final List<BidderBid> bidderBids = new ArrayList<>();
        for (SeatBid seatBid : bidResponse.getSeatbid()) {
            if (CollectionUtils.isEmpty(seatBid.getBid())) {
                continue;
            }

            for (Bid bid : seatBid.getBid()) {
                try {
                    bidderBids.add(createBidderBid(bid, bidRequest, bidResponse));
                } catch (PreBidException e) {
                    errors.add(BidderError.badServerResponse(e.getMessage()));
                }
            }
        }

        return Result.of(bidderBids, errors);
    }

    private BidderBid createBidderBid(Bid bid, BidRequest bidRequest, BidResponse bidResponse) {
        final Imp imp = bidRequest.getImp().stream()
                .filter(candidate -> Objects.equals(candidate.getId(), bid.getImpid()))
                .findFirst()
                .orElseThrow(() -> new PreBidException("Invalid bid imp ID %s".formatted(bid.getImpid())));

        final BidType bidType = resolveBidType(bid, imp);
        final Bid resolvedBid = bidType == BidType.video ? resolveVideoBid(bid) : bid;
        final String resolvedCurrency = StringUtils.defaultIfEmpty(bidResponse.getCur(), DEFAULT_BID_CURRENCY);

        return BidderBid.of(resolvedBid, bidType, resolvedCurrency);
    }

    private Bid resolveVideoBid(Bid bid) {
        if (bid.getExt() == null) {
            return bid;
        }

        final JsonNode vastXml = bid.getExt().get("vastXml");
        if (isNonEmptyText(vastXml)) {
            return bid.toBuilder().adm(vastXml.asText()).build();
        }

        final JsonNode vastUrl = bid.getExt().get("vastUrl");
        if (isNonEmptyText(vastUrl) && StringUtils.isEmpty(bid.getAdm())) {
            return bid.toBuilder()
                    .adm(VAST_XML.formatted(vastUrl.asText()).trim())
                    .build();
        }
        return bid;
    }

    private boolean isNonEmptyText(JsonNode node) {
        return node != null && node.isTextual() && StringUtils.isNotEmpty(node.asText());
    }

    private BidType resolveBidType(Bid bid, Imp imp) {
        if (Objects.equals(bid.getMtype(), 1)) {
            return BidType.banner;
        }
        if (Objects.equals(bid.getMtype(), 2)) {
            return BidType.video;
        }
        if (imp.getBanner() != null && imp.getVideo() == null) {
            return BidType.banner;
        }
        if (imp.getVideo() != null && imp.getBanner() == null) {
            return BidType.video;
        }
        throw new PreBidException(
                "Unsupported or ambiguous media type for bid id=%s".formatted(bid.getId()));
    }
}
