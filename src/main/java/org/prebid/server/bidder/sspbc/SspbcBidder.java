package org.prebid.server.bidder.sspbc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.sspbc.request.SspbcRequestType;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.sspbc.ExtImpSspbc;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ObjectUtil;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public class SspbcBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpSspbc>> SSPBC_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final String ADAPTER_VERSION = "5.8";
    private static final String IMP_FALLBACK_SIZE = "1x1";
    private static final String PREBID_SERVER_INTEGRATION_TYPE = "4";
    private static final String BANNER_TEMPLATE = """
            <html><head><title></title><meta charset="UTF-8"><meta name="viewport"\
             content="width=device-width, initial-scale=1.0"><style> body { background-color:\
             transparent; margin: 0; padding: 0; }</style><script> window.rekid = {{.SiteId}};\
             window.slot = {{.SlotId}}; window.adlabel = '{{.AdLabel}}'; window.pubid = '{{.PubId}}';\
             window.wp_sn = 'sspbc_go'; window.page = '{{.Page}}'; window.ref = '{{.Referer}}';\
             window.mcad = {{.McAd}}; window.inver = '{{.Inver}}'; </script></head><body><div id="c"></div><script\
             async crossorigin nomodule src="//std.wpcdn.pl/wpjslib/wpjslib-inline.js"\
             id="wpjslib"></script><script async crossorigin type="module"\
             src="//std.wpcdn.pl/wpjslib6/wpjslib-inline.js" id="wpjslib6"></script></body></html>""";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public SspbcBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        if (request.getSite() == null) {
            return Result.withError(BidderError.badInput("BidRequest.site not provided"));
        }

        final Map<Imp, ExtImpSspbc> impToExt;

        try {
            impToExt = createImpToExt(request);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        final SspbcRequestType requestType = getRequestType(impToExt);
        final List<Imp> imps = new ArrayList<>();
        String siteId = "";

        for (Imp imp : request.getImp()) {
            final ExtImpSspbc extImpSspbc = impToExt.get(imp);
            final String extImpSspbcSiteId = extImpSspbc.getSiteId();
            if (StringUtils.isNotEmpty(extImpSspbcSiteId)) {
                siteId = extImpSspbcSiteId;
            }

            imps.add(updateImp(imp, requestType, extImpSspbc));
        }

        try {
            return Result.withValue(createRequest(updateBidRequest(request, imps, requestType, siteId)));
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }
    }

    private Map<Imp, ExtImpSspbc> createImpToExt(BidRequest request) {
        return request.getImp()
                .stream()
                .collect(Collectors.toMap(Function.identity(), this::parseImpExt));
    }

    private ExtImpSspbc parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), SSPBC_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private Imp updateImp(Imp imp, SspbcRequestType requestType, ExtImpSspbc extImpSspbc) {
        final String originalImpId = imp.getId();

        return imp.toBuilder()
                .id(resolveImpId(originalImpId, extImpSspbc.getId(), requestType))
                .tagid(originalImpId)
                .ext(createImpExt(imp))
                .build();
    }

    private String resolveImpId(String originalImpId, String extImpId, SspbcRequestType requestType) {
        return StringUtils.isNotEmpty(extImpId) && requestType != SspbcRequestType.REQUEST_TYPE_ONE_CODE
                ? extImpId
                : originalImpId;
    }

    private ObjectNode createImpExt(Imp imp) {
        return mapper.mapper().createObjectNode()
                .set("data", mapper.mapper().createObjectNode()
                        .put("pbslot", imp.getTagid())
                        .put("pbsize", getImpSize(imp)));
    }

    private BidRequest updateBidRequest(BidRequest bidRequest,
                                        List<Imp> imps,
                                        SspbcRequestType requestType,
                                        String siteId) {

        return bidRequest.toBuilder()
                .imp(imps)
                .site(updateSite(bidRequest.getSite(), requestType, siteId))
                .test(updateTest(requestType, bidRequest.getTest()))
                .build();
    }

    private Site updateSite(Site site, SspbcRequestType requestType, String siteId) {
        return site.toBuilder()
                .id(resolveSiteId(requestType, siteId))
                .domain(getUri(site.getPage()).getHost())
                .build();
    }

    private static String resolveSiteId(SspbcRequestType requestType, String siteId) {
        return requestType == SspbcRequestType.REQUEST_TYPE_ONE_CODE || StringUtils.isBlank(siteId)
                ? StringUtils.EMPTY
                : siteId;
    }

    private static Integer updateTest(SspbcRequestType requestType, Integer test) {
        return requestType == SspbcRequestType.REQUEST_TYPE_TEST ? 1 : test;
    }

    private String getImpSize(Imp imp) {
        final List<Format> formats = ObjectUtil.getIfNotNull(imp.getBanner(), Banner::getFormat);
        if (CollectionUtils.isEmpty(formats)) {
            return IMP_FALLBACK_SIZE;
        }

        return formats.stream()
                .max(Comparator.comparing(SspbcBidder::formatToArea))
                .filter(format -> formatToArea(format) > 0)
                .map(format -> String.format("%dx%d", format.getW(), format.getH()))
                .orElse(IMP_FALLBACK_SIZE);
    }

    private static int formatToArea(Format format) {
        final Integer w = ObjectUtil.getIfNotNull(format, Format::getW);
        final Integer h = ObjectUtil.getIfNotNull(format, Format::getH);

        return w != null && h != null ? w * h : 0;
    }

    private SspbcRequestType getRequestType(Map<Imp, ExtImpSspbc> impToExt) {
        for (ExtImpSspbc extImpSspbc : impToExt.values()) {
            if (extImpSspbc.getTest() != 0) {
                return SspbcRequestType.REQUEST_TYPE_TEST;
            }

            if (StringUtils.isAnyEmpty(extImpSspbc.getSiteId(), extImpSspbc.getId())) {
                return SspbcRequestType.REQUEST_TYPE_ONE_CODE;
            }
        }

        return SspbcRequestType.REQUEST_TYPE_STANDARD;
    }

    private HttpRequest<BidRequest> createRequest(BidRequest request) {
        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(updateUrl(getUri(endpointUrl)))
                .headers(HttpUtil.headers())
                .body(mapper.encodeToBytes(request))
                .payload(request)
                .build();
    }

    private static URIBuilder getUri(String endpointUrl) {
        final URIBuilder uri;
        try {
            uri = new URIBuilder(endpointUrl);
        } catch (URISyntaxException e) {
            throw new PreBidException("Malformed URL: %s.".formatted(endpointUrl));
        }
        return uri;
    }

    private String updateUrl(URIBuilder uriBuilder) {
        return uriBuilder
                .addParameter("bdver", ADAPTER_VERSION)
                .addParameter("inver", PREBID_SERVER_INTEGRATION_TYPE)
                .toString();
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidResponse, httpCall.getRequest().getPayload()));
        } catch (PreBidException | DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse, BidRequest bidRequest) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(seatBid -> CollectionUtils.emptyIfNull(seatBid.getBid())
                        .stream()
                        .filter(Objects::nonNull)
                        .map(bid -> toBidderBid(bid, seatBid.getSeat(), bidResponse.getCur(), bidRequest)))
                .flatMap(UnaryOperator.identity())
                .toList();
    }

    private BidderBid toBidderBid(Bid bid, String seat, String currency, BidRequest bidRequest) {
        if (StringUtils.isEmpty(bid.getAdm())) {
            throw new PreBidException("Bid format is not supported");
        }

        final ObjectNode bidExt = bid.getExt();
        final Bid updatedBid = bid.toBuilder()
                .impid(getImpTagId(bidRequest.getImp(), bid))
                .adm(createBannerAd(
                        bid,
                        stringOrNull(bidExt, "adlabel"),
                        stringOrNull(bidExt, "pubid"),
                        stringOrNull(bidExt, "siteid"),
                        stringOrNull(bidExt, "slotid"),
                        seat,
                        bidRequest))
                .build();

        return BidderBid.of(updatedBid, BidType.banner, currency);
    }

    private static String stringOrNull(ObjectNode bidExt, String property) {
        return Optional.ofNullable(bidExt)
                .map(ext -> ext.get(property))
                .map(JsonNode::asText)
                .orElse(StringUtils.EMPTY);
    }

    private static String getImpTagId(List<Imp> imps, Bid bid) {
        return imps.stream()
                .filter(imp -> Objects.equals(imp.getId(), bid.getImpid()))
                .map(Imp::getTagid)
                .findAny()
                .orElseThrow(() -> new PreBidException("imp not found"));
    }

    private String createBannerAd(Bid bid,
                                  String adlabel,
                                  String pubid,
                                  String siteid,
                                  String slotid,
                                  String seat,
                                  BidRequest bidRequest) {
        if (bid.getAdm().contains("<!--preformatted-->")) {
            return bid.getAdm();
        }

        final ObjectNode mcad = mapper.mapper().createObjectNode()
                .put("id", bidRequest.getId())
                .put("seat", seat)
                .set("seatbid", mapper.mapper()
                        .convertValue(SeatBid.builder().bid(Collections.singletonList(bid)).build(), JsonNode.class));

        return BANNER_TEMPLATE
                .replace("{{.SiteId}}", siteid)
                .replace("{{.SlotId}}", slotid)
                .replace("{{.AdLabel}}", adlabel)
                .replace("{{.PubId}}", pubid)
                .replace("{{.Page}}", bidRequest.getSite().getPage())
                .replace("{{.Referer}}", bidRequest.getSite().getRef())
                .replace("{{.McAd}}", mcad.toString())
                .replace("{{.Inver}}", PREBID_SERVER_INTEGRATION_TYPE);
    }
}
