package org.prebid.server.bidder.pubmatic;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.pubmatic.model.request.PubmaticBidderImpExt;
import org.prebid.server.bidder.pubmatic.model.request.PubmaticExtData;
import org.prebid.server.bidder.pubmatic.model.request.PubmaticExtDataAdServer;
import org.prebid.server.bidder.pubmatic.model.response.PubmaticBidExt;
import org.prebid.server.bidder.pubmatic.model.response.VideoCreativeInfo;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.pubmatic.ExtImpPubmatic;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidVideo;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class PubmaticBidder implements Bidder<BidRequest> {

    private static final String DCTR_KEY_NAME = "key_val";
    private static final String PM_ZONE_ID_KEY_NAME = "pmZoneId";
    private static final String PM_ZONE_ID_OLD_KEY_NAME = "pmZoneID";
    private static final String IMP_EXT_AD_UNIT_KEY = "dfp_ad_unit_code";
    private static final String AD_SERVER_GAM = "gam";
    private static final String PREBID = "prebid";

    private static final TypeReference<Map<String, Integer>> WRAPPER_TYPE =
            new TypeReference<Map<String, Integer>>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public PubmaticBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> validImps = new ArrayList<>();

        ObjectNode wrapper = null;
        String pubId = null;

        for (Imp imp : request.getImp()) {
            try {
                if (imp.getBanner() == null && imp.getVideo() == null) {
                    throw new PreBidException(String.format("Invalid MediaType. PubMatic only supports "
                            + "Banner and Video. Ignoring ImpID=%s", imp.getId()));
                }
                final PubmaticBidderImpExt impExt = parseImpExt(imp);
                final ExtImpPubmatic extBidder = impExt.getBidder();
                if (pubId == null) {
                    pubId = StringUtils.trimToNull(extBidder.getPublisherId());
                }
                final ObjectNode extWrapper = impExt.getBidder().getWrapper();
                if (wrapper == null && extWrapper != null && !extWrapper.isEmpty()) {
                    validateExtWrapper(extWrapper, imp.getId());
                    wrapper = extWrapper;
                }

                validImps.add(modifyImp(imp, impExt));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (validImps.isEmpty()) {
            return Result.withErrors(errors);
        }

        return Result.of(Collections.singletonList(createRequest(request, validImps, pubId, wrapper)), errors);
    }

    private PubmaticBidderImpExt parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), PubmaticBidderImpExt.class);
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private Imp modifyImp(Imp imp, PubmaticBidderImpExt impExt) {
        final Imp.ImpBuilder impBuilder = imp.toBuilder().audio(null);

        final Banner banner = imp.getBanner();

        if (banner != null) {
            impBuilder.banner(assignSizesIfMissing(banner));
        }

        enrichImpBuilderWithAdSlotParameters(impBuilder, impExt.getBidder().getAdSlot(), banner);

        final ObjectNode keywordsNode = makeKeywords(impExt);
        if (!keywordsNode.isEmpty()) {
            impBuilder.ext(keywordsNode);
        } else {
            impBuilder.ext(null);
        }

        return impBuilder.build();
    }

    private void validateExtWrapper(ObjectNode wrapExt, String impId) {
        try {
            mapper.mapper().convertValue(wrapExt, WRAPPER_TYPE);
        } catch (IllegalArgumentException e) {
            throw new PreBidException(
                    String.format("Error in Wrapper Parameters = %s  for ImpID = %s WrapperExt = %s",
                            e.getMessage(), impId, wrapExt.toString()));
        }
    }

    private void enrichImpBuilderWithAdSlotParameters(Imp.ImpBuilder impBuilder, String adSlot, Banner banner) {
        final String trimmedAdSlot = StringUtils.trimToNull(adSlot);

        if (StringUtils.isEmpty(trimmedAdSlot)) {
            return;
        }

        if (!trimmedAdSlot.contains("@")) {
            impBuilder.tagid(trimmedAdSlot);
            return;
        }

        final String[] adSlotParams = trimmedAdSlot.split("@");
        if (adSlotParams.length != 2
                || StringUtils.isEmpty(adSlotParams[0].trim())
                || StringUtils.isEmpty(adSlotParams[1].trim())) {
            throw new PreBidException(String.format("Invalid adSlot '%s'", trimmedAdSlot));
        }
        impBuilder.tagid(adSlotParams[0]);
        final String[] adSize = adSlotParams[1].toLowerCase().split("x");
        if (adSize.length != 2) {
            throw new PreBidException(String.format("Invalid size provided in adSlot '%s'", trimmedAdSlot));
        }

        final Integer width = parseAdSizeParam(adSize[0], "width", adSlot);
        final String[] heightParams = adSize[1].split(":");
        final Integer height = parseAdSizeParam(heightParams[0], "height", adSlot);

        impBuilder.banner(modifyWithSizeParams(banner, width, height));
    }

    private static Integer parseAdSizeParam(String number, String paramName, String adSlot) {
        try {
            return Integer.parseInt(number.trim());
        } catch (NumberFormatException e) {
            throw new PreBidException(String.format("Invalid %s provided in adSlot '%s'", paramName, adSlot));
        }
    }

    private static Banner modifyWithSizeParams(Banner banner, Integer width, Integer height) {
        return banner != null
                ? banner.toBuilder().w(width).h(height).build()
                : null;
    }

    private static Banner assignSizesIfMissing(Banner banner) {
        final List<Format> format = banner.getFormat();
        if ((banner.getW() != null && banner.getH() != null) || CollectionUtils.isEmpty(format)) {
            return banner;
        }

        final Format firstFormat = format.get(0);

        return modifyWithSizeParams(banner, firstFormat.getW(), firstFormat.getH());
    }

    private ObjectNode makeKeywords(PubmaticBidderImpExt impExt) {
        final ObjectNode keywordsNode = mapper.mapper().createObjectNode();
        putExtBidderKeywords(keywordsNode, impExt.getBidder());

        final PubmaticExtData pubmaticExtData = impExt.getData();
        if (pubmaticExtData != null) {
            putExtDataKeywords(keywordsNode, pubmaticExtData);
        }

        return keywordsNode;
    }

    private static void putExtBidderKeywords(ObjectNode keywords, ExtImpPubmatic extBidder) {
        CollectionUtils.emptyIfNull(extBidder.getKeywords()).forEach(keyword -> {
            if (CollectionUtils.isEmpty(keyword.getValue())) {
                return;
            }
            keywords.put(keyword.getKey(), String.join(",", keyword.getValue()));
        });
        final JsonNode pmZoneIdKeyWords = keywords.remove(PM_ZONE_ID_OLD_KEY_NAME);
        final String pmZomeId = extBidder.getPmZoneId();
        if (StringUtils.isNotEmpty(pmZomeId)) {
            keywords.put(PM_ZONE_ID_KEY_NAME, extBidder.getPmZoneId());
        } else if (pmZoneIdKeyWords != null) {
            keywords.set(PM_ZONE_ID_KEY_NAME, pmZoneIdKeyWords);
        }

        final String dctr = extBidder.getDctr();
        if (StringUtils.isNotEmpty(dctr)) {
            keywords.put(DCTR_KEY_NAME, dctr);
        }
    }

    private static void putExtDataKeywords(ObjectNode keywords, PubmaticExtData extData) {
        final String pbaAdSlot = extData.getPbAdSlot();
        final PubmaticExtDataAdServer extAdServer = extData.getAdServer();
        final String adSeverName = extAdServer != null ? extAdServer.getName() : null;
        final String adSeverAdSlot = extAdServer != null ? extAdServer.getAdSlot() : null;
        if (AD_SERVER_GAM.equals(adSeverName) && StringUtils.isNotEmpty(adSeverAdSlot)) {
            keywords.put(IMP_EXT_AD_UNIT_KEY, adSeverAdSlot);
        } else if (StringUtils.isNotEmpty(pbaAdSlot)) {
            keywords.put(IMP_EXT_AD_UNIT_KEY, pbaAdSlot);
        }
    }

    private HttpRequest<BidRequest> createRequest(BidRequest request,
                                                  List<Imp> imps,
                                                  String pubId,
                                                  ObjectNode wrapper) {
        final BidRequest.BidRequestBuilder requestBuilder = request.toBuilder().imp(imps);

        if (wrapper != null) {
            requestBuilder.ext(
                    mapper.fillExtension(ExtRequest.empty(),
                            mapper.mapper().createObjectNode().set("wrapper", wrapper)));
        }

        if (pubId != null) {
            updateRequestWithPubIdParam(requestBuilder, request.getSite(), request.getApp(), pubId);
        }

        final BidRequest modifiedRequest = requestBuilder.build();
        final String body = mapper.encode(modifiedRequest);

        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .body(body)
                .headers(HttpUtil.headers())
                .payload(modifiedRequest)
                .build();
    }

    private static void updateRequestWithPubIdParam(BidRequest.BidRequestBuilder requestBuilder,
                                                    Site site,
                                                    App app,
                                                    String pubId) {
        if (site != null) {
            modifySite(pubId, site, requestBuilder);
        } else if (app != null) {
            modifyApp(pubId, app, requestBuilder);
        }
    }

    private static void modifySite(String pubId, Site site, BidRequest.BidRequestBuilder bidRequestBuilder) {
        if (site.getPublisher() != null) {
            final Publisher modifiedPublisher = site.getPublisher().toBuilder().id(pubId).build();
            bidRequestBuilder.site(site.toBuilder().publisher(modifiedPublisher).build());
        } else {
            bidRequestBuilder.site(site.toBuilder().publisher(Publisher.builder().id(pubId).build()).build());
        }
    }

    private static void modifyApp(String pubId, App app, BidRequest.BidRequestBuilder bidRequestBuilder) {
        if (app.getPublisher() != null) {
            final Publisher modifiedPublisher = app.getPublisher().toBuilder().id(pubId).build();
            bidRequestBuilder.app(app.toBuilder().publisher(modifiedPublisher).build());
        } else {
            bidRequestBuilder.app(app.toBuilder().publisher(Publisher.builder().id(pubId).build()).build());
        }
    }

    @Override
    public final Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(bidResponse), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse) {
        return bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())
                ? Collections.emptyList()
                : bidsFromResponse(bidResponse);
    }

    private List<BidderBid> bidsFromResponse(BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> resolveBidderBid(bid, bidResponse.getCur()))
                .collect(Collectors.toList());
    }

    private BidderBid resolveBidderBid(Bid bid, String currency) {
        final List<String> singleElementBidCat = CollectionUtils.emptyIfNull(bid.getCat()).stream()
                .limit(1)
                .collect(Collectors.collectingAndThen(Collectors.toList(),
                        bidCat -> !bidCat.isEmpty() ? bidCat : null));

        final PubmaticBidExt pubmaticBidExt = extractBidExt(bid.getExt());
        final Integer duration = getDuration(pubmaticBidExt);
        final Bid updatedBid = singleElementBidCat != null || duration != null
                ? bid.toBuilder()
                .cat(singleElementBidCat)
                .ext(duration != null ? updateBidExtWithExtPrebid(duration, bid.getExt()) : bid.getExt())
                .build()
                : bid;

        return BidderBid.of(updatedBid, getBidType(pubmaticBidExt), currency);
    }

    private PubmaticBidExt extractBidExt(ObjectNode bidExt) {
        try {
            return bidExt != null ? mapper.mapper().treeToValue(bidExt, PubmaticBidExt.class) : null;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static BidType getBidType(PubmaticBidExt bidExt) {
        final Integer bidType = bidExt != null
                ? ObjectUtils.defaultIfNull(bidExt.getBidType(), 0)
                : 0;

        switch (bidType) {
            case 1:
                return BidType.video;
            case 2:
                return BidType.xNative;
            default:
                return BidType.banner;
        }
    }

    private static Integer getDuration(PubmaticBidExt bidExt) {
        final VideoCreativeInfo creativeInfo = bidExt != null ? bidExt.getVideo() : null;
        return creativeInfo != null ? creativeInfo.getDuration() : null;
    }

    private ObjectNode updateBidExtWithExtPrebid(Integer duration, ObjectNode extBid) {
        final ExtBidPrebid extBidPrebid = ExtBidPrebid.builder().video(ExtBidPrebidVideo.of(duration, null)).build();
        return extBid.set(PREBID, mapper.mapper().valueToTree(extBidPrebid));
    }
}
