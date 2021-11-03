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
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
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

public class BrightrollBidder implements Bidder<BidRequest> {

    private static final String OPENRTB_VERSION = "2.5";
    private static final TypeReference<ExtPrebid<?, ExtImpBrightroll>> BRIGHTROLL_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpBrightroll>>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;
    private final Map<String, BigDecimal> publisherIdToBidFloor;

    public BrightrollBidder(String endpointUrl,
                            JacksonMapper mapper,
                            Map<String, BigDecimal> publisherIdToBidFloor) {

        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
        this.publisherIdToBidFloor = Objects.requireNonNull(publisherIdToBidFloor);
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

        if (!publisherIdToBidFloor.containsKey(publisher)) {
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

        final BigDecimal publisherBidFloor = publisherIdToBidFloor.get(firstImpExtPublisher);

        builder.imp(bidRequest.getImp().stream()
                .filter(imp -> isImpValid(imp, errors))
                .map(imp -> updateImp(imp, publisherBidFloor))
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
    private Imp updateImp(Imp imp, BigDecimal publisherBidFloor) {
        final Banner banner = imp.getBanner();
        if (banner != null) {
            final boolean noSizes = banner.getW() == null && banner.getH() == null
                    && CollectionUtils.isNotEmpty(banner.getFormat());

            if (publisherBidFloor != null || noSizes) {
                final Imp.ImpBuilder impBuilder = imp.toBuilder();

                if (publisherBidFloor != null) {
                    impBuilder.bidfloor(publisherBidFloor);
                }
                if (noSizes) {
                    impBuilder.banner(updateBanner(banner));
                }

                return impBuilder.build();
            }
        }

        final Video video = imp.getVideo();
        if (video != null && publisherBidFloor != null) {
            return imp.toBuilder().bidfloor(publisherBidFloor).build();
        }
        return imp;
    }

    private static Banner updateBanner(Banner banner) {
        final Banner.BannerBuilder bannerBuilder = banner.toBuilder();
        final Format firstFormat = banner.getFormat().get(0);
        return bannerBuilder.w(firstFormat.getW())
                .h(firstFormat.getH())
                .build();
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
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Result.empty();
        }
        return Result.withValues(createBiddersBids(bidResponse.getSeatbid(), imps, bidResponse.getCur()));
    }

    /**
     * Extracts {@link Bid}s from response and finds its type against matching {@link Imp}. In case matching {@link Imp}
     * was not found, {@link BidType} is considered as banner.
     */
    private static List<BidderBid> createBiddersBids(List<SeatBid> seatBids, List<Imp> imps, String currency) {
        return seatBids.get(0).getBid().stream()
                .filter(Objects::nonNull)
                .map(bid -> BidderBid.of(bid, getBidType(bid.getImpid(), imps), currency))
                .collect(Collectors.toList());
    }

    /**
     * Finds matching {@link Imp} by impId and checks for {@link BidType}.
     */
    private static BidType getBidType(String impId, List<Imp> imps) {
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
