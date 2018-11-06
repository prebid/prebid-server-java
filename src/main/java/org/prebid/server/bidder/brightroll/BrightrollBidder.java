package org.prebid.server.bidder.brightroll;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
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

    private final String endpointUrl;

    public BrightrollBidder(String endpointUrl) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
    }

    private static final TypeReference<ExtPrebid<?, ExtImpBrightroll>> BRIGHTROLL_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpBrightroll>>() {
            };

    /**
     * Creates POST HTTP requests which should be made to fetch bids.
     */
    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<Imp> imps = request.getImp();
        if (CollectionUtils.isEmpty(imps)) {
            return Result.of(Collections.emptyList(),
                    Collections.singletonList(BidderError.badInput("No impression in the bid request")));
        }

        final List<BidderError> errors = new ArrayList<>();
        final BidRequest updateBidRequest = updateBidRequest(request, errors);

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

        final String publisher;
        try {
            publisher = getPublisher(imps.get(0));
        } catch (PreBidException ex) {
            return Result.of(Collections.emptyList(), Collections.singletonList(BidderError.badInput(ex.getMessage())));
        }

        return Result.of(Collections.singletonList(HttpRequest.of(
                HttpMethod.POST,
                String.format("%s?publisher=%s", endpointUrl, publisher),
                bidRequestBody,
                createHeaders(updateBidRequest.getDevice()),
                updateBidRequest)),
                Collections.emptyList());
    }

    /**
     * Updates {@link BidRequest} with {@link Imp}s if something changed or dropped from the list.
     */
    private static BidRequest updateBidRequest(BidRequest bidRequest, List<BidderError> errors) {
        final List<Imp> imps = bidRequest.getImp();
        final boolean requiredUpdate = imps.stream().anyMatch(BrightrollBidder::isRequiredUpdate);

        if (requiredUpdate) {
            return bidRequest.toBuilder().imp(imps.stream()
                    .filter(imp -> isImpValid(imp, errors))
                    .map(BrightrollBidder::updateImpSize)
                    .collect(Collectors.toList())).build();
        }
        return bidRequest;
    }

    /**
     * Checks if {@link Imp} requires changes.
     */
    private static boolean isRequiredUpdate(Imp imp) {
        final Banner banner = imp.getBanner();
        return (banner == null && imp.getVideo() == null)
                || (banner != null && banner.getW() == null && banner.getH() == null
                && CollectionUtils.isNotEmpty(banner.getFormat()));
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
     * Updates {@link Imp} {@link Banner} if required.
     */
    private static Imp updateImpSize(Imp imp) {
        final Banner banner = imp.getBanner();
        if (banner != null && banner.getW() == null && banner.getH() == null
                && CollectionUtils.isNotEmpty(banner.getFormat())) {

            // update banner with size from first format
            final Format firstFormat = banner.getFormat().get(0);
            return imp.toBuilder()
                    .banner(banner.toBuilder()
                            .w(firstFormat.getW())
                            .h(firstFormat.getH())
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
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpHeaders.USER_AGENT.toString(), device.getUa());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpHeaders.ACCEPT_LANGUAGE.toString(), device.getLanguage());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER.toString(), device.getIp());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.DNT_HEADER.toString(),
                    Objects.toString(device.getDnt(), null));
        }

        return headers;
    }

    /**
     * Extracts publisher from {@link ExtImpBrightroll}.
     */
    private String getPublisher(Imp imp) {
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
