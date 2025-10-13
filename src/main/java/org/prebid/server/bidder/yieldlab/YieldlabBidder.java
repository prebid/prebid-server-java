package org.prebid.server.bidder.yieldlab;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
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
import io.vertx.uritemplate.UriTemplate;
import io.vertx.uritemplate.Variables;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.yieldlab.model.YieldlabBid;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.DsaTransparency;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtRegsDsa;
import org.prebid.server.proto.openrtb.ext.request.ExtSource;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.yieldlab.ExtImpYieldlab;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidDsa;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
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

    private static final TypeReference<ExtPrebid<?, ExtImpYieldlab>> YIELDLAB_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private static final TypeReference<List<YieldlabBid>> YIELDLAB_BID_TYPE_REFERENCE =
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
        final UriTemplate uriTemplate = UriTemplate.of(endpointUrl + "{/adslotId}{?queryParams*}");
        final Map<String, String> queryParams = new HashMap<>();

        queryParams.put("content", "json");
        queryParams.put("pvid", "true");
        queryParams.put("ts", resolveNumberParameter(clock.instant().getEpochSecond()));
        queryParams.put("t", getTargetingValues(extImpYieldlab));

        final String formats = makeFormats(request, extImps);

        if (formats != null) {
            queryParams.put("sizes", formats);
        }

        final User user = request.getUser();
        if (user != null && StringUtils.isNotBlank(user.getBuyeruid())) {
            queryParams.put("ids", "ylid:" + StringUtils.defaultString(user.getBuyeruid()));
        }

        final Device device = request.getDevice();
        if (device != null) {
            addUriParameterIfNotBlank(queryParams, "yl_rtb_ifa", device.getIfa());
            addUriParameterIfNotBlank(queryParams, "yl_rtb_devicetype", resolveNumberParameter(device.getDevicetype()));
            final Integer connectionType = device.getConnectiontype();
            if (connectionType != null) {
                queryParams.put("yl_rtb_connectiontype", device.getConnectiontype().toString());
            }

            final Geo geo = device.getGeo();
            if (geo != null) {
                addUriParameterIfNotBlank(queryParams, "lat", ObjectUtils.defaultIfNull(geo.getLat(), 0f).toString());
                addUriParameterIfNotBlank(queryParams, "lon", ObjectUtils.defaultIfNull(geo.getLon(), 0f).toString());
            }
        }

        final App app = request.getApp();
        if (app != null) {
            addUriParameterIfNotBlank(queryParams, "pubappname", app.getName());
            addUriParameterIfNotBlank(queryParams, "pubbundlename", app.getBundle());

        }
        addUriParameterIfNotBlank(queryParams, "gdpr", getGdprParameter(request.getRegs()));
        addUriParameterIfNotBlank(queryParams, "gdpr_consent", getConsentParameter(request.getUser()));
        addUriParameterIfNotBlank(queryParams, "schain", getSchainParameter(request.getSource()));
        queryParams.putAll(extractDsaRequestParamsFromBidRequest(request));

        return uriTemplate.expandToString(Variables.variables()
                .set("adslotId", extImpYieldlab.getAdslotId())
                .set("queryParams", queryParams));
    }

    private static void addUriParameterIfNotBlank(Map<String, String> queryParams, String parameter, String value) {
        if (StringUtils.isNotBlank(value)) {
            queryParams.put(parameter, value);
        }
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

    private String getTargetingValues(ExtImpYieldlab extImpYieldlab) {
        return UriTemplate.of("{?queryParams*}")
                .expandToString(Variables.variables().set("queryParams", extImpYieldlab.getTargeting()))
                .replace("?", StringUtils.EMPTY);
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
        final List<BidderError> errors = new ArrayList<>();
        try {
            final List<YieldlabBid> yieldlabBids = mapper.decodeValue(
                    httpCall.getResponse().getBody(),
                    YIELDLAB_BID_TYPE_REFERENCE);
            return Result.of(extractBids(bidRequest, yieldlabBids, errors), errors);
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidRequest bidRequest,
                                        List<YieldlabBid> yieldlabBids,
                                        List<BidderError> errors) {

        if (CollectionUtils.isEmpty(yieldlabBids)) {
            return Collections.emptyList();
        }

        final Map<String, Pair<Imp, ExtImpYieldlab>> adSlotMap = new HashMap<>();
        for (Imp imp : bidRequest.getImp()) {
            final ExtImpYieldlab extImpYieldlab = parseImpExt(imp);
            if (extImpYieldlab != null) {
                adSlotMap.put(extImpYieldlab.getAdslotId(), Pair.of(imp, extImpYieldlab));
            }
        }

        return yieldlabBids.stream()
                .filter(Objects::nonNull)
                .map(bid -> makeBid(bidRequest, bid, adSlotMap, errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private BidderBid makeBid(BidRequest bidRequest,
                              YieldlabBid yieldlabBid,
                              Map<String, Pair<Imp, ExtImpYieldlab>> adSlotMap,
                              List<BidderError> errors) {

        final String adSlotId = resolveNumberParameter(yieldlabBid.getId());
        final Pair<Imp, ExtImpYieldlab> impPair = adSlotMap.get(adSlotId);

        if (impPair == null) {
            throw new PreBidException(("failed to find yieldlab request for adslotID %d. "
                    + "This is most likely a programming issue").formatted(yieldlabBid.getId()));
        }

        final Imp imp = impPair.getKey();
        final ExtImpYieldlab extImp = impPair.getValue();
        final BidType bidType = resolveBidType(imp);

        if (bidType == null) {
            return null;
        }

        final Format adsize = resolveAdSize(yieldlabBid.getAdSize());
        final String advertiser = yieldlabBid.getAdvertiser();
        final Bid bid = Bid.builder()
                .id(adSlotId)
                .price(BigDecimal.valueOf(yieldlabBid.getPrice() / 100))
                .impid(imp.getId())
                .crid(makeCreativeId(yieldlabBid, adSlotId))
                .dealid(resolveNumberParameter(yieldlabBid.getPid()))
                .nurl(bidType == BidType.video ? makeNurl(bidRequest, extImp, yieldlabBid) : null)
                .adm(bidType == BidType.video
                        ? makeVast(bidRequest, extImp, yieldlabBid)
                        : makeBanner(bidRequest, extImp, yieldlabBid))
                .w(adsize.getW())
                .h(adsize.getH())
                .adomain(advertiser != null ? Collections.singletonList(advertiser) : null)
                .ext(resolveBidExt(yieldlabBid, errors))
                .build();

        return BidderBid.of(bid, bidType, BID_CURRENCY);
    }

    private static BidType resolveBidType(Imp imp) {
        if (imp.getVideo() != null) {
            return BidType.video;
        } else if (imp.getBanner() != null) {
            return BidType.banner;
        } else {
            return null;
        }
    }

    private static Format resolveAdSize(String adsize) {
        if (adsize == null) {
            return Format.builder().w(0).h(0).build();
        }

        final String[] sizes = adsize.split(AD_SIZE_SEPARATOR);
        if (sizes.length != 2) {
            return Format.builder().w(0).h(0).build();
        }

        try {
            return Format.builder()
                    .w(Integer.parseUnsignedInt(sizes[0], 10))
                    .h(Integer.parseUnsignedInt(sizes[1], 10))
                    .build();
        } catch (NumberFormatException e) {
            throw new PreBidException("failed to parse yieldlab adsize");
        }
    }

    private static String makeCreativeId(YieldlabBid yieldlabBid, String adSlotId) {
        return CREATIVE_ID.formatted(adSlotId, yieldlabBid.getPid(), Calendar.getInstance().get(Calendar.WEEK_OF_YEAR));
    }

    private String makeBanner(BidRequest bidRequest, ExtImpYieldlab extImp, YieldlabBid yieldlabBid) {
        return AD_SOURCE_BANNER.formatted(makeNurl(bidRequest, extImp, yieldlabBid));
    }

    private String makeVast(BidRequest bidRequest, ExtImpYieldlab extImp, YieldlabBid yieldlabBid) {
        return VAST_MARKUP.formatted(extImp.getAdslotId(), makeNurl(bidRequest, extImp, yieldlabBid));
    }

    private String makeNurl(BidRequest bidRequest, ExtImpYieldlab extImp, YieldlabBid yieldlabBid) {
        final Map<String, String> queryParams = new HashMap<>();
        queryParams.put("ts", resolveNumberParameter(clock.instant().getEpochSecond()));
        queryParams.put("id", extImp.getExtId());
        queryParams.put("pvid", yieldlabBid.getPvid());

        final User user = bidRequest.getUser();
        if (user != null && StringUtils.isNotBlank(user.getBuyeruid())) {
            queryParams.put("ids", "ylid:" + StringUtils.defaultString(user.getBuyeruid()));
        }

        final String gdpr = getGdprParameter(bidRequest.getRegs());
        final String consent = getConsentParameter(bidRequest.getUser());
        if (StringUtils.isNotBlank(gdpr) && StringUtils.isNotBlank(consent)) {
            queryParams.put("gdpr", gdpr);
            queryParams.put("gdpr_consent", consent);
        }

        final List<String> pathSegments = List.of(extImp.getAdslotId(), extImp.getSupplyId(), yieldlabBid.getAdSize());
        return UriTemplate.of("https://ad.yieldlab.net/d{/path*}{?queryParams*}")
                .expandToString(Variables.variables().set("path", pathSegments).set("queryParams", queryParams));
    }

    private ObjectNode resolveBidExt(YieldlabBid bid, List<BidderError> errors) {
        final ExtBidDsa dsa = bid.getDsa();
        if (dsa == null) {
            return null;
        }
        final ObjectNode ext = mapper.mapper().createObjectNode();
        final JsonNode dsaNode;
        try {
            dsaNode = mapper.mapper().valueToTree(dsa);
        } catch (IllegalArgumentException e) {
            errors.add(BidderError.badServerResponse(
                    "Failed to serialize DSA object for adslot %d".formatted(bid.getId())));
            return null;
        }
        ext.set("dsa", dsaNode);
        return ext;
    }

    private static String resolveNumberParameter(Number param) {
        return param != null ? String.valueOf(param) : null;
    }
}
