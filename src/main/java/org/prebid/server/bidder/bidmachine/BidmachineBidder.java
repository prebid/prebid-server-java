package org.prebid.server.bidder.bidmachine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Video;
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
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.bidmachine.ExtImpBidmachine;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;
import org.springframework.boot.json.JsonParseException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Bidmachine {@link Bidder} implementation.
 */
public class BidmachineBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpBidmachine>> BIDMACHINE_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpBidmachine>>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public BidmachineBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                validateImp(imp);
                final BidRequest singleRequest = createSingleRequest(imp, parsePrebid(imp.getExt()), request);
                final String body = mapper.encode(singleRequest);

                httpRequests.add(HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(buildEndpointUrl(parseAndValidateImpExt(imp)))
                        .body(body)
                        .headers(resolveHeaders())
                        .payload(singleRequest)
                        .build());
            } catch (Exception e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.of(httpRequests, errors);
    }

    private void validateImp(Imp imp) {
        final Banner banner = imp.getBanner();
        if (banner == null) {
            return;
        }

        if (banner.getW() == null && banner.getH() == null) {
            final List<Format> format = banner.getFormat();
            if (format == null) {
                throw new PreBidException("Impression with id: " + imp.getId()
                        + " has following error: Banner width and height is not provided and"
                        + " banner format is missing. At least one is required");
            }
            if (format.isEmpty()) {
                throw new PreBidException("Impression with id: " + imp.getId() + " has following error:"
                        + " Banner width and height is not provided and banner format array is empty. "
                        + "At least one is required");

            }
        }
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();

        try {
            final int statusCode = httpCall.getResponse().getStatusCode();
            if (statusCode == 204) {
                return Result.empty();
            }

            if (statusCode != 200) {
                return Result.withError(BidderError.badServerResponse(
                        "unexpected status code: " + statusCode + " " + httpCall.getResponse().getBody()
                ));
            }

            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(httpCall.getRequest().getPayload(), bidResponse, errors), errors);
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse, List<BidderError> errors) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidRequest, bidResponse, errors);
    }

    private static List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse,
                                                    List<BidderError> errors) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid,
                        getBidType(bid.getImpid(), bidRequest.getImp(), errors), bidResponse.getCur()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private static BidType getBidType(String impId, List<Imp> imps, List<BidderError> errors) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getBanner() == null && imp.getVideo() != null) {
                    return BidType.video;
                } else {
                    return BidType.banner;
                }
            }
        }

        errors.add(BidderError.badServerResponse(String.format("Failed to find impression with id: %s", impId)));
        return null;
    }

    private BidRequest createSingleRequest(Imp imp, ExtImpPrebid extPrebid, BidRequest request) {
        return request.toBuilder()
                .imp(Collections.singletonList(modifyImp(imp, extPrebid)))
                .build();
    }

    private Imp modifyImp(Imp imp, ExtImpPrebid extPrebid) {
        if (extPrebid != null && extPrebid.getIsRewardedInventory() == 1) {
            final Banner banner = imp.getBanner();
            final List<Integer> bannerBattr = banner == null ? null : banner.getBattr();
            final Imp.ImpBuilder impBuilder = imp.toBuilder();
            if (bannerBattr != null && !hasRewardedBattr(bannerBattr)) {
                bannerBattr.add(16);
                impBuilder.banner(imp.getBanner().toBuilder().battr(bannerBattr).build());
            }
            final Video video = imp.getVideo();
            final List<Integer> videoBattr = video == null ? null : video.getBattr();

            if (videoBattr != null && !hasRewardedBattr(videoBattr)) {
                videoBattr.add(16);
                impBuilder.video(imp.getVideo().toBuilder().battr(videoBattr).build());
            }

            return impBuilder.build();
        }

        return imp;
    }

    private boolean hasRewardedBattr(List<Integer> battr) {
        return battr.contains(16);
    }

    private ExtImpPrebid parsePrebid(ObjectNode ext) throws JsonProcessingException {
        return mapper.mapper().treeToValue(ext.get("prebid"), ExtImpPrebid.class);
    }

    private String buildEndpointUrl(ExtImpBidmachine extImpBidmachine) {
        return endpointUrl.replace("{{HOST}}", extImpBidmachine.getHost())
                .replace("{{PATH}}", extImpBidmachine.getPath())
                .replace("{{SELLER_ID}}", extImpBidmachine.getSellerId());
    }

    private ExtImpBidmachine parseAndValidateImpExt(Imp imp) {
        final ExtImpBidmachine extImpBidmachine;
        try {
            extImpBidmachine = mapper.mapper().convertValue(imp.getExt(), BIDMACHINE_EXT_TYPE_REFERENCE).getBidder();
        } catch (JsonParseException e) {
            throw new PreBidException("Missing bidder ext in impression with id: " + imp.getId());
        }

        if (StringUtils.isEmpty(extImpBidmachine.getSellerId())) {
            throw new PreBidException("Invalid/Missing sellerId");
        }

        if (StringUtils.isBlank(extImpBidmachine.getHost())) {
            throw new PreBidException("Invalid/Missing host");
        }

        if (StringUtils.isEmpty(extImpBidmachine.getPath())) {
            throw new PreBidException("Invalid/Missing path");
        }

        return extImpBidmachine;

    }

    private static MultiMap resolveHeaders() {
        final MultiMap headers = HttpUtil.headers();
        headers.add(HttpUtil.X_OPENRTB_VERSION_HEADER, "2.5");
        return headers;
    }
}
