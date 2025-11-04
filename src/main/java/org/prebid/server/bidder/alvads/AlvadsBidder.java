package org.prebid.server.bidder.alvads;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.alvads.model.AlvaAdsImp;
import org.prebid.server.bidder.alvads.model.AlvaAdsSite;
import org.prebid.server.bidder.alvads.model.AlvadsRequestOrtb;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.alvads.AlvadsImpExt;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class AlvadsBidder implements Bidder<AlvadsRequestOrtb> {

    private static final TypeReference<ExtPrebid<?, AlvadsImpExt>> ALVADS_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public AlvadsBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public final Result<List<HttpRequest<AlvadsRequestOrtb>>> makeHttpRequests(BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();
        final List<HttpRequest<AlvadsRequestOrtb>> httpRequests = new ArrayList<>();

        for (Imp imp : bidRequest.getImp()) {
            try {
                final AlvadsImpExt impExt = parseImpExt(imp);
                final HttpRequest<AlvadsRequestOrtb> request = makeHttpRequest(bidRequest, imp, impExt);
                httpRequests.add(request);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return httpRequests.isEmpty() ? Result.withErrors(errors) : Result.of(httpRequests, errors);
    }

    private AlvadsImpExt parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), ALVADS_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Missing or invalid bidder ext in impression with id: " + imp.getId());
        }
    }

    private HttpRequest<AlvadsRequestOrtb> makeHttpRequest(BidRequest request, Imp imp, AlvadsImpExt impExt) {
        final AlvaAdsImp impObj = makeImp(imp);
        final AlvaAdsSite siteObj = makeSite(request.getSite(), impExt.getPublisherUniqueId());
        final AlvadsRequestOrtb alvadsRequest = AlvadsRequestOrtb.builder()
                .id(request.getId())
                .imp(List.of(impObj))
                .device(request.getDevice())
                .user(request.getUser())
                .regs(request.getRegs())
                .site(siteObj)
                .build();

        return HttpRequest.<AlvadsRequestOrtb>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .headers(HttpUtil.headers())
                .payload(alvadsRequest)
                .body(mapper.encodeToBytes(alvadsRequest))
                .impIds(alvadsRequest.getImp().stream().map(AlvaAdsImp::getId).collect(Collectors.toSet()))
                .build();
    }

    private static AlvaAdsImp makeImp(Imp imp) {
        final Banner banner = imp.getBanner();
        final Video video = imp.getVideo();

        return AlvaAdsImp.builder()
                .id(imp.getId())
                .tagid(imp.getTagid())
                .bidfloor(imp.getBidfloor())
                .banner(banner != null ? sizes(banner.getW(), banner.getH()) : null)
                .video(video != null ? sizes(video.getW(), video.getH()) : null)
                .build();
    }

    private static Map<String, Object> sizes(Integer w, Integer h) {
        final Map<String, Object> map = new HashMap<>();
        if (w != null) {
            map.put("w", w);
        }
        if (h != null) {
            map.put("h", h);
        }
        return map.isEmpty() ? null : map;
    }

    private static AlvaAdsSite makeSite(Site site, String publisherUniqueId) {
        final String page = site != null ? site.getPage() : null;
        return AlvaAdsSite.builder()
                .page(page)
                .ref(page)
                .publisher(Map.of("id", publisherUniqueId))
                .build();
    }

    @Override
    public final Result<List<BidderBid>> makeBids(BidderCall<AlvadsRequestOrtb> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidResponse, httpCall.getRequest().getPayload()));
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse("Failed to decode BidResponse: " + e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse, AlvadsRequestOrtb request) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidResponse, request);
    }

    private List<BidderBid> bidsFromResponse(BidResponse bidResponse, AlvadsRequestOrtb request) {
        return Optional.ofNullable(bidResponse.getSeatbid())
                .orElse(Collections.emptyList())
                .stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> makeBid(bid, request, bidResponse.getCur()))
                .toList();
    }

    private BidderBid makeBid(Bid bid, AlvadsRequestOrtb request, String currency) {
        final AlvaAdsImp imp = request.getImp().stream()
                .filter(i -> i.getId().equals(bid.getImpid()))
                .findFirst()
                .orElse(null);

        return BidderBid.of(bid, getBidType(bid, imp), currency);
    }

    private BidType getBidType(Bid bid, AlvaAdsImp imp) {
        if (imp != null && imp.getVideo() != null) {
            return BidType.video;
        }

        return Optional.ofNullable(getBidExt(bid))
                .map(ExtBidAlvads::getCrtype)
                .orElse(BidType.banner);
    }

    private ExtBidAlvads getBidExt(Bid bid) {
        try {
            return mapper.mapper().convertValue(bid.getExt(), ExtBidAlvads.class);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
