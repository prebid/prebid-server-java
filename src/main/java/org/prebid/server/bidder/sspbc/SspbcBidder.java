package org.prebid.server.bidder.sspbc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.sspbc.ExtImpSspbc;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class SspbcBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpSspbc>> SSPBC_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final String ADAPTER_VERSION = "5.8";
    private static final String IMP_FALLBACK_SIZE = "1x1";
    private static final Integer REQUEST_TYPE_STANDARD = 1;
    private static final Integer REQUEST_TYPE_ONE_CODE = 2;
    private static final Integer REQUEST_TYPE_TEST = 3;
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

        final Integer requestType;

        try {
            requestType = getRequestType(request);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        final List<Imp> imps = new ArrayList<>();
        String siteId = "";

        for (Imp imp : request.getImp()) {
            final ExtImpSspbc extImpSspbc = parseImpExt(imp);
            if (StringUtils.isNoneEmpty(extImpSspbc.getSiteId())) {
                siteId = extImpSspbc.getSiteId();
            }

            imps.add(updateImp(imp, requestType, extImpSspbc));
        }

        final HttpRequest<BidRequest> bidRequestHttpRequest;

        try {
            bidRequestHttpRequest = createRequest(updateBidRequest(request, imps, requestType, siteId));
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        return Result.withValue(bidRequestHttpRequest);
    }

    private Imp updateImp(Imp imp, Integer requestType, ExtImpSspbc extImpSspbc) {
        final Imp.ImpBuilder impBuilder = Imp.builder();

        if (StringUtils.isNotEmpty(extImpSspbc.getId()) && !Objects.equals(requestType, REQUEST_TYPE_ONE_CODE)) {
            impBuilder.id(extImpSspbc.getId());
        }

        final ObjectNode impExt = mapper.mapper().createObjectNode()
                .set("data", mapper.mapper().createObjectNode()
                        .put("pbslot", imp.getTagid())
                        .put("pbsize", getImpSize(imp)));

        return impBuilder
                .tagid(imp.getId())
                .ext(impExt)
                .build();
    }

    private BidRequest updateBidRequest(BidRequest bidRequest,
                                        List<Imp> imps,
                                        Integer requestType,
                                        String siteId) {
        return bidRequest.toBuilder()
                .imp(imps)
                .site(updateSite(bidRequest.getSite(), requestType, siteId))
                .test(updateTest(requestType, bidRequest.getTest()))
                .build();
    }

    private Site updateSite(Site site, Integer requestType, String siteId) {
        final Site.SiteBuilder siteBuilder = site.toBuilder();
        if (Objects.equals(requestType, REQUEST_TYPE_ONE_CODE) || StringUtils.isBlank(siteId)) {
            siteBuilder.id(StringUtils.EMPTY).build();
        } else {
            siteBuilder.id(siteId).build();
        }

        final URIBuilder uriBuilder = getUri(site.getPage());
        siteBuilder.domain(uriBuilder.getHost());

        return siteBuilder.build();
    }

    private static Integer updateTest(Integer requestType, Integer test) {
        return Objects.equals(requestType, REQUEST_TYPE_TEST) ? 1 : test;
    }

    private String getImpSize(Imp imp) {
        if (imp.getBanner() == null || imp.getBanner().getFormat().size() == 0) {
            return IMP_FALLBACK_SIZE;
        }

        long areaMax = 0L;
        String impSize = IMP_FALLBACK_SIZE;

        for (Format format : imp.getBanner().getFormat()) {
            int area = format.getW() * format.getH();
            if (area > areaMax) {
                areaMax = area;
                impSize = String.format("%dx%d", format.getW(), format.getH());
            }
        }

        return impSize;
    }

    private Integer getRequestType(BidRequest request) {
        int incompleteImps = 0;

        for (Imp imp : request.getImp()) {
            final ExtImpSspbc extImpSspbc = parseImpExt(imp);
            if (extImpSspbc.getTest() != 0) {
                return REQUEST_TYPE_TEST;
            }

            if (StringUtils.isEmpty(extImpSspbc.getSiteId()) || StringUtils.isEmpty(extImpSspbc.getId())) {
                incompleteImps += 1;
            }
        }

        if (incompleteImps > 0) {
            return REQUEST_TYPE_ONE_CODE;
        }

        return REQUEST_TYPE_STANDARD;
    }

    private ExtImpSspbc parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), SSPBC_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
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

    private URIBuilder getUri(String endpointUrl) {
        final URI uri;
        try {
            uri = new URI(endpointUrl);
        } catch (URISyntaxException e) {
            throw new PreBidException("Malformed URL: %s.".formatted(endpointUrl));
        }
        return new URIBuilder(uri);
    }

    private String updateUrl(URIBuilder uriBuilder) {
        return uriBuilder
                .addParameter("bdver", ADAPTER_VERSION)
                .addParameter("inver", PREBID_SERVER_INTEGRATION_TYPE)
                .toString();
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            final List<BidderBid> bidderBids = extractBids(bidResponse, httpCall.getRequest().getPayload());
            return Result.withValues(bidderBids);
        } catch (PreBidException | DecodeException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
        }
        return Result.withErrors(errors);
    }

    private List<BidderBid> extractBids(BidResponse bidResponse, BidRequest bidRequest) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return resolveBidderBid(bidResponse, bidRequest);
    }

    private List<BidderBid> resolveBidderBid(BidResponse bidResponse, BidRequest bidRequest) {
        final List<BidderBid> bidderBidList = new ArrayList<>();

        for (SeatBid seatBid : bidResponse.getSeatbid()) {
            for (Bid bid : seatBid.getBid()) {
                BidType bidType = null;

                if (StringUtils.isNotEmpty(bid.getAdm())) {
                    bidType = BidType.banner;
                }

                final Bid.BidBuilder bidBuilder = bid.toBuilder();
                bidBuilder.impid(getImpTagId(bidRequest.getImp(), bid));

                final ObjectNode bidExt = bid.getExt();
                final String adlabel = bidExt.get("adlabel").asText();
                final String pubid = bidExt.get("pubid").asText();
                final String siteid = bidExt.get("siteid").asText();
                final String slotid = bidExt.get("slotid").asText();

                if (bidType != BidType.banner) {
                    throw new PreBidException("Bid format is not supported");
                }

                bidBuilder.adm(createBannerAd(bid, adlabel, pubid, siteid, slotid, seatBid.getSeat(), bidRequest));

                bidderBidList.add(BidderBid.of(bidBuilder.build(), bidType, bidResponse.getCur()));
            }
        }
        return bidderBidList;
    }

    private static String getImpTagId(List<Imp> imps, Bid bid) {
        return imps.stream()
                .filter(imp -> imp.getId().equals(bid.getImpid()))
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
