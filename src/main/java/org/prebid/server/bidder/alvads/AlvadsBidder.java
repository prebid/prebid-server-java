package org.prebid.server.bidder.alvads;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.alvads.model.AlvaAdsImp;
import org.prebid.server.bidder.alvads.model.AlvaAdsSite;
import org.prebid.server.bidder.alvads.model.AlvadsRequestORTB;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.alvads.AlvadsImpExt;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AlvadsBidder implements Bidder<AlvadsRequestORTB> {

    private final String endpointUrl;
    private final JacksonMapper mapper;
    private static final TypeReference<ExtPrebid<?, AlvadsImpExt>> ALVADS_EXT_TYPE_REFERENCE =
            new TypeReference<>() { };

    public AlvadsBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public final Result<List<HttpRequest<AlvadsRequestORTB>>> makeHttpRequests(BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();
        final List<HttpRequest<AlvadsRequestORTB>> httpRequests = new ArrayList<>();

        for (Imp imp : bidRequest.getImp()) {
            try {
                final AlvadsImpExt impExt = parseImpExt(imp);
                final HttpRequest<AlvadsRequestORTB> request = makeHttpRequest(bidRequest, imp, impExt);
                httpRequests.add(request);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (httpRequests.isEmpty()) {
            errors.add(BidderError.badInput("found no valid impressions"));
            return Result.withErrors(errors);
        }

        return Result.of(httpRequests, errors);
    }

    private HttpRequest<AlvadsRequestORTB> makeHttpRequest(BidRequest request, Imp imp, AlvadsImpExt impExt) {
        final String resolvedUrl = impExt.getEndPointUrl() != null ? impExt.getEndPointUrl() : endpointUrl;
        try {
            URI.create(resolvedUrl);
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Invalid endpoint URL: " + resolvedUrl, e);
        }

        Map<String, Object> bannerMap = null;
        if (imp.getBanner() != null) {
            bannerMap = new HashMap<>();
            if (imp.getBanner().getW() != null) {
                bannerMap.put("w", imp.getBanner().getW());
            }
            if (imp.getBanner().getH() != null) {
                bannerMap.put("h", imp.getBanner().getH());
            }
        }

        // Build video map safely
        Map<String, Object> videoMap = null;
        if (imp.getVideo() != null) {
            videoMap = new HashMap<>();
            if (imp.getVideo().getW() != null) {
                videoMap.put("w", imp.getVideo().getW());
            }
            if (imp.getVideo().getH() != null) {
                videoMap.put("h", imp.getVideo().getH());
            }
        }

        final AlvaAdsImp impObj = AlvaAdsImp.builder()
                .id(imp.getId())
                .tagid(imp.getTagid())
                .bidfloor(imp.getBidfloor())
                .banner(bannerMap)
                .video(videoMap)
                .build();

        final AlvaAdsSite siteObj = AlvaAdsSite.builder()
                .page(request.getSite() != null ? request.getSite().getPage() : null)
                .ref(request.getSite() != null ? request.getSite().getPage() : null)
                .publisher(Map.of("id", impExt.getPublisherUniqueId()))
                .build();

        final AlvadsRequestORTB alvadsRequest = AlvadsRequestORTB.builder()
                .id(request.getId())
                .imp(List.of(impObj))
                .device(request.getDevice())
                .user(request.getUser())
                .regs(request.getRegs())
                .site(siteObj)
                .build();

        return HttpRequest.<AlvadsRequestORTB>builder()
                .method(HttpMethod.POST)
                .uri(resolvedUrl)
                .headers(HttpUtil.headers())
                .payload(alvadsRequest)
                .body(mapper.encodeToBytes(alvadsRequest))
                .impIds(alvadsRequest.getImp().stream()
                        .map(AlvaAdsImp::getId)
                        .collect(Collectors.toSet()))
                .build();
    }

    private AlvadsImpExt parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), ALVADS_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Missing or invalid bidder ext in impression with id: " + imp.getId());
        }
    }

    @Override
    public final Result<List<BidderBid>> makeBids(BidderCall<AlvadsRequestORTB> httpCall,
                                                  BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(
                    httpCall.getResponse().getBody(),
                    BidResponse.class
            );
            return Result.withValues(extractBids(bidResponse, httpCall.getRequest().getPayload()));
        } catch (org.prebid.server.json.DecodeException e) {
            return Result.withError(BidderError.badServerResponse(
                    "Failed to decode BidResponse: " + e.getMessage()
            ));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse, AlvadsRequestORTB request) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidResponse, request);
    }

    private List<BidderBid> bidsFromResponse(BidResponse bidResponse, AlvadsRequestORTB request) {
        return bidResponse.getSeatbid().stream()
                .flatMap(seatBid -> seatBid == null || seatBid.getBid() == null
                        ? Stream.empty()
                        : seatBid.getBid().stream())
                .map(bid -> BidderBid.of(bid, getBidType(bid, request), bidResponse.getCur()))
                .toList();
    }

    private BidType getBidType(Bid bid, AlvadsRequestORTB request) {
        final ExtBidAlvads bidExt = getBidExt(bid);
        if (bidExt == null) {
            return BidType.banner;
        }
        final BidType crtype = bidExt.getCrtype();
        return request.getImp().get(0).getVideo() != null ? BidType.video : crtype == null ? BidType.banner : crtype;
    }

    private ExtBidAlvads getBidExt(Bid bid) {
        try {
            return mapper.mapper().convertValue(bid.getExt(), ExtBidAlvads.class);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

}
