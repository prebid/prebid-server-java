package org.prebid.server.bidder.adgeneration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.adgeneration.model.AdgenerationResponse;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.adgeneration.ExtImpAdgeneration;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * AdgenerationBidder {@link Bidder} implementation.
 */
public class AdgenerationBidder implements Bidder<BidRequest> {
    private static final TypeReference<ExtPrebid<?, ExtImpAdgeneration>> ADGENERATION_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpAdgeneration>>() {
            };
    private static final String VERSION = "1.0.0";
    private static final String DEFAULT_REQUEST_CURRENCY = "JPY";
    private static final String DEFAULT_BID_CURRENCY = "USD";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public AdgenerationBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<HttpRequest<BidRequest>> result = new ArrayList<>();

        if (CollectionUtils.isEmpty(request.getImp())) {
            return Result.emptyWithError(BidderError.badInput("No impression in the bid request"));
        }

        for (Imp imp : request.getImp()) {
            try {
                final ExtImpAdgeneration extImpAdgeneration = parseAndValidateImpExt(imp);
                final String uri = getUri(endpointUrl, getAdSize(imp), extImpAdgeneration.getId(),
                        getCurrency(request), request.getSite());
                result.add(createSingleRequest(imp, request, uri));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.of(result, errors);
    }

    private ExtImpAdgeneration parseAndValidateImpExt(Imp imp) {
        final ExtImpAdgeneration extImpAdgeneration;
        try {
            extImpAdgeneration = mapper.mapper().convertValue(imp.getExt(), ADGENERATION_EXT_TYPE_REFERENCE)
                    .getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }

        if (StringUtils.isBlank(extImpAdgeneration.getId())) {
            throw new PreBidException("No Location ID in ExtImpAdgeneration.");
        }
        return extImpAdgeneration;
    }

    private String getUri(String endpointUrl, String adSize, String id, String currency, Site site) {
        final URIBuilder uriBuilder = new URIBuilder()
                .setPath(endpointUrl)
                .addParameter("posall", "SSPLOC")
                .addParameter("id", id)
                .addParameter("sdktype", "0")
                .addParameter("hb", "true")
                .addParameter("t", "json3")
                .addParameter("currency", currency)
                .addParameter("sdkname", "prebidserver")
                .addParameter("adapterver", VERSION);

        if (StringUtils.isNotBlank(adSize)) {
            uriBuilder.addParameter("size", adSize);
        }

        if (site != null && StringUtils.isNotBlank(site.getPage())) {
            uriBuilder.addParameter("tp", site.getPage());
        }

        return uriBuilder.toString();
    }

    private String getAdSize(Imp imp) {
        if (imp.getBanner() == null || CollectionUtils.isEmpty(imp.getBanner().getFormat())) {
            return "";
        }
        StringBuilder adSize = new StringBuilder();
        for (Format format : imp.getBanner().getFormat()) {
            final Integer formatHeight = format.getH();
            final Integer formatWidth = format.getW();
            adSize.append(String.format("%s×%s,", formatWidth, formatHeight));
        }

        if (adSize.length() > 0 && adSize.lastIndexOf(",") == adSize.length() - 1) {
            adSize.deleteCharAt(adSize.length() - 1);
        }

        return adSize.toString();
    }

    private String getCurrency(BidRequest request) {
        if (CollectionUtils.isEmpty(request.getCur())) {
            return DEFAULT_REQUEST_CURRENCY;
        } else {
            for (String cur : request.getCur()) {
                if (cur.equals(DEFAULT_REQUEST_CURRENCY)) {
                    return cur;
                }
            }
            return request.getCur().get(0);
        }
    }

    private HttpRequest<BidRequest> createSingleRequest(Imp imp, BidRequest request, String uri) {
        final BidRequest outgoingRequest = request.toBuilder().imp(Collections.singletonList(imp)).build();

        final String body = mapper.encode(outgoingRequest);

        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(uri)
                .headers(HttpUtil.headers())
                .body(body)
                .payload(outgoingRequest)
                .build();
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        final int statusCode = httpCall.getResponse().getStatusCode();
        if (statusCode == HttpResponseStatus.NO_CONTENT.code()) {
            return Result.of(Collections.emptyList(), Collections.emptyList());
        } else if (statusCode == HttpResponseStatus.BAD_REQUEST.code()) {
            return Result.emptyWithError(BidderError.badInput("Invalid request."));
        } else if (statusCode != HttpResponseStatus.OK.code()) {
            return Result.emptyWithError(BidderError.badServerResponse(String.format("Unexpected HTTP status %s.",
                    statusCode)));
        }

        final AdgenerationResponse adgenerationResponse;
        try {
            adgenerationResponse = decodeBodyToBidResponse(httpCall);
        } catch (PreBidException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }

        final List<BidderBid> bidderBids = new ArrayList<>();
        for (Imp imp : bidRequest.getImp()) {
            final ExtImpAdgeneration extImpAdgeneration;
            try {
                extImpAdgeneration = parseAndValidateImpExt(imp);
            } catch (PreBidException e) {
                return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
            }

            if (extImpAdgeneration.getId().equals(adgenerationResponse.getLocationid())) {
                final Bid updatedBid = Bid.builder()
                        .id(adgenerationResponse.getLocationid())
                        .impid(imp.getId())
                        .adm(getAdm(adgenerationResponse, imp.getId()))
                        .price(adgenerationResponse.getCpm())
                        .w(adgenerationResponse.getW())
                        .h(adgenerationResponse.getH())
                        .crid(adgenerationResponse.getCreativeid())
                        .dealid(adgenerationResponse.getDealid())
                        .build();
                final BidderBid bidderBid = BidderBid.of(updatedBid, BidType.banner, DEFAULT_BID_CURRENCY);
                bidderBids.add(bidderBid);
                return Result.of(bidderBids, Collections.emptyList());
            }
        }
        return null;
    }

    private AdgenerationResponse decodeBodyToBidResponse(HttpCall<BidRequest> httpCall) {
        try {
            return mapper.decodeValue(httpCall.getResponse().getBody(), AdgenerationResponse.class);
        } catch (DecodeException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private String getAdm(AdgenerationResponse adgenerationResponse, String impId) {
        String ad = adgenerationResponse.getAd();
        if (StringUtils.isNotBlank(adgenerationResponse.getVastxml())) {
            ad = "<body><div id=\"apvad-" + impId + "\"></div><script type=\"text/javascript\" id=\"apv\" "
                    + "src=\"https://cdn.apvdr.com/js/VideoAd.min.js\"></script>" + insertVASTMethod(impId,
                    adgenerationResponse.getVastxml()) + "</body>";
        }
        ad = appendChildToBody(ad, adgenerationResponse.getBeacon());
        final String unwrappedAd = removeWrapper(ad);
        if (StringUtils.isNotBlank(unwrappedAd)) {
            return unwrappedAd;
        }
        return ad;
    }

    private String insertVASTMethod(String impId, String vastXml) {
        final String replacedVastxml = vastXml.replaceAll("/\\r?\\n/g", "");
        return "<script type=\"text/javascript\"> (function(){ new APV.VideoAd({s:\"" + impId + "\"}).load('"
                + replacedVastxml + "'); })(); </script>";
    }

    private String appendChildToBody(String ad, String beacon) {
        return ad.replaceAll("</\\s?body>", beacon + "</body>");
    }

    private String removeWrapper(String ad) {
        if (!ad.contains("<body>") || ad.lastIndexOf("</body>") == -1) {
            return "";
        }
        return ad.replace("<body>", "").replaceFirst("<body>", "").trim();
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}
