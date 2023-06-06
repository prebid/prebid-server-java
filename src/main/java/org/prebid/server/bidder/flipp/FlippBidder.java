package org.prebid.server.bidder.flipp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.flipp.model.request.CampaignRequestBody;
import org.prebid.server.bidder.flipp.model.request.CampaignRequestBodyUser;
import org.prebid.server.bidder.flipp.model.request.Placement;
import org.prebid.server.bidder.flipp.model.request.PrebidRequest;
import org.prebid.server.bidder.flipp.model.request.Properties;
import org.prebid.server.bidder.flipp.model.response.CampaignResponseBody;
import org.prebid.server.bidder.flipp.model.response.Content;
import org.prebid.server.bidder.flipp.model.response.Data;
import org.prebid.server.bidder.flipp.model.response.Decisions;
import org.prebid.server.bidder.flipp.model.response.Inline;
import org.prebid.server.bidder.flipp.model.response.Prebid;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.flipp.ExtImpFlipp;
import org.prebid.server.proto.openrtb.ext.request.flipp.ExtImpFlippOptions;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ObjectUtil;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

public class FlippBidder implements Bidder<CampaignRequestBody> {

    private static final String INLINE_DIV_NAME = "inline";
    private static final Integer COUNT = 1;
    private static final String CREATIVE_TYPE = "DTX";
    private static final Set<Integer> AD_TYPES = Set.of(4309, 641);
    private static final Set<Integer> DTX_TYPES = Set.of(5061);
    private static final TypeReference<ExtPrebid<?, ExtImpFlipp>> FLIPP_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public FlippBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<CampaignRequestBody>>> makeHttpRequests(BidRequest bidRequest) {
        final List<HttpRequest<CampaignRequestBody>> httpRequests = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        for (Imp imp : bidRequest.getImp()) {
            final CampaignRequestBody.CampaignRequestBodyBuilder campaignRequestBody = CampaignRequestBody.builder();
            final ExtImpFlipp extImpFlipp;

            try {
                extImpFlipp = parseImpExt(imp);
                campaignRequestBody.ip(resolveIp(bidRequest, extImpFlipp));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
                continue;
            }

            httpRequests.add(createRequest(bidRequest,
                    updateCampaignRequestBody(bidRequest, imp, campaignRequestBody, extImpFlipp)));
        }

        if (CollectionUtils.isEmpty(httpRequests)) {
            errors.add(BidderError.badInput("Adapter request is empty"));
            return Result.withErrors(errors);
        }

        return Result.of(httpRequests, errors);
    }

    private static Placement createPlacement(BidRequest bidRequest, Imp imp, ExtImpFlipp extImpFlipp) {
        return Placement.builder()
                .divName(INLINE_DIV_NAME)
                .siteId(extImpFlipp.getSiteId())
                .adTypes(Objects.equals(extImpFlipp.getCreativeType(), CREATIVE_TYPE) ? DTX_TYPES : AD_TYPES)
                .zoneIds(extImpFlipp.getZoneIds())
                .count(COUNT)
                .prebid(createPrebidRequest(extImpFlipp, imp))
                .properties(Properties.of(resolveContentCode(bidRequest, extImpFlipp)))
                .options(extImpFlipp.getOptions())
                .build();
    }

