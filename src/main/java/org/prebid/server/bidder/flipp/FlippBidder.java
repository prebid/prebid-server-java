package org.prebid.server.bidder.flipp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import com.iabtcf.decoder.TCString;
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
import org.prebid.server.privacy.gdpr.vendorlist.proto.PurposeCode;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
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

public class FlippBidder implements Bidder<CampaignRequestBody> {

    private static final TypeReference<ExtPrebid<?, ExtImpFlipp>> FLIPP_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private static final String INLINE_DIV_NAME = "inline";
    private static final Integer COUNT = 1;
    private static final String CREATIVE_TYPE = "DTX";
    private static final Set<Integer> AD_TYPES = Set.of(4309, 641);
    private static final Set<Integer> DTX_TYPES = Set.of(5061);
    private static final String EXT_REQUEST_TRANSMIT_EIDS = "transmitEids";

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
            try {
                final ExtImpFlipp extImp = parseImpExt(imp);
                final CampaignRequestBody campaignRequest = makeCampaignRequest(bidRequest, imp, extImp);
                httpRequests.add(makeHttpRequest(bidRequest.getDevice().getUa(), campaignRequest));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (CollectionUtils.isEmpty(httpRequests)) {
            errors.add(BidderError.badInput("Adapter request is empty"));
            return Result.withErrors(errors);
        }

        return Result.of(httpRequests, errors);
    }

