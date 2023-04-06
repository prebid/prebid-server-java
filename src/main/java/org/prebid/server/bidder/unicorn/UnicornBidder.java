package org.prebid.server.bidder.unicorn;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
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
import org.prebid.server.bidder.unicorn.model.UnicornImpExt;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtSource;
import org.prebid.server.proto.openrtb.ext.request.unicorn.ExtImpUnicorn;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class UnicornBidder implements Bidder<BidRequest> {

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public UnicornBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final ExtImpUnicorn firstImpExt;
        final List<Imp> modifiedImps;
        final Source modifiedSource;
        final App modifiedApp;

        try {
            validateRegs(request.getRegs());

            firstImpExt = parseImpExt(request.getImp().get(0)).getBidder();
            modifiedImps = request.getImp().stream().map(this::modifyImp).toList();
            modifiedSource = modifySource(request.getSource());
            modifiedApp = modifyApp(request.getApp(), firstImpExt.getMediaId(), firstImpExt.getPublisherId());
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        final ExtRequest modifiedExtRequest = modifyExtRequest(request.getExt(), firstImpExt.getAccountId());
        return Result.withValue(createRequest(request, modifiedImps, modifiedApp, modifiedSource, modifiedExtRequest));
    }

    private static void validateRegs(Regs regs) {
        if (regs != null) {
            if (Objects.equals(regs.getCoppa(), 1)) {
                throw new PreBidException("COPPA is not supported");
            }

            final ExtRegs extRegs = regs.getExt();
            if (extRegs != null) {
                if (Objects.equals(extRegs.getGdpr(), 1)) {
                    throw new PreBidException("GDPR is not supported");
                }

                if (StringUtils.isNotEmpty(extRegs.getUsPrivacy())) {
                    throw new PreBidException("CCPA is not supported");
                }
            }
        }
    }

    private Imp modifyImp(Imp imp) {
        final UnicornImpExt unicornImpExt = parseImpExt(imp);
        final ExtImpUnicorn extImpBidder = unicornImpExt.getBidder();

        final String placementId = extImpBidder.getPlacementId();
        final String resolvedPlacementId = StringUtils.isEmpty(placementId)
                ? getStoredRequestImpId(imp)
                : null;

        final UnicornImpExt resolvedUnicornImpExt = resolvedPlacementId != null
                ? unicornImpExt.toBuilder()
                .bidder(extImpBidder.toBuilder().placementId(resolvedPlacementId).build())
                .build()
                : null;

        return imp.toBuilder()
                .secure(1)
                .tagid(resolvedPlacementId != null ? resolvedPlacementId : placementId)
                .ext(mapper.mapper().convertValue(
                        ObjectUtils.defaultIfNull(resolvedUnicornImpExt, unicornImpExt),
                        ObjectNode.class))
                .build();
    }

    private UnicornImpExt parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), UnicornImpExt.class);
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Error while decoding ext of imp with id: %s, error: %s "
                    .formatted(imp.getId(), e.getMessage()));
        }
    }

    private static String getStoredRequestImpId(Imp imp) {
        final JsonNode extPrebid = imp.getExt().get("prebid");
        final JsonNode storedRequestNode = isNotEmptyNode(extPrebid) ? extPrebid.get("storedrequest") : null;
        final JsonNode storedRequestIdNode = isNotEmptyNode(storedRequestNode) ? storedRequestNode.get("id") : null;
        final String storedRequestId = storedRequestIdNode != null && storedRequestIdNode.isTextual()
                ? storedRequestIdNode.textValue()
                : null;

        if (StringUtils.isEmpty(storedRequestId)) {
            throw new PreBidException("stored request id not found in imp: " + imp.getId());
        }

        return storedRequestId;
    }

    private static boolean isNotEmptyNode(JsonNode node) {
        return node != null && !node.isEmpty();
    }

    private static Source modifySource(Source source) {
        return Optional.ofNullable(source)
                .map(Source::toBuilder)
                .orElseGet(Source::builder)
                .ext(createExtSource())
                .build();
    }

    private static ExtSource createExtSource() {
        final ExtSource extSource = ExtSource.of(null);
        extSource.addProperty("stype", new TextNode("prebid_server_uncn"));
        extSource.addProperty("bidder", new TextNode("unicorn"));
        return extSource;
    }

    private static App modifyApp(App app, String mediaId, String publisherId) {
        if (app == null) {
            throw new PreBidException("request app is required");
        }

        final Publisher publisher = Optional.ofNullable(app.getPublisher())
                .map(Publisher::toBuilder)
                .orElseGet(Publisher::builder)
                .id(publisherId)
                .build();

        return app.toBuilder().id(mediaId).publisher(publisher).build();
    }

    private static ExtRequest modifyExtRequest(ExtRequest extRequest, int accountId) {
        final ExtRequest modifiedRequest;
        if (extRequest != null) {
            modifiedRequest = ExtRequest.of(extRequest.getPrebid());
            modifiedRequest.addProperties(extRequest.getProperties());
        } else {
            modifiedRequest = ExtRequest.of(null);
        }

        modifiedRequest.addProperty("accountId", new IntNode(accountId));

        return modifiedRequest;
    }

    private HttpRequest<BidRequest> createRequest(BidRequest request,
                                                  List<Imp> imps,
                                                  App app,
                                                  Source source,
                                                  ExtRequest extRequest) {

        final BidRequest outgoingRequest = request.toBuilder()
                .imp(imps)
                .app(app)
                .source(source)
                .ext(extRequest)
                .build();

        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .headers(resolveHeaders(request.getDevice()))
                .payload(outgoingRequest)
                .body(mapper.encodeToBytes(outgoingRequest))
                .build();
    }

    private static MultiMap resolveHeaders(Device device) {
        final MultiMap headers = HttpUtil.headers();
        HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_OPENRTB_VERSION_HEADER, "2.5");

        if (device != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER, device.getUa());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIpv6());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIp());
        }

        return headers;
    }

    @Override
    public final Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(bidResponse), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, BidType.banner, bidResponse.getCur()))
                .toList();
    }
}
