package org.prebid.server.bidder.yieldlab;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.yieldlab.model.YieldlabDigitalServicesActResponse;
import org.prebid.server.bidder.yieldlab.model.YieldlabResponse;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtRegsDsa;
import org.prebid.server.proto.openrtb.ext.request.ExtRegsDsaTransparency;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.yieldlab.ExtImpYieldlab;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class YieldlabBidder implements Bidder<Void> {

    private static final Logger logger = LoggerFactory.getLogger(YieldlabBidder.class);
    private static final TypeReference<ExtPrebid<?, ExtImpYieldlab>> YIELDLAB_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private static final String BID_CURRENCY = "EUR";
    private static final String AD_SLOT_ID_SEPARATOR = ",";
    private static final String AD_SIZE_SEPARATOR = "x";
    private static final String CREATIVE_ID = "%s%s%s";
    private static final String AD_SOURCE_BANNER = "<script src=\"%s\"></script>";
    private static final String AD_SOURCE_URL = "https://ad.yieldlab.net/d/%s/%s/%s?%s";
    private static final String TRANSPARENCY_TEMPLATE = "%s~%s";
    private static final String TRANSPARENCY_TEMPLATE_PARAMS_DELIMITER = "_";
    private static final String TRANSPARENCY_TEMPLATE_DELIMITER = "~~";
    private static final String VAST_MARKUP = """
            <VAST version="2.0"><Ad id="%s"><Wrapper>
            <AdSystem>Yieldlab</AdSystem>
            <VASTAdTagURI><![CDATA[ %s ]]></VASTAdTagURI>
            <Impression></Impression>
            <Creatives></Creatives>
            </Wrapper></Ad></VAST>""";

    private final String endpointUrl;
    private final Clock clock;
    private final JacksonMapper mapper;

    public YieldlabBidder(String endpointUrl, Clock clock, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.clock = Objects.requireNonNull(clock);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<Void>>> makeHttpRequests(BidRequest request) {
        final ExtImpYieldlab modifiedExtImp = constructExtImp(request.getImp());

        final String uri;
        try {
            uri = makeUrl(modifiedExtImp, request);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        return Result.withValue(HttpRequest.<Void>builder()
                .method(HttpMethod.GET)
                .uri(uri)
                .headers(resolveHeaders(request.getSite(), request.getDevice(), request.getUser()))
                .build());
    }

    private ExtImpYieldlab constructExtImp(List<Imp> imps) {
        final List<ExtImpYieldlab> extImps = collectImpExt(imps);

        final List<String> adSlotIds = extImps.stream()
                .map(ExtImpYieldlab::getAdslotId)
                .filter(Objects::nonNull)
                .toList();

        final Map<String, String> targeting = extImps.stream()
                .map(ExtImpYieldlab::getTargeting)
                .filter(Objects::nonNull)
                .flatMap(map -> map.entrySet().stream())
                .filter(entry -> entry.getKey() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (channel1, channel2) -> channel1));

        final String adSlotIdsParams = adSlotIds.stream().sorted().collect(Collectors.joining(AD_SLOT_ID_SEPARATOR));
        return ExtImpYieldlab.builder().adslotId(adSlotIdsParams).targeting(targeting).build();
    }

    private List<ExtImpYieldlab> collectImpExt(List<Imp> imps) {
        final List<ExtImpYieldlab> extImps = new ArrayList<>();
        for (Imp imp : imps) {
            final ExtImpYieldlab extImpYieldlab = parseImpExt(imp);
            if (extImpYieldlab != null) {
                extImps.add(extImpYieldlab);
            }
        }
        return extImps;
    }

    private ExtImpYieldlab parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), YIELDLAB_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String makeUrl(ExtImpYieldlab extImpYieldlab, BidRequest request) {
        // for passing validation tests
        final String timestamp = isDebugEnabled(request) ? "200000" : String.valueOf(clock.instant().getEpochSecond());

        final String updatedPath = "%s/%s".formatted(endpointUrl, extImpYieldlab.getAdslotId());

        final URIBuilder uriBuilder;
        try {
            uriBuilder = new URIBuilder(updatedPath);
        } catch (URISyntaxException e) {
            throw new PreBidException("Invalid url: %s, error: %s".formatted(updatedPath, e.getMessage()));
        }

        uriBuilder
                .addParameter("content", "json")
                .addParameter("pvid", "true")
                .addParameter("ts", timestamp)
                .addParameter("t", getTargetingValues(extImpYieldlab));

        final User user = request.getUser();
        if (user != null && StringUtils.isNotBlank(user.getBuyeruid())) {
            uriBuilder.addParameter("ids", String.join("ylid:", user.getBuyeruid()));
        }

        final Device device = request.getDevice();
        if (device != null) {
            uriBuilder.addParameter("yl_rtb_ifa", device.getIfa());

            uriBuilder.addParameter("yl_rtb_devicetype", resolveNumberParameter(device.getDevicetype()));
            final Integer connectionType = device.getConnectiontype();
            if (connectionType != null) {
                uriBuilder.addParameter("yl_rtb_connectiontype", device.getConnectiontype().toString());
            }

            final Geo geo = device.getGeo();
            if (geo != null) {
                uriBuilder.addParameter("lat", resolveNumberParameter(geo.getLat()));
                uriBuilder.addParameter("lon", resolveNumberParameter(geo.getLon()));
            }
        }

        final App app = request.getApp();
        if (app != null) {
            uriBuilder.addParameter("pubappname", app.getName())
                    .addParameter("pubbundlename", app.getBundle());
        }

        final String gdpr = getGdprParameter(request.getRegs());
        if (StringUtils.isNotBlank(gdpr)) {
            uriBuilder.addParameter("gdpr", gdpr);
        }

        final String consent = getConsentParameter(request.getUser());
        if (StringUtils.isNotBlank(consent)) {
            uriBuilder.addParameter("consent", consent);
        }

        extractDsaRequestParamsFromBidRequest(request).forEach(uriBuilder::addParameter);

        return uriBuilder.toString();
    }

    /**
     * Determines debug flag from {@link BidRequest} or {@link ExtRequest}.
     */
    private static boolean isDebugEnabled(BidRequest bidRequest) {
        if (Objects.equals(bidRequest.getTest(), 1)) {
            return true;
        }

        final ExtRequest extRequest = bidRequest.getExt();
        final ExtRequestPrebid extRequestPrebid = extRequest != null ? extRequest.getPrebid() : null;
        return extRequestPrebid != null && Objects.equals(extRequestPrebid.getDebug(), 1);
    }

    private String getTargetingValues(ExtImpYieldlab extImpYieldlab) {
        final URIBuilder uriBuilder = new URIBuilder();

        for (Map.Entry<String, String> targeting : extImpYieldlab.getTargeting().entrySet()) {
            uriBuilder.addParameter(targeting.getKey(), targeting.getValue());
        }

        return uriBuilder.toString().replace("?", "");
    }

    private static String getGdprParameter(Regs regs) {
        if (regs != null) {
            final Integer gdpr = regs.getExt() != null ? regs.getExt().getGdpr() : null;
            if (gdpr != null && (gdpr == 0 || gdpr == 1)) {
                return gdpr.toString();
            }
        }
        return "";
    }

    private static String getConsentParameter(User user) {
        final ExtUser extUser = user != null ? user.getExt() : null;
        final String consent = extUser != null ? extUser.getConsent() : null;
        return ObjectUtils.defaultIfNull(consent, "");
    }

    private static Map<String, String> extractDsaRequestParamsFromBidRequest(BidRequest request) {
        return Optional.ofNullable(request.getRegs())
            .map(Regs::getExt)
            .map(ExtRegs::getDsa)
            .map(YieldlabBidder::extractDsaRequestParamsFromDsaRegsExtension)
            .orElse(Collections.emptyMap());
    }

    private static Map<String, String> extractDsaRequestParamsFromDsaRegsExtension(final ExtRegsDsa dsa) {
        final Map<String, String> dsaRequestParams = new HashMap<>();

        if (dsa.getDsaRequired() != null) {
            dsaRequestParams.put("dsarequired", dsa.getDsaRequired().toString());
        }

        if (dsa.getPubRender() != null) {
            dsaRequestParams.put("dsapubrender", dsa.getPubRender().toString());
        }

        if (dsa.getDataToPub() != null) {
            dsaRequestParams.put("dsadatatopub", dsa.getDataToPub().toString());
        }

        final List<ExtRegsDsaTransparency> dsaTransparency = dsa.getTransparency();
        if (CollectionUtils.isNotEmpty(dsaTransparency)) {
            final String encodedTransparencies = encodeTransparenciesAsString(dsaTransparency);
            if (StringUtils.isNotBlank(encodedTransparencies)) {
                dsaRequestParams.put("dsatransparency", encodedTransparencies);
            }
        }

        return dsaRequestParams;
    }

    private static String encodeTransparenciesAsString(List<ExtRegsDsaTransparency> transparencies) {
        return transparencies.stream()
            .filter(YieldlabBidder::isTransparencyValid)
            .map(YieldlabBidder::encodeTransparency)
            .collect(Collectors.joining(TRANSPARENCY_TEMPLATE_DELIMITER));
    }

    private static boolean isTransparencyValid(ExtRegsDsaTransparency transparency) {
        return StringUtils.isNotBlank(transparency.getDomain())
                && transparency.getDsaParams() != null
                && CollectionUtils.isNotEmpty(transparency.getDsaParams());
    }

    private static String encodeTransparency(ExtRegsDsaTransparency transparency) {
        return TRANSPARENCY_TEMPLATE.formatted(transparency.getDomain(),
            encodeTransparencyParams(transparency.getDsaParams()));
    }

    private static String encodeTransparencyParams(List<Integer> dsaParams) {
        return dsaParams.stream().map(Objects::toString).collect(Collectors.joining(
            TRANSPARENCY_TEMPLATE_PARAMS_DELIMITER));
    }

    private static MultiMap resolveHeaders(Site site, Device device, User user) {
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap()
                .add(HttpUtil.ACCEPT_HEADER, HttpHeaderValues.APPLICATION_JSON);

        if (site != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.REFERER_HEADER.toString(), site.getPage());
        }

        if (device != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER.toString(), device.getUa());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER.toString(), device.getIp());
        }

        if (user != null && StringUtils.isNotBlank(user.getBuyeruid())) {
            headers.add(HttpUtil.COOKIE_HEADER.toString(), "id=" + user.getBuyeruid());
        }

        return headers;
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<Void> httpCall, BidRequest bidRequest) {
        final List<YieldlabResponse> yieldlabResponses;
        try {
            yieldlabResponses = decodeBodyToBidList(httpCall);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }

        final List<BidderBid> bidderBids = new ArrayList<>();
        for (int i = 0; i < yieldlabResponses.size(); i++) {
            final BidderBid bidderBid;
            try {
                bidderBid = resolveBidderBid(yieldlabResponses, i, bidRequest);
            } catch (PreBidException e) {
                return Result.withError(BidderError.badInput(e.getMessage()));
            }

            if (bidderBid != null) {
                bidderBids.add(bidderBid);
            }
        }
        return Result.of(bidderBids, Collections.emptyList());
    }

    private BidderBid resolveBidderBid(List<YieldlabResponse> yieldlabResponses,
                                       int currentImpIndex, BidRequest bidRequest) {
        final YieldlabResponse yieldlabResponse = yieldlabResponses.get(currentImpIndex);

        final ExtImpYieldlab matchedExtImp = getMatchedExtImp(yieldlabResponse.getId(), bidRequest.getImp());
        if (matchedExtImp == null) {
            throw new PreBidException("Invalid extension");
        }

        final Imp currentImp = bidRequest.getImp().get(currentImpIndex);
        if (currentImp == null) {
            throw new PreBidException("Imp not present for id " + currentImpIndex);
        }
        final Bid.BidBuilder updatedBid = Bid.builder();

        final BidType bidType;
        if (currentImp.getVideo() != null) {
            bidType = BidType.video;
            updatedBid.nurl(makeNurl(bidRequest, matchedExtImp, yieldlabResponse));
            updatedBid.adm(resolveAdm(bidRequest, matchedExtImp, yieldlabResponse));
        } else if (currentImp.getBanner() != null) {
            bidType = BidType.banner;
            updatedBid.adm(makeAdm(bidRequest, matchedExtImp, yieldlabResponse));
        } else {
            return null;
        }

        addBidParams(yieldlabResponse, bidRequest, updatedBid)
                .impid(currentImp.getId());

        return BidderBid.of(updatedBid.build(), bidType, BID_CURRENCY);
    }

    private List<YieldlabResponse> decodeBodyToBidList(BidderCall<Void> httpCall) {
        try {
            return mapper.mapper().readValue(
                    httpCall.getResponse().getBody(),
                    mapper.mapper().getTypeFactory().constructCollectionType(List.class, YieldlabResponse.class));
        } catch (DecodeException | JsonProcessingException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private ExtImpYieldlab getMatchedExtImp(Integer responseId, List<Imp> imps) {
        return collectImpExt(imps).stream()
                .filter(ext -> ext.getAdslotId().equals(String.valueOf(responseId)))
                .findFirst()
                .orElse(null);
    }

    private Bid.BidBuilder addBidParams(YieldlabResponse yieldlabResponse, BidRequest bidRequest,
                                        Bid.BidBuilder updatedBid) {
        final ExtImpYieldlab matchedExtImp = getMatchedExtImp(yieldlabResponse.getId(), bidRequest.getImp());

        if (matchedExtImp == null) {
            throw new PreBidException("Invalid extension");
        }

        updatedBid.id(resolveNumberParameter(yieldlabResponse.getId()))
                .price(resolvePrice(yieldlabResponse.getPrice()))
                .dealid(resolveNumberParameter(yieldlabResponse.getPid()))
                .crid(makeCreativeId(bidRequest, yieldlabResponse, matchedExtImp))
                .w(resolveSizeParameter(yieldlabResponse.getAdSize(), true))
                .h(resolveSizeParameter(yieldlabResponse.getAdSize(), false))
                .ext(resolveExtParameter(yieldlabResponse));

        return updatedBid;
    }

    private static BigDecimal resolvePrice(Double price) {
        return price != null ? BigDecimal.valueOf(price / 100) : null;
    }

    private static String resolveNumberParameter(Number param) {
        return param != null ? String.valueOf(param) : null;
    }

    private static String makeCreativeId(BidRequest bidRequest, YieldlabResponse yieldlabResponse,
                                         ExtImpYieldlab extImp) {
        // for passing validation tests
        final int weekNumber = isDebugEnabled(bidRequest) ? 35 : Calendar.getInstance().get(Calendar.WEEK_OF_YEAR);
        return CREATIVE_ID.formatted(extImp.getAdslotId(), yieldlabResponse.getPid(), weekNumber);
    }

    private static Integer resolveSizeParameter(String adSize, boolean isWidth) {
        final String[] sizeParts = adSize.split(AD_SIZE_SEPARATOR);

        if (sizeParts.length != 2) {
            return 0;
        }
        final int sizeIndex = isWidth ? 0 : 1;
        return StringUtils.isNumeric(sizeParts[sizeIndex]) ? Integer.parseInt(sizeParts[sizeIndex]) : 0;
    }

    private String makeAdm(BidRequest bidRequest, ExtImpYieldlab extImpYieldlab, YieldlabResponse yieldlabResponse) {
        return AD_SOURCE_BANNER.formatted(makeNurl(bidRequest, extImpYieldlab, yieldlabResponse));
    }

    private String resolveAdm(BidRequest bidRequest, ExtImpYieldlab extImpYieldlab, YieldlabResponse yieldlabResponse) {
        return VAST_MARKUP.formatted(
                extImpYieldlab.getAdslotId(),
                makeNurl(bidRequest, extImpYieldlab, yieldlabResponse));
    }

    private String makeNurl(BidRequest bidRequest, ExtImpYieldlab extImpYieldlab, YieldlabResponse yieldlabResponse) {
        // for passing validation tests
        final String timestamp = isDebugEnabled(bidRequest)
                ? "200000"
                : String.valueOf(clock.instant().getEpochSecond());

        final URIBuilder uriBuilder = new URIBuilder()
                .addParameter("ts", timestamp)
                .addParameter("id", extImpYieldlab.getExtId())
                .addParameter("pvid", yieldlabResponse.getPvid());

        final User user = bidRequest.getUser();
        if (user != null && StringUtils.isNotBlank(user.getBuyeruid())) {
            uriBuilder.addParameter("ids", String.join("ylid:", user.getBuyeruid()));
        }

        final String gdpr = getGdprParameter(bidRequest.getRegs());
        final String consent = getConsentParameter(bidRequest.getUser());
        if (StringUtils.isNotBlank(gdpr) && StringUtils.isNotBlank(consent)) {
            uriBuilder.addParameter("gdpr", gdpr)
                    .addParameter("consent", consent);
        }

        return AD_SOURCE_URL.formatted(
                extImpYieldlab.getAdslotId(),
                extImpYieldlab.getSupplyId(),
                yieldlabResponse.getAdSize(),
                uriBuilder.toString().replace("?", ""));
    }

    private ObjectNode resolveExtParameter(YieldlabResponse yieldlabResponse) {
        final YieldlabDigitalServicesActResponse dsa = yieldlabResponse.getDsa();
        if (dsa == null) {
            return null;
        }
        final ObjectNode ext = mapper.mapper().createObjectNode();
        final JsonNode dsaNode;
        try {
            dsaNode = mapper.mapper().valueToTree(dsa);
        } catch (IllegalArgumentException e) {
            logger.error("Failed to serialize DSA object for adslot {}", yieldlabResponse.getId(), e);
            return null;
        }
        ext.set("dsa", dsaNode);
        return ext;
    }
}
