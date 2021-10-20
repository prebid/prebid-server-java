package org.prebid.server.bidder.impactify;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.impactify.ExtImpImpactify;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ImpactifyBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpImpactify>> IMPACTIFY_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpImpactify>>() {
            };
    private static final String X_OPENRTB_VERSION = "2.5";
    private static final String BIDDER_CURRENCY = "USD";

    private final String endpointUrl;
    private final JacksonMapper mapper;
    private final CurrencyConversionService currencyConversionService;

    public ImpactifyBidder(String endpointUrl, JacksonMapper mapper, CurrencyConversionService conversionService) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
        this.currencyConversionService = Objects.requireNonNull(conversionService);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<Imp> imps = request.getImp();
        final List<Imp> updatedImps = new ArrayList<>();
        final BidRequest updatedBidRequest;

        for (Imp imp : imps) {
            if (imp.getBidfloor().compareTo(BigDecimal.ZERO) > 0
                    && !imp.getBidfloorcur().isEmpty()
                    && !imp.getBidfloorcur().equalsIgnoreCase(BIDDER_CURRENCY)) {
                final ExtImpImpactify extImpImpactify;
                try {
                    extImpImpactify = mapper.mapper().convertValue(imp.getExt(), IMPACTIFY_EXT_TYPE_REFERENCE).getBidder();
                } catch (IllegalArgumentException e) {
                    return Result.withError(BidderError.badInput("Ext.bidder not provided"));
                }

                updatedImps.add(imp.toBuilder()
                        .bidfloor(resolveBidFloor(imp, request))
                        .ext(mapper.mapper().convertValue(extImpImpactify, ObjectNode.class))
                        .build());
            }
        }
        if (updatedImps.size() == 0) {
            return Result
                    .withError(BidderError.badInput("No valid impressions in the bid request"));
        }

        updatedBidRequest = request.toBuilder()
                .cur(List.of(BIDDER_CURRENCY))
                .imp(updatedImps)
                .build();

        return Result.withValue(HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(resolveEndpoint())
                .headers(constructHeaders(updatedBidRequest))
                .body(mapper.encode(updatedBidRequest))
                .payload(updatedBidRequest)
                .build());
    }

    private static BigDecimal resolveBidFloorPrice(Imp imp) {
        final BigDecimal bidFloor = imp.getBidfloor();
        return BidderUtil.isValidPrice(bidFloor) ? bidFloor : null;
    }

    private static MultiMap constructHeaders(BidRequest bidRequest) {
        final var device = bidRequest.getDevice();
        final var deviceUa = device != null ? device.getUa() : null;
        final var deviceIp = device != null ? device.getIp() : null;
        final var deviceIpv6 = device != null ? device.getIpv6() : null;
        final var site = bidRequest.getSite();
        final var sitePage = site != null ? site.getPage() : null;
        final var user = bidRequest.getUser();
        final var userUid = user != null ? user.getBuyeruid() : null;
        final var headers = HttpUtil.headers();

        headers.set(HttpUtil.X_OPENRTB_VERSION_HEADER, X_OPENRTB_VERSION);
        headers.set(HttpUtil.CONTENT_TYPE_HEADER, HttpUtil.APPLICATION_JSON_CONTENT_TYPE);
        headers.set(HttpUtil.ACCEPT_HEADER, HttpHeaderValues.APPLICATION_JSON);
        if (Objects.nonNull(device)) {
            if (Objects.nonNull(deviceUa)) {
                headers.set(HttpUtil.USER_AGENT_HEADER, deviceUa);
            }
            if (Objects.nonNull(deviceIp)) {
                headers.set(HttpUtil.X_FORWARDED_FOR_HEADER, deviceIp);
            }
            if (Objects.nonNull(deviceIpv6)) {
                headers.set(HttpUtil.X_FORWARDED_FOR_HEADER, deviceIpv6);
            }
        }
        if (Objects.nonNull(site)) {
            headers.set(HttpUtil.REFERER_HEADER, sitePage);
        }
        if (Objects.nonNull(user) && Objects.nonNull(userUid) && !userUid.isEmpty()) {
            headers.set(HttpUtil.REFERER_HEADER, sitePage);
        }

        return headers;
    }

    private BigDecimal resolveBidFloor(Imp imp, BidRequest bidRequest) {
        final BigDecimal validBidFloorPrice = resolveBidFloorPrice(imp);
        if (validBidFloorPrice == null) {
            return null;
        }

        return convertBidFloorCurrency(validBidFloorPrice, bidRequest, imp);
    }

    private BigDecimal convertBidFloorCurrency(BigDecimal bidFloor, BidRequest bidRequest, Imp imp) {
        try {
            return currencyConversionService.convertCurrency(bidFloor, bidRequest, imp.getBidfloorcur(), BIDDER_CURRENCY);
        } catch (PreBidException e) {
            throw new PreBidException(String.format(
                    "Unable to convert provided bid floor currency from %s to %s for imp `%s` with a reason: %s",
                    imp.getBidfloorcur(), BIDDER_CURRENCY, imp.getId(), e.getMessage()));
        }
    }

    private String resolveEndpoint() {
        return endpointUrl;
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall httpCall, BidRequest bidRequest) {
        return null;
    }
}
