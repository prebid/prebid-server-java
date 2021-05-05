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
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.brightroll.model.PublisherOverride;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.EncodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.brightroll.ExtImpBrightroll;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Brightroll {@link Bidder} implementation.
 */
public class BrightrollBidder implements Bidder<BidRequest> {

    private static final String OPENRTB_VERSION = "2.5";
    private static final TypeReference<ExtPrebid<?, ExtImpBrightroll>> BRIGHTROLL_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpBrightroll>>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;
    private final Map<String, PublisherOverride> publisherIdToOverride;

    public BrightrollBidder(String endpointUrl,
                            JacksonMapper mapper,
                            Map<String, PublisherOverride> publisherIdToOverride) {

        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
        this.publisherIdToOverride = Objects.requireNonNull(publisherIdToOverride);
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
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        final BidRequest updateBidRequest = updateBidRequest(request, firstImpExtPublisher, errors);

        if (CollectionUtils.isEmpty(updateBidRequest.getImp())) {
            errors.add(BidderError.badInput("No valid impression in the bid request"));
            return Result.withErrors(errors);
        }

        final String bidRequestBody;
        try {
            bidRequestBody = mapper.encode(updateBidRequest);
        } catch (EncodeException e) {
            errors.add(BidderError.badInput(String.format("error while encoding bidRequest, err: %s", e.getMessage())));
            return Result.withErrors(errors);
        }

        return Result.withValue(HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(String.format("%s?publisher=%s", endpointUrl, firstImpExtPublisher))
                .body(bidRequestBody)
                .headers(createHeaders(updateBidRequest.getDevice()))
                .payload(updateBidRequest)
                .build());
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
            publisher = mapper.mapper().convertValue(impExt, BRIGHTROLL_EXT_TYPE_REFERENCE).getBidder().getPublisher();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("ext.bidder.publisher not provided");
        }

        if (StringUtils.isEmpty(publisher)) {
            throw new PreBidException("publisher is empty");
        }

        if (!publisherIdToOverride.containsKey(publisher)) {
            throw new PreBidException("publisher is not valid");
        }

        return publisher;
    }

    /**
     * Updates {@link BidRequest} with default auction type
     * and {@link Imp}s if something changed or dropped from the list.
     */
    private BidRequest updateBidRequest(BidRequest bidRequest, String firstImpExtPublisher,
                                        List<BidderError> errors) {
        final BidRequest.BidRequestBuilder builder = bidRequest.toBuilder();
        // Defaulting to first price auction for all prebid requests
        builder.at(1);

        final PublisherOverride publisherOverride = publisherIdToOverride.get(firstImpExtPublisher);

        if (publisherOverride != null) {
            builder.bcat(publisherOverride.getBcat())
                    .badv(publisherOverride.getBadv());
        }

        builder.imp(bidRequest.getImp().stream()
                .filter(imp -> isImpValid(imp, errors))
                .map(imp -> updateImp(imp, publisherOverride))
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
    private Imp updateImp(Imp imp, PublisherOverride publisherOverride) {
        final BigDecimal bidFloor = publisherOverride != null && publisherOverride.getBidFloor() != null
                ? publisherOverride.getBidFloor()
                : null;
        final List<Integer> impBattr = publisherOverride != null && publisherOverride.getImpBattr() != null
                ? publisherOverride.getImpBattr()
                : null;

        final Banner banner = imp.getBanner();
        if (banner != null) {
            final boolean noSizes = banner.getW() == null && banner.getH() == null
                    && CollectionUtils.isNotEmpty(banner.getFormat());

            if (bidFloor != null || impBattr != null || noSizes) {
                final Imp.ImpBuilder impBuilder = imp.toBuilder();

                if (bidFloor != null) {
                    impBuilder.bidfloor(bidFloor);
                }
                if (impBattr != null || noSizes) {
                    impBuilder.banner(updateBanner(banner, impBattr, noSizes));
                }

                return impBuilder.build();
            }
        }

        final Video video = imp.getVideo();
        if (video != null && (bidFloor != null || impBattr != null)) {
            final Imp.ImpBuilder impBuilder = imp.toBuilder();

            if (bidFloor != null) {
                impBuilder.bidfloor(bidFloor);
            }
            if (impBattr != null) {
                impBuilder.video(video.toBuilder()
                        .battr(impBattr)
                        .build());
            }
            return impBuilder.build();
        }
        return imp;
    }

    private static Banner updateBanner(Banner banner, List<Integer> impBattr, boolean noSizes) {
        final Banner.BannerBuilder bannerBuilder = banner.toBuilder();

        if (impBattr != null) {
            bannerBuilder.battr(impBattr);
        }
        if (noSizes) {
            // update banner with size from first format
            final Format firstFormat = banner.getFormat().get(0);
            bannerBuilder
                    .w(firstFormat.getW())
                    .h(firstFormat.getH());
        }

        return bannerBuilder.build();
    }

    /**
     * Creates headers for post request with version and {@link Device} properties.
     */
    private MultiMap createHeaders(Device device) {
        final MultiMap headers = HttpUtil.headers();
        headers.add(HttpUtil.X_OPENRTB_VERSION_HEADER, OPENRTB_VERSION);

        if (device != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER, device.getUa());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.ACCEPT_LANGUAGE_HEADER, device.getLanguage());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIp());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.DNT_HEADER, Objects.toString(device.getDnt(), null));
        }

        return headers;
    }

    /**
     * Converts response to {@link List} of {@link BidderBid}s with {@link List} of errors.
     */
    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return extractBids(bidResponse, bidRequest.getImp());
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    /**
     * Extracts {@link Bid}s from response.
     */
    private Result<List<BidderBid>> extractBids(BidResponse bidResponse, List<Imp> imps) {
        return bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())
                ? Result.empty()
                : Result.withValues(createBiddersBid(bidResponse, imps));
    }

    /**
     * Extracts {@link Bid}s from response and finds its type against matching {@link Imp}. In case matching {@link Imp}
     * was not found, {@link BidType} is considered as banner .
     */
    private static List<BidderBid> createBiddersBid(BidResponse bidResponse, List<Imp> imps) {

        return bidResponse.getSeatbid().get(0).getBid().stream().filter(Objects::nonNull)
                .map(bid -> BidderBid.of(bid, getBidderType(imps, bid.getImpid()), bidResponse.getCur()))
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
        return imp.getVideo() != null ? BidType.video : BidType.banner;
    }
}