    private ExtImpFlipp parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), FLIPP_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Flipp params not found. " + e.getMessage());
        }
    }

    private static CampaignRequestBody makeCampaignRequest(BidRequest bidRequest, Imp imp, ExtImpFlipp extImp) {
        return CampaignRequestBody.builder()
                .ip(resolveIpFromDevice(bidRequest.getDevice()))
                .placements(Collections.singletonList(createPlacement(bidRequest, imp, extImp)))
                .url(ObjectUtil.getIfNotNull(bidRequest.getSite(), Site::getPage))
                .keywords(resolveKeywords(bidRequest))
                .user(CampaignRequestBodyUser.of(resolveKey(bidRequest, extImp)))
                .build();
    }

    private static String resolveIpFromDevice(Device device) {
        return Optional.ofNullable(device)
                .map(Device::getIp)
                .filter(StringUtils::isNotEmpty)
                .orElseThrow(() -> new PreBidException("No IP set in Flipp bidder params or request device"));
    }

    private static Placement createPlacement(BidRequest bidRequest, Imp imp, ExtImpFlipp extImp) {
        return Placement.builder()
                .divName(INLINE_DIV_NAME)
                .siteId(extImp.getSiteId())
                .adTypes(CREATIVE_TYPE.equals(extImp.getCreativeType()) ? DTX_TYPES : AD_TYPES)
                .zoneIds(extImp.getZoneIds())
                .count(COUNT)
                .prebid(createPrebidRequest(imp, extImp))
                .properties(Properties.of(resolveContentCode(bidRequest.getSite(), extImp)))
                .options(extImp.getOptions())
                .build();
    }

    private static PrebidRequest createPrebidRequest(Imp imp, ExtImpFlipp extImp) {
        final Format format = Optional.ofNullable(imp.getBanner())
                .map(Banner::getFormat)
                .filter(CollectionUtils::isNotEmpty)
                .map(formats -> formats.getFirst())
                .orElse(null);

        return PrebidRequest.builder()
                .requestId(imp.getId())
                .creativeType(extImp.getCreativeType())
                .publisherNameIdentifier(extImp.getPublisherNameIdentifier())
                .height(format != null ? format.getH() : null)
                .width(format != null ? format.getW() : null)
                .build();
    }

    private static String resolveContentCode(Site site, ExtImpFlipp extImp) {
        final String contentCode = ObjectUtil.getIfNotNull(extImp.getOptions(), ExtImpFlippOptions::getContentCode);
        if (StringUtils.isNotEmpty(contentCode)) {
            return contentCode;
        }

        final String pageUrl = Optional.ofNullable(site)
                .map(Site::getPage)
                .orElse(null);

        return URLEncodedUtils.parse(pageUrl, StandardCharsets.UTF_8)
                .stream()
                .filter(nameValuePair -> nameValuePair.getName().contains("flipp-content-code"))
                .map(NameValuePair::getValue)
                .findFirst()
                .orElse(null);
    }

    private static List<String> resolveKeywords(BidRequest bidRequest) {
        return Optional.ofNullable(bidRequest.getSite())
                .map(Site::getKeywords)
                .map(a -> a.split(","))
                .map(Arrays::asList)
                .orElse(null);
    }

    private static String resolveKey(BidRequest bidRequest, ExtImpFlipp extImp) {
        return keyFromUser(bidRequest.getUser())
                .or(() -> keyFromExt(bidRequest, extImp))
                .orElseGet(() -> UUID.randomUUID().toString());
    }

    private static Optional<String> keyFromUser(User user) {
        return Optional.ofNullable(user)
                .map(User::getId)
                .filter(StringUtils::isNotEmpty);
    }

    private static Optional<String> keyFromExt(BidRequest bidRequest, ExtImpFlipp extImpFlipp) {
        return Optional.ofNullable(extImpFlipp.getUserKey())
                .filter(userKey -> StringUtils.isNotEmpty(userKey) && isUserKeyPermitted(bidRequest));
    }

    private static boolean isUserKeyPermitted(BidRequest request) {
        final Regs regs = request.getRegs();
        return !restrictedByCoppa(regs)
                && !restrictedByGdpr(regs)
                && !restrictedByExtConfig(request.getExt())
                && !restrictedByTcf(request.getUser());
    }

    private static boolean restrictedByCoppa(Regs regs) {
        return Optional.ofNullable(regs)
                .map(Regs::getCoppa)
                .orElse(0) == 1;
    }

    private static boolean restrictedByGdpr(Regs regs) {
        return Optional.ofNullable(regs)
                .map(Regs::getGdpr)
                .orElse(0) == 1;
    }

    private static boolean restrictedByExtConfig(ExtRequest extRequest) {
        return Optional.ofNullable(extRequest)
                .map(ext -> ext.getProperty(EXT_REQUEST_TRANSMIT_EIDS))
                .filter(JsonNode::isBoolean)
                .map(node -> !node.booleanValue())
                .orElse(false);
    }

    private static boolean restrictedByTcf(User user) {
        return Optional.ofNullable(user)
                .map(User::getConsent)
                .filter(StringUtils::isNotBlank)
                .map(FlippBidder::decode)
                .map(TCString::getPurposesConsent)
                .map(purposesAllowed -> !purposesAllowed.contains(PurposeCode.FOUR.code()))
                .orElse(false);
    }

    private static TCString decode(String consent) {
        try {
            return TCString.decode(consent);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private HttpRequest<CampaignRequestBody> makeHttpRequest(String userAgent, CampaignRequestBody campaignRequest) {
        return HttpRequest.<CampaignRequestBody>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .headers(makeHeaders(userAgent))
                .body(mapper.encodeToBytes(campaignRequest))
                .payload(campaignRequest)
                .build();
    }

    private static MultiMap makeHeaders(String userAgent) {
        final MultiMap headers = HttpUtil.headers();
        HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER, userAgent);
        return headers;
    }

    @Override
    public final Result<List<BidderBid>> makeBids(BidderCall<CampaignRequestBody> httpCall, BidRequest bidRequest) {
        try {
            final CampaignResponseBody campaignResponseBody =
                    mapper.decodeValue(httpCall.getResponse().getBody(), CampaignResponseBody.class);
            return Result.withValues(extractBids(campaignResponseBody, bidRequest));
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(CampaignResponseBody campaignResponseBody, BidRequest bidRequest) {
        return Optional.ofNullable(campaignResponseBody)
                .map(CampaignResponseBody::getDecisions)
                .map(Decisions::getInline)
                .stream()
                .flatMap(Collection::stream)
                .filter(inline -> isInlineValid(bidRequest, inline))
                .map(inline -> BidderBid.of(constructBid(inline), BidType.banner, "USD"))
                .toList();
    }

    private static boolean isInlineValid(BidRequest bidRequest, Inline inline) {
        final String requestId = Optional.ofNullable(inline)
                .map(Inline::getPrebid)
                .map(Prebid::getRequestId)
                .orElse(null);

        return requestId != null && bidRequest.getImp().stream()
                .map(Imp::getId)
                .anyMatch(impId -> impId.equals(requestId));
    }

    private static Bid constructBid(Inline inline) {
        final Prebid prebid = inline.getPrebid();
        final Data data = Optional.ofNullable(inline.getContents())
                .map(content -> content.getFirst())
                .map(Content::getData)
                .orElse(null);

        return Bid.builder()
                .crid(Integer.toString(inline.getCreativeId()))
                .price(prebid.getCpm())
                .adm(prebid.getCreative())
                .id(Integer.toString(inline.getAdId()))
                .impid(prebid.getRequestId())
                .w(data != null ? data.getWidth() : null)
                .h(data != null ? 0 : null)
                .build();
    }
}
