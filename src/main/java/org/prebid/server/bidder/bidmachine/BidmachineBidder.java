package org.prebid.server.bidder.bidmachine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class BidmachineBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<ExtImpPrebid, ExtImpBidmachine>> BIDMACHINE_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<ExtImpPrebid, ExtImpBidmachine>>() {
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
                final ExtPrebid<ExtImpPrebid, ExtImpBidmachine> mappedExt = parseImpExt(imp);
                final BidRequest outgoingRequest = createRequest(imp, mappedExt.getPrebid(), request);

                httpRequests.add(HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(buildEndpointUrl(mappedExt.getBidder()))
                        .body(mapper.encodeToBytes(outgoingRequest))
                        .headers(resolveHeaders())
                        .payload(outgoingRequest)
                        .build());
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.of(httpRequests, errors);
    }

    private static void validateImp(Imp imp) {
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

    private BidRequest createRequest(Imp imp, ExtImpPrebid extPrebid, BidRequest request) {
        return request.toBuilder()
                .imp(Collections.singletonList(modifyImp(imp, extPrebid)))
                .build();
    }

    private Imp modifyImp(Imp imp, ExtImpPrebid extPrebid) {
        if (extPrebid != null && Objects.equals(extPrebid.getIsRewardedInventory(), 1)) {
            final Banner banner = imp.getBanner();
            final List<Integer> resolvedBannerBattr = banner == null ? null : resolveBattrList(banner.getBattr());

            final Video video = imp.getVideo();
            final List<Integer> resolvedVideoBattr = video == null ? null : resolveBattrList(video.getBattr());

            if (resolvedBannerBattr != null || resolvedVideoBattr != null) {
                final Imp.ImpBuilder impBuilder = imp.toBuilder();
                if (resolvedBannerBattr != null) {
                    impBuilder.banner(banner.toBuilder().battr(resolvedBannerBattr).build());
                }
                if (resolvedVideoBattr != null) {
                    impBuilder.video(video.toBuilder().battr(resolvedVideoBattr).build());
                }

                return impBuilder.build();
            }
        }

        return imp;
    }

    private static List<Integer> resolveBattrList(List<Integer> battr) {
        if (isMissedRewardedBattr(battr)) {
            final List<Integer> updatedBattr = battr == null ? new ArrayList<>() : new ArrayList<>(battr);
            updatedBattr.add(16);
            return updatedBattr;
        }

        return null;
    }

    private static boolean isMissedRewardedBattr(List<Integer> battr) {
        return battr == null || !battr.contains(16);
    }

    private String buildEndpointUrl(ExtImpBidmachine extImpBidmachine) {
        return endpointUrl.replace("{{HOST}}", extImpBidmachine.getHost())
                .replace("{{PATH}}", extImpBidmachine.getPath())
                .replace("{{SELLER_ID}}", extImpBidmachine.getSellerId());
    }

    private ExtPrebid<ExtImpPrebid, ExtImpBidmachine> parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), BIDMACHINE_EXT_TYPE_REFERENCE);
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Missing bidder ext in impression with id: " + imp.getId());
        }
    }

    private static MultiMap resolveHeaders() {
        final MultiMap headers = HttpUtil.headers();
        headers.add(HttpUtil.X_OPENRTB_VERSION_HEADER, "2.5");
        return headers;
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();

        try {
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

    private static List<BidderBid> bidsFromResponse(BidRequest bidRequest,
                                                    BidResponse bidResponse,
                                                    List<BidderError> errors) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> createBidderBid(bid, bidRequest.getImp(), bidResponse.getCur(), errors))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private static BidderBid createBidderBid(Bid bid, List<Imp> imps, String currency, List<BidderError> errors) {
        final BidType bidType = getBidType(bid.getImpid(), imps);
        if (bidType == null) {
            errors.add(BidderError.badServerResponse(
                    String.format("ignoring bid id=%s, request doesn't contain any valid "
                            + "impression with id=%s", bid.getId(), bid.getImpid())));

            return null;
        }

        return BidderBid.of(bid, bidType, currency);
    }

    private static BidType getBidType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (impId.equals(imp.getId())) {
                if (imp.getBanner() == null && imp.getVideo() != null) {
                    return BidType.video;
                } else {
                    return BidType.banner;
                }
            }
        }

        return null;
    }
}
