package org.prebid.server.bidder.brightroll;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.EncodeException;
import io.vertx.core.json.Json;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderUtil;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.brightroll.ExtImpBrightroll;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Brightroll {@link Bidder} implementation.
 */
public class BrightrollBidder implements Bidder<BidRequest> {

    private static final String VERSION = "2.5";
    private static final CharSequence OPEN_RTB_VERSION_HEADER = HttpHeaders.createOptimized("x-openrtb-version");

    private static final List<Integer> BLOCKED_CREATIVETYPES_FOR_AD_THRIVE = Arrays.asList(1, 2, 3, 6, 9, 10);
    private static final List<String> BLOCKED_CATEGORIES_FOR_ADTHRIVE =
            Arrays.asList("IAB8-5", "IAB8-18", "IAB15-1", "IAB7-30", "IAB14-1", "IAB22-1", "IAB3-7", "IAB7-3",
                    "IAB14-3", "IAB11", "IAB11-1", "IAB11-2", "IAB11-3", "IAB11-4", "IAB11-5", "IAB23", "IAB23-1",
                    "IAB23-2", "IAB23-3", "IAB23-4", "IAB23-5", "IAB23-6", "IAB23-7", "IAB23-8", "IAB23-9", "IAB23-10",
                    "IAB7-39", "IAB9-30", "IAB7-44", "IAB25", "IAB25-1", "IAB25-2", "IAB25-3", "IAB25-4", "IAB25-5",
                    "IAB25-6", "IAB25-7", "IAB26", "IAB26-1", "IAB26-2", "IAB26-3", "IAB26-4");

    private static final TypeReference<ExtPrebid<?, ExtImpBrightroll>> BRIGHTROLL_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpBrightroll>>() {
            };

    private final String endpointUrl;

