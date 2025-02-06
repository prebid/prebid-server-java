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
import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.SupplyChain;
import com.iab.openrtb.request.SupplyChainNode;
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
import org.prebid.server.proto.openrtb.ext.request.DsaTransparency;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtRegsDsa;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtSource;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.yieldlab.ExtImpYieldlab;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
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
        final Map<String, ExtImpYieldlab> extImps = collectImpExt(request.getImp());
        final ExtImpYieldlab modifiedExtImp = mergeExtImps(extImps.values());

        final String uri;
        try {
            uri = makeUrl(modifiedExtImp, request, extImps);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        return Result.withValue(HttpRequest.<Void>builder()
                .method(HttpMethod.GET)
                .uri(uri)
                .impIds(BidderUtil.impIds(request))
                .headers(resolveHeaders(request.getSite(), request.getDevice(), request.getUser()))
                .build());
    }

    private static ExtImpYieldlab mergeExtImps(Collection<ExtImpYieldlab> extImps) {
        final String adSlotIdsParams = extImps.stream()
                .map(ExtImpYieldlab::getAdslotId)
                .map(StringUtils::defaultString)
                .collect(Collectors.joining(AD_SLOT_ID_SEPARATOR));

        final Map<String, String> targeting = new HashMap<>();
        extImps.stream()
                .map(ExtImpYieldlab::getTargeting)
                .filter(Objects::nonNull)
                .forEach(targeting::putAll);

        return ExtImpYieldlab.builder().adslotId(adSlotIdsParams).targeting(targeting).build();
    }

    private Map<String, ExtImpYieldlab> collectImpExt(List<Imp> imps) {
        final Map<String, ExtImpYieldlab> extImps = new HashMap<>();
        for (Imp imp : imps) {
            final ExtImpYieldlab extImpYieldlab = parseImpExt(imp);
            if (extImpYieldlab != null) {
                extImps.put(imp.getId(), extImpYieldlab);
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

    private String makeUrl(ExtImpYieldlab extImpYieldlab, BidRequest request, Map<String, ExtImpYieldlab> extImps) {
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

        final String formats = makeFormats(request, extImps);

        if (formats != null) {
            uriBuilder.addParameter("sizes", formats);
        }

        final User user = request.getUser();
        if (user != null && StringUtils.isNotBlank(user.getBuyeruid())) {
            uriBuilder.addParameter("ids", "ylid:" + StringUtils.defaultString(user.getBuyeruid()));
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
                uriBuilder.addParameter("lat", ObjectUtils.defaultIfNull(geo.getLat(), 0f).toString());
                uriBuilder.addParameter("lon", ObjectUtils.defaultIfNull(geo.getLon(), 0f).toString());
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
            uriBuilder.addParameter("gdpr_consent", consent);
        }

        final String schain = getSchainParameter(request.getSource());
        if (schain != null) {
            uriBuilder.addParameter("schain", schain);
        }

        extractDsaRequestParamsFromBidRequest(request).forEach(uriBuilder::addParameter);

        return uriBuilder.toString();
    }

    private String makeFormats(BidRequest request, Map<String, ExtImpYieldlab> extImps) {
        final List<String> formats = new LinkedList<>();
        for (Imp imp: request.getImp()) {
            if (!isBanner(imp)) {
                continue;
            }
            final ExtImpYieldlab extImp = extImps.get(imp.getId());
            if (extImp == null) {
                continue;
            }

            final String formatsPerAdSlotString = CollectionUtils.emptyIfNull(imp.getBanner().getFormat()).stream()
                    .map(format -> "%dx%d".formatted(format.getW(), format.getH()))
                    .collect(Collectors.joining("|"));

            formats.add("%s:%s".formatted(extImp.getAdslotId(), formatsPerAdSlotString));
        }

        return formats.isEmpty() ? null : String.join(",", formats);
    }

    private boolean isBanner(Imp imp) {
        return imp.getBanner() != null && imp.getXNative() == null && imp.getVideo() == null && imp.getAudio() == null;
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
        return Optional.ofNullable(regs)
                .map(Regs::getExt)
                .map(ExtRegs::getGdpr)
                .filter(gdpr -> gdpr == 0 || gdpr == 1)
                .map(Object::toString)
                .orElse(StringUtils.EMPTY);
    }

    private static String getConsentParameter(User user) {
        return Optional.ofNullable(user)
                .map(User::getExt)
                .map(ExtUser::getConsent)
                .orElse(StringUtils.EMPTY);
    }

    private String getSchainParameter(Source source) {
        return Optional.ofNullable(source)
                .map(Source::getExt)
                .map(ExtSource::getSchain)
                .map(this::resolveSupplyChain)
                .orElse(null);
    }

    private String resolveSupplyChain(SupplyChain schain) {
        final List<SupplyChainNode> nodes = schain.getNodes();
        if (CollectionUtils.isEmpty(nodes)) {
            return null;
        }

        final StringBuilder schainBuilder = new StringBuilder();

        schainBuilder.append(schain.getVer());
        schainBuilder.append(",");
        schainBuilder.append(ObjectUtils.defaultIfNull(schain.getComplete(), 0));
        for (SupplyChainNode node : schain.getNodes()) {
            schainBuilder.append("!");
            schainBuilder.append(encodeValue(node.getAsi()));
            schainBuilder.append(",");

            schainBuilder.append(encodeValue(node.getSid()));
            schainBuilder.append(",");

            schainBuilder.append(node.getHp() == null ? StringUtils.EMPTY : node.getHp());
            schainBuilder.append(",");

            schainBuilder.append(encodeValue(node.getRid()));
            schainBuilder.append(",");

            schainBuilder.append(encodeValue(node.getName()));
            schainBuilder.append(",");

            schainBuilder.append(encodeValue(node.getDomain()));
            schainBuilder.append(",");

            schainBuilder.append(node.getExt() == null
                    ? StringUtils.EMPTY
                    : HttpUtil.encodeUrl(mapper.encodeToString(node.getExt())));
        }

        return schainBuilder.toString();
    }

    private static String encodeValue(String value) {
        return value == null ? StringUtils.EMPTY : HttpUtil.encodeUrl(value);
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

        final List<DsaTransparency> dsaTransparency = dsa.getTransparency();
        if (CollectionUtils.isNotEmpty(dsaTransparency)) {
            final String encodedTransparencies = encodeTransparenciesAsString(dsaTransparency);
            if (StringUtils.isNotBlank(encodedTransparencies)) {
                dsaRequestParams.put("dsatransparency", encodedTransparencies);
            }
        }

        return dsaRequestParams;
    }

    private static String encodeTransparenciesAsString(List<DsaTransparency> transparencies) {
        return transparencies.stream()
                .map(YieldlabBidder::encodeTransparency)
                .collect(Collectors.joining(TRANSPARENCY_TEMPLATE_DELIMITER));
    }

    private static String encodeTransparency(DsaTransparency transparency) {
        final String domain = transparency.getDomain();
        if (StringUtils.isBlank(domain)) {
            return StringUtils.EMPTY;
        }

        final List<Integer> dsaParams = transparency.getDsaParams();
        if (CollectionUtils.isEmpty(dsaParams)) {
            return domain;
        }

        return TRANSPARENCY_TEMPLATE.formatted(domain, encodeTransparencyParams(dsaParams));
    }

    private static String encodeTransparencyParams(List<Integer> dsaParams) {
        return dsaParams.stream()
                .map(param -> ObjectUtils.defaultIfNull(param, 0))
                .map(Object::toString)
                .collect(Collectors.joining(TRANSPARENCY_TEMPLATE_PARAMS_DELIMITER));
    }

    private static MultiMap resolveHeaders(Site site, Device device, User user) {
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap()
                .add(HttpUtil.ACCEPT_HEADER, HttpHeaderValues.APPLICATION_JSON);

        if (site != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.REFERER_HEADER, site.getPage());
        }

        if (device != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER, device.getUa());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIp());
        }

        final String buyerUid = user != null ? user.getBuyeruid() : null;
        if (StringUtils.isNotBlank(buyerUid)) {
            headers.add(HttpUtil.COOKIE_HEADER, "id=" + buyerUid);
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

        final Collection<ExtImpYieldlab> extImpYieldlabs = collectImpExt(bidRequest.getImp()).values();
        final List<BidderBid> bidderBids = new ArrayList<>();
        for (int i = 0; i < yieldlabResponses.size(); i++) {
            final BidderBid bidderBid;
            try {
                bidderBid = resolveBidderBid(yieldlabResponses, i, bidRequest, extImpYieldlabs);
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
                                       int currentImpIndex,
                                       BidRequest bidRequest,
                                       Collection<ExtImpYieldlab> extImpYieldlabs) {

        final YieldlabResponse yieldlabResponse = yieldlabResponses.get(currentImpIndex);

        final ExtImpYieldlab matchedExtImp = getMatchedExtImp(yieldlabResponse.getId(), extImpYieldlabs);
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

        addBidParams(yieldlabResponse, bidRequest, updatedBid, extImpYieldlabs)
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

    private ExtImpYieldlab getMatchedExtImp(Integer responseId, Collection<ExtImpYieldlab> extImpYieldlabs) {
        return extImpYieldlabs.stream()
                .filter(ext -> ext.getAdslotId().equals(String.valueOf(responseId)))
                .findFirst()
                .orElse(null);
    }

    private Bid.BidBuilder addBidParams(YieldlabResponse yieldlabResponse,
                                        BidRequest bidRequest,
                                        Bid.BidBuilder updatedBid,
                                        Collection<ExtImpYieldlab> extImpYieldlabs) {

        final ExtImpYieldlab matchedExtImp = getMatchedExtImp(yieldlabResponse.getId(), extImpYieldlabs);

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
            uriBuilder.addParameter("ids", "ylid:" + StringUtils.defaultString(user.getBuyeruid()));
        }

        final String gdpr = getGdprParameter(bidRequest.getRegs());
        final String consent = getConsentParameter(bidRequest.getUser());
        if (StringUtils.isNotBlank(gdpr) && StringUtils.isNotBlank(consent)) {
            uriBuilder.addParameter("gdpr", gdpr)
                    .addParameter("gdpr_consent", consent);
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
