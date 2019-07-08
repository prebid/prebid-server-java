package org.prebid.server.bidder.sharethrough;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
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
        final String page = SharethroughRequestUtil.getPage(request.getSite());

        // site.page validation is already performed by {@link RequestValidator#validate}
        if (page == null) {
            return Result.emptyWithError(BidderError.badInput("site.page is required"));
        }

        List<StrUriParameters> strUriParameters;
        try {
            strUriParameters = parseBidRequestToUriParameters(request);
        } catch (IllegalArgumentException e) {
            return Result.emptyWithError(BidderError.badInput(
                    String.format("Error occurred parsing sharethrough parameters %s", e.getMessage())));
        }

        final MultiMap headers = HttpUtil.headers()
                .add("Origin", SharethroughRequestUtil.getHost(page));

        final List<HttpRequest<Void>> httpRequests = strUriParameters.stream()
                .map(strUriParameter -> SharethroughUriBuilderUtil.buildSharethroughUrl(
                        endpointUrl, SUPPLY_ID, VERSION, strUriParameter))
                .map(uri -> makeHttpRequest(uri, headers))
                .collect(Collectors.toList());

        return Result.of(httpRequests, Collections.emptyList());
    }

    /**
     * Retrieves from {@link Imp} and filter not valid {@link ExtImpSharethrough} and returns list result with errors.
     */
    private List<StrUriParameters> parseBidRequestToUriParameters(BidRequest request) {
        final List<StrUriParameters> strUriParameters = new ArrayList<>();

        final boolean consentRequired = SharethroughRequestUtil.isConsentRequired(request.getRegs());

        final ExtUser extUser = SharethroughRequestUtil.getExtUser(request.getUser());
        final String consent = SharethroughRequestUtil.getConsent(extUser);

        final String ua = SharethroughRequestUtil.getUa(request.getDevice());
        final boolean canAutoPlay = SharethroughRequestUtil.canBrowserAutoPlayVideo(ua);

        for (Imp imp : request.getImp()) {
            ExtImpSharethrough extImpStr = Json.mapper.<ExtPrebid<?, ExtImpSharethrough>>convertValue(
                    imp.getExt(), SHARETHROUGH_EXT_TYPE_REFERENCE).getBidder();

            strUriParameters.add(createStrUriParameters(consentRequired, consent, canAutoPlay, imp, extImpStr));
        }
        return strUriParameters;
    }

    /**
     * Populate {@link StrUriParameters} with publisher request, imp, imp.ext values
     */
    private StrUriParameters createStrUriParameters(boolean isConsentRequired, String consentString,
                                                    boolean canBrowserAutoPlayVideo, Imp imp,
                                                    ExtImpSharethrough extImpStr) {
        final Size size = SharethroughRequestUtil.getSize(imp, extImpStr);
        return StrUriParameters.builder()
                .pkey(extImpStr.getPkey())
                .bidID(imp.getId())
                .consentRequired(isConsentRequired)
                .consentString(consentString)
                .instantPlayCapable(canBrowserAutoPlayVideo)
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
            return Result.of(toBidderBid(sharethroughBid, httpCall.getRequest()), Collections.emptyList());
        } catch (IOException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return Result.emptyWithError(BidderError.badInput(e.getMessage()));
        }
    }

    /**
     * Converts {@link ExtImpSharethroughResponse} to {@link List} of {@link BidderBid}.
     */
    private List<BidderBid> toBidderBid(ExtImpSharethroughResponse sharethroughBid, HttpRequest request)
            throws IOException {
        if (sharethroughBid.getCreatives().isEmpty()) {
            throw new IllegalArgumentException("No creative provided");
        }

        final StrUriParameters strUriParameters = SharethroughUriBuilderUtil
                .buildSharethroughUrlParameters(request.getUri());
        final ExtImpSharethroughCreative creative = sharethroughBid.getCreatives().get(0);

        final String adMarkup;
        try {
            adMarkup = SharethroughMarkupUtil.getAdMarkup(sharethroughBid, strUriParameters);
        } catch (JsonProcessingException e) {
            throw new IOException("Cant parse markup", e);
        }

        return Collections.singletonList(
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
                        DEFAULT_BID_CURRENCY));
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}

