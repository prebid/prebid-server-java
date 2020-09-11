package org.prebid.server.bidder.consumable;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.consumable.model.ConsumableAdType;
import org.prebid.server.bidder.consumable.model.ConsumableBidGdpr;
import org.prebid.server.bidder.consumable.model.ConsumableBidRequest;
import org.prebid.server.bidder.consumable.model.ConsumableBidResponse;
import org.prebid.server.bidder.consumable.model.ConsumableDecision;
import org.prebid.server.bidder.consumable.model.ConsumablePlacement;
import org.prebid.server.bidder.consumable.model.ConsumablePricing;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.EncodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.consumable.ExtImpConsumable;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ConsumableBidder implements Bidder<ConsumableBidRequest> {

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public ConsumableBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<ConsumableBidRequest>>> makeHttpRequests(BidRequest request) {
        final ConsumableBidRequest.ConsumableBidRequestBuilder requestBuilder = ConsumableBidRequest.builder()
                .time(Instant.now().getEpochSecond())
                .includePricingData(true)
                .enableBotFiltering(true)
                .parallel(true);

        final Site site = request.getSite();
        if (site != null) {
            requestBuilder
                    .referrer(site.getRef())
                    .url(site.getPage());
        }

        final Regs regs = request.getRegs();
        final ExtRegs extRegs = regs != null ? regs.getExt() : null;
        final String usPrivacy = extRegs != null ? extRegs.getUsPrivacy() : null;
        if (usPrivacy != null) {
            requestBuilder.usPrivacy(usPrivacy);
        }

        final Integer gdpr = extRegs != null ? extRegs.getGdpr() : null;
        final User user = request.getUser();
        final ExtUser extUser = user != null ? user.getExt() : null;
        final String gdprConsent = extUser != null ? extUser.getConsent() : null;
        if (gdpr != null || gdprConsent != null) {
            final ConsumableBidGdpr.ConsumableBidGdprBuilder bidGdprBuilder = ConsumableBidGdpr.builder();
            if (gdpr != null) {
                bidGdprBuilder.applies(gdpr != 0);
            }
            if (gdprConsent != null) {
                bidGdprBuilder.consent(gdprConsent).build();
            }
            requestBuilder.gdpr(bidGdprBuilder.build());
        }

        try {
            resolveRequestFields(requestBuilder, request.getImp());
        } catch (PreBidException e) {
            return Result.of(Collections.emptyList(), Collections.singletonList(BidderError.badInput(e.getMessage())));
        }

        final ConsumableBidRequest outgoingRequest = requestBuilder.build();
        String body;
        try {
            body = mapper.encode(outgoingRequest);
        } catch (EncodeException e) {
            return Result.of(Collections.emptyList(),
                    Collections.singletonList(BidderError.badInput(
                            String.format("Failed to encode request body, error: %s", e.getMessage()))));
        }

        return Result.of(Collections.singletonList(
                HttpRequest.<ConsumableBidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(endpointUrl)
                        .body(body)
                        .headers(resolveHeaders(request))
                        .payload(outgoingRequest)
                        .build()),
                Collections.emptyList());
    }

    private void resolveRequestFields(ConsumableBidRequest.ConsumableBidRequestBuilder requestBuilder,
                                      List<Imp> imps) {
        final List<ConsumablePlacement> placements = new ArrayList<>();
        for (int i = 0; i < imps.size(); i++) {
            final Imp currentImp = imps.get(i);
            final ExtImpConsumable extImpConsumable = parseImpExt(currentImp);
            if (i == 0) {
                requestBuilder
                        .networkId(extImpConsumable.getNetworkId())
                        .siteId(extImpConsumable.getSiteId())
                        .unitId(extImpConsumable.getUnitId())
                        .unitName(extImpConsumable.getUnitName());
            }
            placements.add(ConsumablePlacement.builder()
                    .divName(currentImp.getId())
                    .networkId(extImpConsumable.getNetworkId())
                    .siteId(extImpConsumable.getSiteId())
                    .unitId(extImpConsumable.getUnitId())
                    .unitName(extImpConsumable.getUnitName())
                    .adTypes(ConsumableAdType.getSizeCodes(currentImp.getBanner().getFormat()))
                    .build());
        }
        requestBuilder.placements(placements);
    }

    private ExtImpConsumable parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt().get("bidder"), ExtImpConsumable.class);
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private static MultiMap resolveHeaders(BidRequest request) {
        final MultiMap headers = HttpUtil.headers();
        final Device device = request.getDevice();
        if (device != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, "User-Agent", device.getUa());
            final String ip = device.getIp();
            if (StringUtils.isNotBlank(ip)) {
                headers.add("Forwarded", "for=" + ip);
                headers.add("X-Forwarded-For", ip);
            }
        }

        final User user = request.getUser();
        if (user != null && StringUtils.isNotBlank(user.getBuyeruid())) {
            headers.add("Cookie", String.format("azk=%s", user.getBuyeruid().trim()));
        }

        final Site site = request.getSite();
        if (site != null && StringUtils.isNotBlank(site.getPage())) {
            headers.set("Referer", site.getPage());
            try {
                headers.set("Origin", HttpUtil.validateUrl(site.getPage()));
            } catch (IllegalArgumentException e) {
                // do nothing, just skip adding this header
            }
        }

        return headers;
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<ConsumableBidRequest> httpCall, BidRequest bidRequest) {
        final ConsumableBidResponse consumableResponse;
        try {
            consumableResponse = mapper.decodeValue(httpCall.getResponse().getBody(), ConsumableBidResponse.class);
        } catch (DecodeException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }
        final List<BidderError> errors = new ArrayList<>();
        final List<BidderBid> bidderBids = extractBids(bidRequest, consumableResponse.getDecisions(), errors);
        return Result.of(bidderBids, errors);
    }

    private static List<BidderBid> extractBids(BidRequest bidRequest, Map<String, ConsumableDecision> impIdToDecisions,
                                               List<BidderError> errors) {
        final List<BidderBid> bidderBids = new ArrayList<>();
        for (Map.Entry<String, ConsumableDecision> entry : impIdToDecisions.entrySet()) {
            final ConsumableDecision decision = entry.getValue();

            if (decision != null) {
                final ConsumablePricing pricing = decision.getPricing();
                if (pricing != null && pricing.getClearPrice() != null) {
                    final String impId = entry.getKey();
                    final Imp imp;
                    try {
                        imp = getImpById(impId, bidRequest);
                    } catch (PreBidException e) {
                        errors.add(BidderError.badServerResponse(e.getMessage()));
                        continue;
                    }

                    final List<Format> formats = imp.getBanner().getFormat();
                    if (CollectionUtils.isEmpty(formats)) {
                        errors.add(BidderError.badInput(
                                String.format("Skipping imp ID: %s - null or empty formats", imp.getId())));
                        continue;
                    }

                    final Format firstFormat = formats.get(0);
                    final Bid bid = Bid.builder()
                            .id(bidRequest.getId())
                            .impid(impId)
                            .price(BigDecimal.valueOf(pricing.getClearPrice()))
                            .adm(CollectionUtils.isNotEmpty(decision.getContents())
                                    ? decision.getContents().get(0).getBody() : "")
                            .w(firstFormat.getW())
                            .h(firstFormat.getH())
                            .crid(String.valueOf(decision.getAdId()))
                            .exp(30)
                            .build();
                    bidderBids.add(BidderBid.of(bid, BidType.banner, null));
                }
            }
        }
        return bidderBids;
    }

    private static Imp getImpById(String impId, BidRequest bidRequest) {
        return bidRequest.getImp().stream()
                .filter(imp -> imp.getId().equals(impId))
                .findFirst()
                .orElseThrow(() -> new PreBidException(
                        String.format("ignoring bid id=%s, request doesn't contain any impression with id=%s",
                                bidRequest.getId(), impId)));
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}
