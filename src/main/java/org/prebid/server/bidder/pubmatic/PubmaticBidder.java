package org.prebid.server.bidder.pubmatic;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.pubmatic.proto.PubmaticRequestExt;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.pubmatic.ExtImpPubmatic;
import org.prebid.server.proto.openrtb.ext.request.pubmatic.ExtImpPubmaticKeyVal;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class PubmaticBidder implements Bidder<BidRequest> {

    private static final Logger logger = LoggerFactory.getLogger(PubmaticBidder.class);

    private static final String DEFAULT_BID_CURRENCY = "USD";
    private static final String BID_TYPE_EXT_KEY = "BidType";
    private static final TypeReference<ExtPrebid<?, ExtImpPubmatic>> PUBMATIC_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpPubmatic>>() {
            };
    private static final TypeReference<Map<String, Integer>> WRAPPER_VALIDATION =
            new TypeReference<Map<String, Integer>>() {
            };

    private final String endpointUrl;

    public PubmaticBidder(String endpointUrl) {
        this.endpointUrl = HttpUtil.validateUrl(endpointUrl);
    }

    @Override
    public final Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> modifiedImps = new ArrayList<>();
        final List<ExtImpPubmatic> extImpPubmatics = new ArrayList<>();
        for (Imp imp : bidRequest.getImp()) {
            try {
                final ExtImpPubmatic extImpPubmatic = parseImpExt(imp);
                final Imp modifiedImp = modifyImp(imp, extImpPubmatic);
                modifiedImps.add(modifiedImp);
                extImpPubmatics.add(extImpPubmatic);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (modifiedImps.isEmpty()) {
            return Result.of(Collections.emptyList(), errors);
        }

        return Result.of(Collections.singletonList(makeRequest(bidRequest, modifiedImps, extImpPubmatics)), errors);
    }

    private static ExtImpPubmatic parseImpExt(Imp imp) {
        try {
            return Json.mapper.<ExtPrebid<?, ExtImpPubmatic>>convertValue(imp.getExt(), PUBMATIC_EXT_TYPE_REFERENCE)
                    .getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private static Imp modifyImp(Imp imp, ExtImpPubmatic extImpPubmatic) throws PreBidException {
        // validate Impression
        if (imp.getBanner() == null && imp.getVideo() == null) {
            throw new PreBidException(String.format("Invalid MediaType. PubMatic only supports Banner and Video. "
                    + "Ignoring ImpID=%s", imp.getId()));
        }

        // impression extension validation
        final ObjectNode wrapExt = extImpPubmatic.getWrapper();
        if (wrapExt != null) {
            try {
                Json.mapper.convertValue(wrapExt, WRAPPER_VALIDATION);
            } catch (IllegalArgumentException e) {
                throw new PreBidException(
                        String.format("Error in Wrapper Parameters = %s  for ImpID = %s WrapperExt = %s",
                                e.getMessage(), imp.getId(), wrapExt.toString()));
            }
        }

        // impression changes and additional validation
        final Imp.ImpBuilder modifiedImp = imp.toBuilder();
        if (imp.getAudio() != null) {
            modifiedImp.audio(null);
        }
        final Banner banner = imp.getBanner();
        if (banner != null) {
            final String adSlotString = StringUtils.trimToNull(extImpPubmatic.getAdSlot());
            Integer width = null;
            Integer height = null;
            if (!StringUtils.isEmpty(adSlotString)) {
                if (!adSlotString.contains("@")) {
                    modifiedImp.tagid(adSlotString);
                } else {
                    final String[] adSlot = adSlotString.split("@");
                    if (adSlot.length != 2 || StringUtils.isEmpty(adSlot[0].trim())
                            || StringUtils.isEmpty(adSlot[1].trim())) {
                        throw new PreBidException("Invalid adSlot provided");
                    }
                    modifiedImp.tagid(adSlot[0].trim());
                    final String[] adSize = adSlot[1].toLowerCase().split("x");
                    if (adSize.length != 2) {
                        throw new PreBidException("Invalid size provided in adSlot");
                    }
                    final String[] heightStr = adSize[1].split(":");
                    try {
                        width = Integer.valueOf(adSize[0].trim());
                        height = Integer.valueOf(heightStr[0].trim());
                    } catch (NumberFormatException e) {
                        throw new PreBidException("Invalid size provided in adSlot");
                    }
                }
            }
            if (width == null && height == null) {
                final boolean isFormatsPresent = CollectionUtils.isNotEmpty(banner.getFormat());
                width = isFormatsPresent && banner.getW() == null && banner.getH() == null
                        ? banner.getFormat().get(0).getW() : banner.getW();

                height = isFormatsPresent && banner.getH() == null && banner.getW() == null
                        ? banner.getFormat().get(0).getH() : banner.getH();
            }
            final Banner updatedBanner = banner.toBuilder().w(width).h(height).build();
            modifiedImp.banner(updatedBanner);
        }

        if (CollectionUtils.isNotEmpty(extImpPubmatic.getKeywords())) {
            modifiedImp.ext(makeKeywords(extImpPubmatic.getKeywords()));
        } else {
            modifiedImp.ext(null);
        }
        return modifiedImp.build();
    }

    private static ObjectNode makeKeywords(List<ExtImpPubmaticKeyVal> keywords) {
        final List<String> eachKv = new ArrayList<>();
        for (ExtImpPubmaticKeyVal keyVal : keywords) {
            if (CollectionUtils.isEmpty(keyVal.getValue())) {
                logger.error(String.format("No values present for key = %s", keyVal.getKey()));
            } else {
                eachKv.add(String.format("\"%s\":\"%s\"", keyVal.getKey(),
                        String.join(",", keyVal.getValue())));
            }
        }
        final String keywordsString = "{" + String.join(",", eachKv) + "}";
        try {
            return Json.mapper.readValue(keywordsString, ObjectNode.class);
        } catch (IOException e) {
            throw new PreBidException(String.format("Failed to create keywords with error: %s", e.getMessage()), e);
        }
    }

    private HttpRequest<BidRequest> makeRequest(BidRequest bidRequest, List<Imp> imps,
                                                List<ExtImpPubmatic> extImpPubmatics) {
        final BidRequest.BidRequestBuilder requestBuilder = bidRequest.toBuilder().imp(imps);

        extImpPubmatics.stream()
                .map(ExtImpPubmatic::getWrapper)
                .filter(Objects::nonNull)
                .findFirst()
                .ifPresent(wrapExt -> requestBuilder.ext(Json.mapper.valueToTree(PubmaticRequestExt.of(wrapExt))));

        final String pubId = extImpPubmatics.stream()
                .map(ExtImpPubmatic::getPublisherId)
                .filter(Objects::nonNull)
                .findFirst().orElse(null);

        if (bidRequest.getSite() != null) {
            modifySite(pubId, bidRequest, requestBuilder);
        } else if (bidRequest.getApp() != null) {
            modifyApp(pubId, bidRequest, requestBuilder);
        }

        final BidRequest modifiedRequest = requestBuilder.build();
        final String body = Json.encode(modifiedRequest);

        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .body(body)
                .headers(HttpUtil.headers())
                .payload(modifiedRequest)
                .build();
    }

    private static void modifySite(String pubId, BidRequest bidRequest,
                                   BidRequest.BidRequestBuilder bidRequestBuilder) {
        final Site site = bidRequest.getSite();
        if (site.getPublisher() != null) {
            final Publisher modifiedPublisher = site.getPublisher().toBuilder().id(pubId).build();
            bidRequestBuilder.site(site.toBuilder().publisher(modifiedPublisher).build());
        } else {
            bidRequestBuilder.site(site.toBuilder()
                    .publisher(Publisher.builder().id(pubId).build())
                    .build());
        }
    }

    private static void modifyApp(String pubId, BidRequest bidRequest,
                                  BidRequest.BidRequestBuilder bidRequestBuilder) {
        final App app = bidRequest.getApp();
        if (app.getPublisher() != null) {
            final Publisher modifiedPublisher = app.getPublisher().toBuilder().id(pubId).build();
            bidRequestBuilder.app(app.toBuilder().publisher(modifiedPublisher).build());
        } else {
            bidRequestBuilder.app(app.toBuilder()
                    .publisher(Publisher.builder().id(pubId).build())
                    .build());
        }
    }

    @Override
    public final Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = Json.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(bidResponse), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse) {
        return bidResponse == null || bidResponse.getSeatbid() == null
                ? Collections.emptyList()
                : bidsFromResponse(bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, getBidType(bid.getExt()), DEFAULT_BID_CURRENCY))
                .collect(Collectors.toList());
    }

    protected static BidType getBidType(ObjectNode bidExt) {
        if (bidExt != null) {
            final JsonNode bidTypeVal = bidExt.get(BID_TYPE_EXT_KEY);
            if (bidTypeVal != null) {
                switch (bidTypeVal.asInt()) {
                    case 1:
                        return BidType.video;
                    case 2:
                        return BidType.xNative;
                    default:
                        return BidType.banner;
                }
            }
        }
        return BidType.banner;
    }

    @Override
    public final Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}