    private ExtImpFlipp parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), FLIPP_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Flipp params not found. " + e.getMessage());
        }
    }

    private static PrebidRequest createPrebidRequest(ExtImpFlipp extImpFlipp, Imp imp) {
        final PrebidRequest.PrebidRequestBuilder prebidRequest = PrebidRequest.builder()
                .creativeType(extImpFlipp.getCreativeType())
                .publisherNameIdentifier(extImpFlipp.getPublisherNameIdentifier())
                .requestId(imp.getId());

        if (CollectionUtils.isNotEmpty(ObjectUtil.getIfNotNull(imp.getBanner(), Banner::getFormat))) {
            final Format format = imp.getBanner().getFormat().get(0);
            prebidRequest.height(format.getH());
            prebidRequest.width(format.getW());
        }

        return prebidRequest.build();
    }

    private static List<String> resolveKeywords(BidRequest bidRequest) {
        return Optional.ofNullable(bidRequest.getUser())
                .map(User::getKeywords)
                .map(a -> a.split(","))
                .map(Arrays::asList)
                .orElse(null);
    }

    private static String resolveContentCode(BidRequest bidRequest, ExtImpFlipp extImpFlipp) {
        final String contentCode =
                ObjectUtil.getIfNotNull(extImpFlipp.getOptions(), ExtImpFlippOptions::getContentCode);
        if (StringUtils.isNotEmpty(contentCode)) {
            return contentCode;
        }

        final String pageUrl = Optional.ofNullable(bidRequest.getSite())
                .map(Site::getPage)
                .orElse(null);

        return URLEncodedUtils.parse(pageUrl, StandardCharsets.UTF_8)
                .stream()
                .filter(Objects::nonNull)
                .filter(nameValuePair -> nameValuePair.getName().contains("flipp-content-code"))
                .map(NameValuePair::getValue)
                .findFirst()
                .orElse(null);
    }

    private static String resolveIp(BidRequest bidRequest, ExtImpFlipp extImpFlipp) {
        return Optional.ofNullable(extImpFlipp.getIp())
                .filter(StringUtils::isNotEmpty)
                .orElseGet(() -> resolveIpFromDevice(bidRequest.getDevice()));
    }

    private static String resolveIpFromDevice(Device device) {
        return Optional.ofNullable(device)
                .map(Device::getIp)
                .filter(StringUtils::isNoneEmpty)
                .orElseThrow(() -> new PreBidException("No IP set in Flipp bidder params or request device"));
    }

    private static String resolveKey(BidRequest bidRequest, ExtImpFlipp extImpFlipp) {
        return Optional.ofNullable(bidRequest.getUser())
                .map(User::getId)
                .filter(StringUtils::isNotEmpty)
                .orElseGet(() -> extractUserKey(extImpFlipp));
    }

    private static String extractUserKey(ExtImpFlipp extImpFlipp) {
        return Optional.ofNullable(extImpFlipp.getUserKey())
                .filter(StringUtils::isNotEmpty)
                .orElseGet(() -> UUID.randomUUID().toString());
    }

    private HttpRequest<CampaignRequestBody> createRequest(BidRequest bidRequest,
                                                           CampaignRequestBody campaignRequestBody) {
        return HttpRequest.<CampaignRequestBody>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .headers(makeHeaders(bidRequest.getDevice()))
                .body(mapper.encodeToBytes(campaignRequestBody))
                .payload(campaignRequestBody)
                .build();
    }

    private static MultiMap makeHeaders(Device device) {
        return Optional.of(device)
                .map(Device::getUa)
                .map(ua -> HttpUtil.headers().add(HttpUtil.USER_AGENT_HEADER, ua))
                .orElseGet(HttpUtil::headers);
    }

    private static CampaignRequestBody updateCampaignRequestBody(
            BidRequest bidRequest, Imp imp, CampaignRequestBody.CampaignRequestBodyBuilder campaignRequestBody,
            ExtImpFlipp extImpFlipp) {

        return campaignRequestBody
                .placements(Collections.singletonList(createPlacement(bidRequest, imp, extImpFlipp)))
                .url(ObjectUtil.getIfNotNull(bidRequest.getSite(), Site::getPage))
                .keywords(resolveKeywords(bidRequest))
                .user(CampaignRequestBodyUser.of(resolveKey(bidRequest, extImpFlipp)))
                .build();
    }

    @Override
    public final Result<List<BidderBid>> makeBids(BidderCall<CampaignRequestBody> httpCall, BidRequest bidRequest) {
        try {
            final CampaignResponseBody campaignResponseBody =
                    mapper.decodeValue(httpCall.getResponse().getBody(), CampaignResponseBody.class);
            return Result.withValues(extractInline(campaignResponseBody, bidRequest));
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractInline(CampaignResponseBody campaignResponseBody, BidRequest bidRequest) {
        final List<Inline> inlineList = Optional.ofNullable(campaignResponseBody)
                .map(CampaignResponseBody::getDecisions)
                .map(Decisions::getInline)
                .orElse(Collections.emptyList());

        return inlineList.stream()
                .map(inline -> bidFromInline(inline, bidRequest))
                .filter(Objects::nonNull)
                .toList();
    }

    private static BidderBid bidFromInline(Inline inlines, BidRequest bidRequest) {
        for (Imp imp : bidRequest.getImp()) {
            return Optional.ofNullable(inlines)
                    .map(Inline::getPrebid)
                    .map(Prebid::getRequestId)
                    .filter(requestId -> Objects.equals(requestId, imp.getId()))
                    .map(regId -> BidderBid.of(constructBid(inlines, imp.getId()), BidType.banner, "USD"))
                    .orElse(null);
        }
    }

    private static Bid constructBid(Inline inline, String impId) {
        final Prebid prebid = inline.getPrebid();

        return Bid.builder()
                .crid(String.valueOf(inline.getCreativeId()))
                .price(prebid.getCpm())
                .adm(prebid.getCreative())
                .id(String.valueOf(inline.getAdId()))
                .impid(impId)
                .w(resolveWidth(inline))
                .h(CollectionUtils.isNotEmpty(inline.getContents()) ? 0 : null)
                .build();
    }

    private static Integer resolveWidth(Inline inline) {
        return Optional.of(inline)
                .map(Inline::getContents)
                .map(content -> content.get(0))
                .map(Content::getData)
                .map(Data::getWidth)
                .orElse(null);
    }
}
