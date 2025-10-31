package org.prebid.server.bidder.adkernel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.adkernel.ExtImpAdkernel;
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
import java.util.stream.Stream;

public class AdkernelBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpAdkernel>> ADKERNEL_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private static final String ZONE_ID_MACRO = "{{ZoneId}}";

    private static final String MF_SUFFIX = "__mf";
    private static final String MF_SUFFIX_BANNER = "b" + MF_SUFFIX;
    private static final String MF_SUFFIX_VIDEO = "v" + MF_SUFFIX;
    private static final String MF_SUFFIX_AUDIO = "a" + MF_SUFFIX;
    private static final String MF_SUFFIX_NATIVE = "n" + MF_SUFFIX;

    private static final int MF_SUFFIX_LENGTH = MF_SUFFIX.length() + 1;

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public AdkernelBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final Map<ExtImpAdkernel, List<Imp>> pubToImps = new HashMap<>();
        for (Imp imp : request.getImp()) {
            try {
                processImp(imp, pubToImps);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (hasNoImpressions(pubToImps)) {
            return Result.withErrors(errors);
        }

        final BidRequest.BidRequestBuilder requestBuilder = request.toBuilder();
        final List<HttpRequest<BidRequest>> httpRequests = pubToImps.entrySet().stream()
                .map(extAndImp -> createHttpRequest(extAndImp, requestBuilder, request.getSite(), request.getApp()))
                .toList();

        return Result.of(httpRequests, errors);
    }

    private void processImp(Imp imp, Map<ExtImpAdkernel, List<Imp>> pubToImps) {
        validateImp(imp);
        final ExtImpAdkernel extImpAdkernel = parseAndValidateImpExt(imp);
        final Imp updatedImp = imp.toBuilder().ext(null).build();
        dispatchImpression(updatedImp, extImpAdkernel, pubToImps);
    }

    private static void validateImp(Imp imp) {
        if (imp.getBanner() == null && imp.getVideo() == null && imp.getXNative() == null) {
            throw new PreBidException("Invalid imp id=" + imp.getId()
                    + ". Expected imp.banner / imp.video / imp.native");
        }
    }

    private ExtImpAdkernel parseAndValidateImpExt(Imp imp) {
        final ExtImpAdkernel extImpAdkernel;
        try {
            extImpAdkernel = mapper.mapper().convertValue(imp.getExt(), ADKERNEL_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }

        final Integer zoneId = extImpAdkernel.getZoneId();
        if (zoneId == null || zoneId < 1) {
            throw new PreBidException("Invalid zoneId value: %d. Ignoring imp id=%s".formatted(zoneId, imp.getId()));
        }

        return extImpAdkernel;
    }

    private static void dispatchImpression(Imp imp, ExtImpAdkernel extImpAdkernel,
                                           Map<ExtImpAdkernel, List<Imp>> pubToImp) {

        pubToImp.computeIfAbsent(extImpAdkernel, ignored -> new ArrayList<>())
                .addAll(splitByMediaType(imp));
    }

    private static List<Imp> splitByMediaType(Imp imp) {
        final long mediaTypesCount = Stream.of(imp.getVideo(), imp.getAudio(), imp.getBanner(), imp.getXNative())
                .filter(Objects::nonNull)
                .count();

        if (mediaTypesCount == 1) {
            return Collections.singletonList(imp);
        }

        final List<Imp> singleFormatImps = new ArrayList<>();

        if (imp.getBanner() != null) {
            singleFormatImps.add(
                    imp.toBuilder()
                            .id(imp.getId() + MF_SUFFIX_BANNER)
                            .video(null)
                            .audio(null)
                            .xNative(null)
                            .build());
        }

        if (imp.getVideo() != null) {
            singleFormatImps.add(
                    imp.toBuilder()
                            .id(imp.getId() + MF_SUFFIX_VIDEO)
                            .banner(null)
                            .audio(null)
                            .xNative(null)
                            .build());
        }

        if (imp.getAudio() != null) {
            singleFormatImps.add(
                    imp.toBuilder()
                            .id(imp.getId() + MF_SUFFIX_AUDIO)
                            .banner(null)
                            .video(null)
                            .xNative(null)
                            .build());
        }

        if (imp.getXNative() != null) {
            singleFormatImps.add(
                    imp.toBuilder()
                            .id(imp.getId() + MF_SUFFIX_NATIVE)
                            .banner(null)
                            .video(null)
                            .audio(null)
                            .build());
        }

        return singleFormatImps;
    }

    private static boolean hasNoImpressions(Map<ExtImpAdkernel, List<Imp>> pubToImps) {
        return pubToImps.values().stream()
                .allMatch(CollectionUtils::isEmpty);
    }

    private HttpRequest<BidRequest> createHttpRequest(Map.Entry<ExtImpAdkernel, List<Imp>> extAndImp,
                                                      BidRequest.BidRequestBuilder requestBuilder,
                                                      Site site,
                                                      App app) {

        final ExtImpAdkernel impExt = extAndImp.getKey();
        final String uri = endpointUrl.replace(ZONE_ID_MACRO, HttpUtil.encodeUrl(impExt.getZoneId().toString()));

        final MultiMap headers = HttpUtil.headers().add(HttpUtil.X_OPENRTB_VERSION_HEADER, "2.5");

        final BidRequest outgoingRequest = createBidRequest(extAndImp.getValue(), requestBuilder, site, app);

        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(uri)
                .headers(headers)
                .body(mapper.encodeToBytes(outgoingRequest))
                .payload(outgoingRequest)
                .build();
    }

    private static BidRequest createBidRequest(List<Imp> imps,
                                               BidRequest.BidRequestBuilder requestBuilder,
                                               Site site,
                                               App app) {

        requestBuilder.imp(imps);

        if (site != null) {
            requestBuilder.site(site.toBuilder().publisher(null).build());
        } else {
            requestBuilder.app(app.toBuilder().publisher(null).build());
        }
        return requestBuilder.build();
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidResponse));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse) {
        if (bidResponse == null || bidResponse.getSeatbid() == null) {
            return Collections.emptyList();
        }
        if (bidResponse.getSeatbid().size() != 1) {
            throw new PreBidException("Invalid SeatBids count: " + bidResponse.getSeatbid().size());
        }
        return bidsFromResponse(bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .map(SeatBid::getBid)
                .flatMap(Collection::stream)
                .map(bid -> makeBidderBid(bid, bidResponse.getCur()))
                .toList();
    }

    private static BidderBid makeBidderBid(Bid bid, String currency) {
        final Optional<String> mfSuffix = getMfSuffix(bid.getImpid());
        final Bid updatedBid = mfSuffix.map(suffix -> removeMfSuffixFromImpId(bid, suffix)).orElse(bid);

        return BidderBid.of(updatedBid, getBidType(bid), currency);
    }

    private static Optional<String> getMfSuffix(String impId) {
        return Optional.of(impId.indexOf(MF_SUFFIX) - 1)
                .filter(index -> index >= 0)
                .map(index -> impId.substring(index, index + MF_SUFFIX_LENGTH));
    }

    private static Bid removeMfSuffixFromImpId(Bid bid, String mfSuffix) {
        return bid.toBuilder()
                .impid(bid.getImpid().replace(mfSuffix, ""))
                .build();
    }

    private static BidType getBidType(Bid bid) {
        final Integer markupType = bid.getMtype();
        if (markupType == null) {
            throw new PreBidException("Missing MType for bid: " + bid.getId());
        }

        return switch (markupType) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            case 3 -> BidType.audio;
            case 4 -> BidType.xNative;
            default -> throw new PreBidException(
                    "Unsupported MType %d".formatted(markupType));
        };
    }
}
