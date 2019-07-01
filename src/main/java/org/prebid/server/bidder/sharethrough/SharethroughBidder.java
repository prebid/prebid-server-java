package org.prebid.server.bidder.sharethrough;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.sharethrough.model.Size;
import org.prebid.server.bidder.sharethrough.model.StrUriParameters;
import org.prebid.server.bidder.sharethrough.model.bidResponse.ExtImpSharethroughCreative;
import org.prebid.server.bidder.sharethrough.model.bidResponse.ExtImpSharethroughResponse;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.sharethrough.ExtImpSharethrough;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class SharethroughBidder implements Bidder<Void> {

    private static final String VERSION = "1.0.0";
    private static final String SUPPLY_ID = "FGMrCMMc";
    private static final String DEFAULT_BID_CURRENCY = "USD";
    private static final BidType DEFAULT_BID_TYPE = BidType.xNative;

    private static final TypeReference<ExtPrebid<?, ExtImpSharethrough>> SHARETHROUGH_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpSharethrough>>() {
            };
    private final String endpointUrl;

    public SharethroughBidder(String endpointUrl) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
    }

    /**
     * Makes the HTTP requests which should be made to fetch bids.
     * <p>
     * Creates POST http request with all parameters in url and headers with empty body.
     */
    @Override
    public Result<List<HttpRequest<Void>>> makeHttpRequests(BidRequest request) {
        String page = SharethroughRequestUtil.getPage(request.getSite());

        // site.page validation is already performed by {@link RequestValidator#validate}
        if (page == null) {
            return Result.emptyWithError(BidderError.badInput("site.page is required"));
        }

        final Result<List<StrUriParameters>> strUriParamtersResult = parseBidRequestToUriParameters(request);
        final List<StrUriParameters> strUriParameters = strUriParamtersResult.getValue();
        final List<BidderError> errors = strUriParamtersResult.getErrors();
        if (!errors.isEmpty()) {
            return Result.of(Collections.emptyList(), errors);
        }

        final MultiMap headers = HttpUtil.headers()
                .add("Origin", SharethroughRequestUtil.getHost(page));

        List<HttpRequest<Void>> httpRequests = strUriParameters.stream()
                .map(strUriParameter -> SharethroughUriBuilderUtil.buildSharethroughUrl(
                        endpointUrl, SUPPLY_ID, VERSION, strUriParameter))
                .map(uri -> makeHttpRequest(uri, headers))
                .collect(Collectors.toList());

        return Result.of(httpRequests, Collections.emptyList());
    }

    /**
     * Retrieves from {@link Imp} and filter not valid {@link ExtImpSharethrough} and returns list result with errors.
     */
    private Result<List<StrUriParameters>> parseBidRequestToUriParameters(BidRequest request) {
        final List<StrUriParameters> strUriParameters = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            final ExtImpSharethrough extImpSharethrough;
            try {
                extImpSharethrough = Json.mapper.<ExtPrebid<?, ExtImpSharethrough>>convertValue(imp.getExt(),
                        SHARETHROUGH_EXT_TYPE_REFERENCE).getBidder();
            } catch (IllegalArgumentException e) {
                return Result.emptyWithError(BidderError.badInput(
                        String.format("Error occurred parsing sharethrough parameters %s", e.getMessage())));
            }

            strUriParameters.add(createStrUriParameters(request, imp, extImpSharethrough));
        }

        return Result.of(strUriParameters, Collections.emptyList());
    }

    /**
     * Populate {@link StrUriParameters} with publisher request, imp, imp.ext values
     */
    private StrUriParameters createStrUriParameters(BidRequest bidRequest, Imp imp, ExtImpSharethrough extImpStr) {
        Size size = SharethroughRequestUtil.getSize(imp, extImpStr);
        ExtUser extUser = SharethroughRequestUtil.getExtUser(bidRequest.getUser());
        String ua = SharethroughRequestUtil.getUa(bidRequest.getDevice());
        return StrUriParameters.builder()
                .pkey(extImpStr.getPkey())
                .bidID(imp.getId())
                .consentRequired(SharethroughRequestUtil.isConsentRequired(bidRequest.getRegs()))
                .consentString(SharethroughRequestUtil.getConsent(extUser))
                .instantPlayCapable(SharethroughRequestUtil.isBrowserCanAutoPlayVideo(ua))
                .iframe(extImpStr.getIframe())
                .height(size.getHeight())
                .width(size.getWidth())
                .build();
    }

    /**
     * Make {@link HttpRequest} from uri and headers
     */
    private HttpRequest<Void> makeHttpRequest(String uri, MultiMap headers) {
        return HttpRequest.<Void>builder()
                .method(HttpMethod.POST)
                .uri(uri)
                .body(null)
                .headers(headers)
                .payload(null)
                .build();
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<Void> httpCall, BidRequest bidRequest) {
        final HttpResponse httpResponse = httpCall.getResponse();

        final ExtImpSharethroughResponse sharethroughBid;
        try {
            sharethroughBid = Json.mapper.readValue(httpResponse.getBody(), ExtImpSharethroughResponse.class);
        } catch (IOException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }
        return toBidderBid(sharethroughBid, httpCall.getRequest());
    }

    /**
     * Converts {@link ExtImpSharethroughResponse} to {@link List} of {@link BidderBid}.
     */
    private Result<List<BidderBid>> toBidderBid(ExtImpSharethroughResponse sharethroughBid, HttpRequest request) {
        if (sharethroughBid.getCreatives().isEmpty()) {
            return Result.emptyWithError(BidderError.badInput("No creative provided"));
        }

        try {
            StrUriParameters strUriParameters = SharethroughUriBuilderUtil
                    .buildSharethroughUrlParameters(request.getUri());
            ExtImpSharethroughCreative creative = sharethroughBid.getCreatives().get(0);

            String adMarkup = SharethroughMarkupUtil.getAdMarkup(sharethroughBid, strUriParameters);
            if (StringUtils.isBlank(adMarkup)) {
                return Result.emptyWithError(BidderError.badServerResponse("Cant parse markup"));
            }

            return Result.of(Collections.singletonList(
                    BidderBid.of(
                            Bid.builder()
                                    .adid(sharethroughBid.getAdserverRequestId())
                                    .id(sharethroughBid.getBidId())
                                    .impid(strUriParameters.getBidID())
                                    .price(creative.getCpm())
                                    .cid(creative.getMetadata().getCampaignKey())
                                    .crid(creative.getMetadata().getCreativeKey())
                                    .dealid(creative.getMetadata().getDealId())
                                    .adm(adMarkup)
                                    .h(strUriParameters.getHeight())
                                    .w(strUriParameters.getWidth())
                                    .build(),
                            DEFAULT_BID_TYPE,
                            DEFAULT_BID_CURRENCY)), Collections.emptyList());
        } catch (IllegalArgumentException e) {
            return Result.emptyWithError(BidderError.badInput(e.getMessage()));
        }
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }

}
