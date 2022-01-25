package org.prebid.server.bidder.adgeneration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.response.Bid;
import io.vertx.core.MultiMap;
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
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.adgeneration.ExtImpAdgeneration;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AdgenerationBidder implements Bidder<Void> {

    private static final String VERSION = "1.0.2";
    private static final String DEFAULT_REQUEST_CURRENCY = "JPY";
    private static final Pattern REPLACE_VAST_XML_PATTERN = Pattern.compile("/\\r?\\n/g", Pattern.CASE_INSENSITIVE);
    private static final Pattern APPEND_CHILD_TO_BODY_PATTERN = Pattern.compile("</\\s?body>",
            Pattern.CASE_INSENSITIVE);

    private static final TypeReference<ExtPrebid<?, ExtImpAdgeneration>> ADGENERATION_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public AdgenerationBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<Void>>> makeHttpRequests(BidRequest request) {
        final List<HttpRequest<Void>> requests = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();
        for (Imp imp : request.getImp()) {
            try {
                final ExtImpAdgeneration extImpAdgeneration = parseAndValidateImpExt(imp);
                final String adgenerationId = extImpAdgeneration.getId();
                final String adSizes = getAdSize(imp);
                final String currency = getCurrency(request);
                final String uri = getUri(adSizes, adgenerationId, currency,
                        request.getSite(), request.getSource());

                requests.add(createSingleRequest(uri, request.getDevice()));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.of(requests, errors);
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

    private String getUri(String adSize, String id, String currency, Site site, Source source) {
        final URIBuilder uriBuilder;
        try {
            uriBuilder = new URIBuilder(endpointUrl);
        } catch (URISyntaxException e) {
            throw new PreBidException(String.format("Invalid url: %s, error: %s", endpointUrl, e.getMessage()));
        }

        uriBuilder
                .addParameter("posall", "SSPLOC")
                .addParameter("id", id)
                .addParameter("sdktype", "0")
                .addParameter("hb", "true")
                .addParameter("t", "json3")
                .addParameter("currency", currency)
                .addParameter("sdkname", "prebidserver")
                .addParameter("adapterver", VERSION);

        if (StringUtils.isNotBlank(adSize)) {
            uriBuilder.addParameter("sizes", adSize);
        }

        final String page = site != null ? site.getPage() : null;
        if (StringUtils.isNotBlank(page)) {
            uriBuilder.addParameter("tp", page);
        }

        final String transactionId = source != null ? source.getTid() : null;
        if (StringUtils.isNotBlank(transactionId)) {
            uriBuilder.addParameter("transactionid", transactionId);
        }

        return uriBuilder.toString();
    }

    private static String getAdSize(Imp imp) {
        final Banner banner = imp.getBanner();
        final List<Format> formats = banner != null ? banner.getFormat() : null;
        return CollectionUtils.emptyIfNull(formats).stream()
                .map(format -> String.format("%sx%s", format.getW(), format.getH()))
                .collect(Collectors.joining(","));
    }

    private static String getCurrency(BidRequest bidRequest) {
        final List<String> currencies = bidRequest.getCur();
        return CollectionUtils.isEmpty(currencies)
                ? DEFAULT_REQUEST_CURRENCY
                : currencies.contains(DEFAULT_REQUEST_CURRENCY) ? DEFAULT_REQUEST_CURRENCY : currencies.get(0);
    }

    private static HttpRequest<Void> createSingleRequest(String uri, Device device) {
        return HttpRequest.<Void>builder()
                .method(HttpMethod.GET)
                .uri(uri)
                .headers(resolveHeaders(device))
                .build();
    }

    private static MultiMap resolveHeaders(Device device) {
        final MultiMap headers = HttpUtil.headers();

        final String userAgent = device != null ? device.getUa() : null;
        if (StringUtils.isNotBlank(userAgent)) {
            headers.add("User-Agent", userAgent);
        }
        return headers;
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<Void> httpCall, BidRequest bidRequest) {
        try {
            final AdgenerationResponse adgenerationResponse = decodeBodyToBidResponse(httpCall.getResponse());
            if (CollectionUtils.isEmpty(adgenerationResponse.getResults())) {
                return Result.withError(BidderError.badServerResponse("Results object in BidResponse is empty"));
            }

            return resultWithBidderBids(bidRequest, adgenerationResponse);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private AdgenerationResponse decodeBodyToBidResponse(HttpResponse httpResponse) {
        try {
            return mapper.decodeValue(httpResponse.getBody(), AdgenerationResponse.class);
        } catch (DecodeException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private Result<List<BidderBid>> resultWithBidderBids(BidRequest bidRequest,
                                                         AdgenerationResponse adgenerationResponse) {
        for (Imp imp : bidRequest.getImp()) {
            final ExtImpAdgeneration extImpAdgeneration = parseAndValidateImpExt(imp);

            final String locationId = adgenerationResponse.getLocationid();
            if (extImpAdgeneration.getId().equals(locationId)) {
                final String adm = getAdm(adgenerationResponse, imp.getId());
                final Bid updatedBid = Bid.builder()
                        .id(locationId)
                        .impid(imp.getId())
                        .adm(adm)
                        .price(adgenerationResponse.getCpm())
                        .w(adgenerationResponse.getW())
                        .h(adgenerationResponse.getH())
                        .crid(adgenerationResponse.getCreativeid())
                        .dealid(adgenerationResponse.getDealid())
                        .build();
                final BidderBid bidderBid = BidderBid.of(updatedBid, BidType.banner, getCurrency(bidRequest));
                return Result.withValue(bidderBid);
            }
        }
        return null;
    }

    private String getAdm(AdgenerationResponse adgenerationResponse, String impId) {
        String ad = adgenerationResponse.getAd();
        if (StringUtils.isNotBlank(adgenerationResponse.getVastxml())) {
            ad = String.format("<body><div id=\"apvad-%s\"></div><script type=\"text/javascript\" id=\"apv\" "
                            + "src=\"https://cdn.apvdr.com/js/VideoAd.min.js\"></script>%s</body>", impId,
                    insertVASTMethod(impId, adgenerationResponse.getVastxml()));
        }
        final String updateAd = StringUtils.isNotBlank(adgenerationResponse.getBeacon())
                ? appendChildToBody(ad, adgenerationResponse.getBeacon())
                : ad;

        final String unwrappedAd = removeWrapper(updateAd);
        return StringUtils.isNotBlank(unwrappedAd) ? unwrappedAd : updateAd;
    }

    private String insertVASTMethod(String impId, String vastXml) {
        final String replacedVastxml = REPLACE_VAST_XML_PATTERN.matcher(vastXml).replaceAll("");
        return String.format("<script type=\"text/javascript\"> (function(){ new APV.VideoAd({s:\"%s\"}).load('%s');"
                + " })(); </script>", impId, replacedVastxml);
    }

    private String appendChildToBody(String ad, String beacon) {
        return APPEND_CHILD_TO_BODY_PATTERN.matcher(ad).replaceAll(beacon + "</body>");
    }

    private String removeWrapper(String ad) {
        return !ad.contains("<body>") || ad.lastIndexOf("</body>") == -1
                ? ""
                : ad.replace("<body>", "").replaceFirst("<body>", "").trim();
    }
}