    public BrightrollBidder(String endpointUrl) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
    }

    /**
     * Creates POST HTTP requests which should be made to fetch bids.
     */
    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final String firstImpExtPublisher;
        try {
            firstImpExtPublisher = getAndValidateImpExt(request.getImp().get(0));
        } catch (PreBidException ex) {
            return Result.of(Collections.emptyList(), Collections.singletonList(BidderError.badInput(ex.getMessage())));
        }

        final BidRequest updateBidRequest = updateBidRequest(request, firstImpExtPublisher, errors);

        if (CollectionUtils.isEmpty(updateBidRequest.getImp())) {
            errors.add(BidderError.badInput("No valid impression in the bid request"));
            return Result.of(Collections.emptyList(), errors);
        }

        final String bidRequestBody;
        try {
            bidRequestBody = Json.encode(updateBidRequest);
        } catch (EncodeException e) {
            errors.add(BidderError.badInput(String.format("error while encoding bidRequest, err: %s", e.getMessage())));
            return Result.of(Collections.emptyList(), errors);
        }

        return Result.of(Collections.singletonList(
                HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(String.format("%s?publisher=%s", endpointUrl, firstImpExtPublisher))
                        .body(bidRequestBody)
                        .headers(createHeaders(updateBidRequest.getDevice()))
                        .payload(updateBidRequest)
                        .build()),
                Collections.emptyList());
    }

    /**
     * Extracts and validates {@link ExtImpBrightroll} from given impression.
     */
    private String getAndValidateImpExt(Imp imp) {
        final ObjectNode impExt = imp.getExt();
        if (impExt == null || impExt.size() == 0) {
            throw new PreBidException("ext.bidder not provided");
        }

        final String publisher;
        try {
            publisher = Json.mapper.<ExtPrebid<?, ExtImpBrightroll>>convertValue(impExt,
                    BRIGHTROLL_EXT_TYPE_REFERENCE).getBidder().getPublisher();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("ext.bidder.publisher not provided");
        }

        if (StringUtils.isEmpty(publisher)) {
            throw new PreBidException("publisher is empty");
        }

        return publisher;
    }

    /**
     * Updates {@link BidRequest} with default auction type
     * and {@link Imp}s if something changed or dropped from the list.
     */
    private static BidRequest updateBidRequest(BidRequest bidRequest, String firstImpExtPublisher,
                                               List<BidderError> errors) {
        final BidRequest.BidRequestBuilder builder = bidRequest.toBuilder();
        // Defaulting to first price auction for all prebid requests
        builder.at(1);

        final boolean isAdthrivePublisher = Objects.equals(firstImpExtPublisher, "adthrive");
        if (isAdthrivePublisher) {
            builder.bcat(BLOCKED_CATEGORIES_FOR_ADTHRIVE);
        }

        builder.imp(bidRequest.getImp().stream()
                .filter(imp -> isImpValid(imp, errors))
                .map(imp -> updateImp(imp, isAdthrivePublisher))
                .collect(Collectors.toList()));

        return builder.build();
    }

    /**
     * Checks if {@link Imp} is valid.
     */
    private static boolean isImpValid(Imp imp, List<BidderError> errors) {
        if (imp.getBanner() != null || imp.getVideo() != null) {
            return true;
        } else {
            errors.add(BidderError.badInput(String.format(
                    "Brightroll only supports banner and video imps. Ignoring imp id=%s", imp.getId())));
            return false;
        }
    }

    /**
     * Updates {@link Imp} {@link Banner} and/or {@link Video}.
     */
    private static Imp updateImp(Imp imp, boolean isAdthrivePublisher) {
        final Banner banner = imp.getBanner();
        if (banner != null) {
            final Banner.BannerBuilder bannerBuilder = banner.toBuilder();
            if (banner.getW() == null && banner.getH() == null && CollectionUtils.isNotEmpty(banner.getFormat())) {
                // update banner with size from first format
                final Format firstFormat = banner.getFormat().get(0);
                bannerBuilder
                        .w(firstFormat.getW())
                        .h(firstFormat.getH());
            }
            if (isAdthrivePublisher) {
                bannerBuilder.battr(BLOCKED_CREATIVETYPES_FOR_AD_THRIVE);
            }
            return imp.toBuilder()
                    .banner(bannerBuilder.build())
                    .build();
        }
        final Video video = imp.getVideo();
        if (video != null && isAdthrivePublisher) {
            return imp.toBuilder()
                    .video(video.toBuilder()
                            .battr(BLOCKED_CREATIVETYPES_FOR_AD_THRIVE)
                            .build())
                    .build();
        }
        return imp;
    }

    /**
     * Creates headers for post request with version and {@link Device} properties.
     */
    private MultiMap createHeaders(Device device) {
        final MultiMap headers = BidderUtil.headers();

        headers.add(OPEN_RTB_VERSION_HEADER, VERSION);

        if (device != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER.toString(), device.getUa());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.ACCEPT_LANGUAGE_HEADER.toString(),
                    device.getLanguage());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER.toString(), device.getIp());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.DNT_HEADER.toString(),
                    Objects.toString(device.getDnt(), null));
        }

        return headers;
    }

    /**
     * Converts response to {@link List} of {@link BidderBid}s with {@link List} of errors.
     */
    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = Json.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return extractBids(bidResponse, bidRequest.getImp());
        } catch (DecodeException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    /**
     * Extracts {@link Bid}s from response.
     */
    private Result<List<BidderBid>> extractBids(BidResponse bidResponse, List<Imp> imps) {
        return bidResponse == null || bidResponse.getSeatbid() == null
                ? Result.of(Collections.emptyList(), Collections.emptyList())
                : Result.of(createBiddersBid(bidResponse, imps), Collections.emptyList());
    }

    /**
     * Extracts {@link Bid}s from response and finds its type against matching {@link Imp}. In case matching {@link Imp}
     * was not found, {@link BidType} is considered as banner .
     */
    private static List<BidderBid> createBiddersBid(BidResponse bidResponse, List<Imp> imps) {

        return bidResponse.getSeatbid().get(0).getBid().stream().filter(Objects::nonNull)
                .map(bid -> BidderBid.of(bid, getBidderType(imps, bid.getImpid()), null))
                .collect(Collectors.toList());
    }

    /**
     * Finds matching {@link Imp} by impId and checks for {@link BidType}.
     */
    private static BidType getBidderType(List<Imp> imps, String impId) {
        return imps.stream()
                .filter(imp -> Objects.equals(imp.getId(), impId))
                .findAny()
                .map(BrightrollBidder::bidTypeFromImp)
                .orElse(BidType.banner);
    }

    /**
     * Identifies {@link BidType} depends on {@link Imp} parameters.
     */
    private static BidType bidTypeFromImp(Imp imp) {
        final BidType bidType;
        if (imp.getVideo() != null) {
            bidType = BidType.video;
        } else {
            bidType = BidType.banner;
        }
        return bidType;
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}
