package org.prebid.server.bidder.conversant;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderUtil;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.conversant.ExtImpConversant;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Conversant {@link Bidder} implementation.
 */
public class ConversantBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpConversant>> CONVERSANT_EXT_TYPE_REFERENCE = new
            TypeReference<ExtPrebid<?, ExtImpConversant>>() {
            };

    // List of API frameworks supported by the publisher
    private static final Set<Integer> APIS = IntStream.range(1, 7).boxed().collect(Collectors.toSet());

    // Options for the various bid response protocols that could be supported by an exchange
    private static final Set<Integer> PROTOCOLS = IntStream.range(1, 11).boxed().collect(Collectors.toSet());

    // Position of the ad as a relative measure of visibility or prominence
    private static final Set<Integer> AD_POSITIONS = IntStream.range(0, 8).boxed().collect(Collectors.toSet());

    private static final String DEFAULT_BID_CURRENCY = "USD";
    private static final String DISPLAY_MANAGER = "prebid-s2s";
    private static final String DISPLAY_MANAGER_VER = "1.0.1";

    private final String endpointUrl;

    public ConversantBidder(String endpointUrl) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        if (bidRequest.getApp() != null) {
            return Result.emptyWithError(BidderError.badInput("Conversant doesn't support App requests"));
        }

        final List<BidderError> errors = new ArrayList<>();
        final BidRequest outgoingRequest;
        try {
            outgoingRequest = createBidRequest(bidRequest, errors);
        } catch (PreBidException e) {
            errors.add(BidderError.badInput(e.getMessage()));
            return Result.of(Collections.emptyList(), errors);
        }

        final String body = Json.encode(outgoingRequest);

        return Result.of(Collections.singletonList(
                HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(endpointUrl)
                        .body(body)
                        .headers(BidderUtil.headers())
                        .payload(outgoingRequest)
                        .build()),
                errors);
    }

    private static BidRequest createBidRequest(BidRequest bidRequest, List<BidderError> errors) {
        final List<Imp> modifiedImps = new ArrayList<>();
        Integer extMobile = null;
        String extSiteId = null;
        for (Imp imp : bidRequest.getImp()) {
            try {
                validateImp(imp);
                final ExtImpConversant impExt = parseAndValidateImpExt(imp);
                modifiedImps.add(modifyImp(imp, impExt));
                extSiteId = impExt.getSiteId();
                if (impExt.getMobile() != null) {
                    extMobile = impExt.getMobile();
                }
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }
        if (modifiedImps.isEmpty()) {
            throw new PreBidException("No valid impressions");
        }

        return bidRequest.toBuilder()
                .imp(modifiedImps)
                .site(modifySite(bidRequest.getSite(), extSiteId, extMobile))
                .build();
    }

    private static void validateImp(Imp imp) {
        if (imp.getBanner() == null && imp.getVideo() == null) {
            throw new PreBidException(String.format("Invalid MediaType. Conversant supports only Banner and Video. "
                    + "Ignoring ImpID=%s", imp.getId()));
        }
    }

    private static ExtImpConversant parseAndValidateImpExt(Imp imp) {
        final ExtImpConversant extImpConversant;
        try {
            extImpConversant = Json.mapper.<ExtPrebid<?, ExtImpConversant>>convertValue(imp.getExt(),
                    CONVERSANT_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }

        if (StringUtils.isBlank(extImpConversant.getSiteId())) {
            throw new PreBidException("Missing site id");
        }

        return extImpConversant;
    }

    private static Imp modifyImp(Imp imp, ExtImpConversant impExt) {
        final BigDecimal extBidfloor = impExt.getBidfloor();
        final String extTagId = impExt.getTagId();
        final Integer extSecure = impExt.getSecure();
        final boolean shouldChangeSecure = extSecure != null && (imp.getSecure() == null || imp.getSecure() == 0);
        final Banner impBanner = imp.getBanner();
        final Integer extPosition = impExt.getPosition();
        final Video impVideo = imp.getVideo();

        final Imp.ImpBuilder impBuilder = imp.toBuilder();

        if (impBanner != null && extPosition != null) {
            impBuilder.banner(impBanner.toBuilder().pos(extPosition).build());
        }

        return impBuilder
                .displaymanager(DISPLAY_MANAGER)
                .displaymanagerver(DISPLAY_MANAGER_VER)
                .bidfloor(extBidfloor != null ? extBidfloor : imp.getBidfloor())
                .tagid(extTagId != null ? extTagId : imp.getTagid())
                .secure(shouldChangeSecure ? extSecure : imp.getSecure())
                .video(impVideo != null ? modifyVideo(impVideo, impExt) : null)
                .build();
    }

    private static Video modifyVideo(Video video, ExtImpConversant impExt) {
        final List<String> extMimes = impExt.getMimes();
        final Integer extMaxduration = impExt.getMaxduration();
        final Integer extPosition = impExt.getPosition();
        final List<Integer> extProtocols = impExt.getProtocols();
        final List<Integer> extApi = impExt.getApi();
        return video.toBuilder()
                .mimes(extMimes != null ? extMimes : video.getMimes())
                .maxduration(extMaxduration != null ? extMaxduration : video.getMaxduration())
                .pos(makePosition(extPosition, video.getPos()))
                .api(makeApi(extApi, video.getApi()))
                .protocols(makeProtocols(extProtocols, video.getProtocols()))
                .build();
    }

    private static Integer makePosition(Integer position, Integer videoPos) {
        return isValidPosition(position) ? position : isValidPosition(videoPos) ? videoPos : null;
    }

    private static boolean isValidPosition(Integer position) {
        return position != null && AD_POSITIONS.contains(position);
    }

    private static List<Integer> makeApi(List<Integer> extApi, List<Integer> videoApi) {
        final List<Integer> protocols = CollectionUtils.isNotEmpty(extApi) ? extApi : videoApi;
        return CollectionUtils.isNotEmpty(protocols)
                ? protocols.stream().filter(APIS::contains).collect(Collectors.toList()) : videoApi;
    }

    private static List<Integer> makeProtocols(List<Integer> extProtocols, List<Integer> videoProtocols) {
        final List<Integer> protocols = CollectionUtils.isNotEmpty(extProtocols) ? extProtocols : videoProtocols;
        return CollectionUtils.isNotEmpty(protocols)
                ? protocols.stream().filter(PROTOCOLS::contains).collect(Collectors.toList()) : videoProtocols;
    }

    private static Site modifySite(Site site, String extSiteId, Integer extMobile) {
        final Site.SiteBuilder siteBuilder = site == null ? Site.builder() : site.toBuilder();
        if (extMobile != null) {
            siteBuilder.mobile(extMobile);
        }
        return siteBuilder
                .id(extSiteId)
                .build();
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = Json.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(httpCall.getRequest().getPayload(), bidResponse), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        return bidResponse == null || bidResponse.getSeatbid() == null
                ? Collections.emptyList()
                : bidsFromResponse(bidRequest, bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, getType(bid.getImpid(), bidRequest.getImp()), DEFAULT_BID_CURRENCY))
                .collect(Collectors.toList());
    }

    private static BidType getType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId) && imp.getVideo() != null) {
                return BidType.video;
            }
        }
        return BidType.banner;
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}
