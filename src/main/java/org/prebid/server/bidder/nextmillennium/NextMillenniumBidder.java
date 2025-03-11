package org.prebid.server.bidder.nextmillennium;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
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
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidServer;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredRequest;
import org.prebid.server.proto.openrtb.ext.request.nextmillennium.ExtImpNextMillennium;
import org.prebid.server.proto.openrtb.ext.request.nextmillennium.ExtRequestNextMillennium;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ObjectUtil;
import org.prebid.server.version.PrebidVersionProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class NextMillenniumBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpNextMillennium>> NEXTMILLENNIUM_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private static final String NM_ADAPTER_VERSION = "v1.0.0";

    private final String endpointUrl;
    private final JacksonMapper mapper;
    private final List<String> nmmFlags;
    private final PrebidVersionProvider versionProvider;

    public NextMillenniumBidder(String endpointUrl,
                                JacksonMapper mapper,
                                List<String> nmmFlags,
                                PrebidVersionProvider versionProvider) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
        this.nmmFlags = nmmFlags;
        this.versionProvider = Objects.requireNonNull(versionProvider);
    }

    @Override
    public final Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();
        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();

        for (Imp imp : bidRequest.getImp()) {
            final ExtImpNextMillennium extImpNextMillennium;
            try {
                extImpNextMillennium = convertExt(imp.getExt());
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
                continue;
            }
            httpRequests.add(makeHttpRequest(updateBidRequest(bidRequest, extImpNextMillennium)));
        }

        return errors.isEmpty() ? Result.withValues(httpRequests) : Result.withErrors(errors);
    }

    private ExtImpNextMillennium convertExt(ObjectNode impExt) {
        try {
            return mapper.mapper().convertValue(impExt, NEXTMILLENNIUM_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private BidRequest updateBidRequest(BidRequest bidRequest, ExtImpNextMillennium ext) {
        final ExtStoredRequest storedRequest = ExtStoredRequest.of(resolveStoredRequestId(bidRequest, ext));
        final ExtRequest requestExt = bidRequest.getExt();
        final ExtRequestPrebid existingPrebid = requestExt != null ? requestExt.getPrebid() : null;
        final ExtRequestPrebidServer existingServer = existingPrebid != null ? existingPrebid.getServer() : null;

        final ExtRequestPrebid createdExtRequestPrebid = ExtRequestPrebid.builder()
                .storedrequest(storedRequest)
                .server(existingServer)
                .build();

        final ExtRequest extRequest = ExtRequest.of(createdExtRequestPrebid);

        final ObjectNode nextMillenniumNode = mapper.mapper().valueToTree(
                ExtRequestNextMillennium.of(nmmFlags, NM_ADAPTER_VERSION, versionProvider.getNameVersionRecord()));

        extRequest.addProperty("nextMillennium", nextMillenniumNode);

        final Imp firstImp = bidRequest.getImp().getFirst();
        final ObjectNode updatedImpExt = mapper.mapper().createObjectNode();
        updatedImpExt.set("nextMillennium", nextMillenniumNode);

        final ObjectNode prebidNode = mapper.mapper().createObjectNode();
        prebidNode.set("storedrequest", mapper.mapper().valueToTree(storedRequest));
        updatedImpExt.set("prebid", prebidNode);

        final Imp updatedImp = firstImp.toBuilder()
                .ext(updatedImpExt)
                .build();

        final List<Imp> updatedImps = new ArrayList<>(bidRequest.getImp());
        updatedImps.set(0, updatedImp);

        return bidRequest.toBuilder()
                .imp(updatedImps)
                .ext(extRequest)
                .build();
    }

    private static String resolveStoredRequestId(BidRequest bidRequest, ExtImpNextMillennium extImpNextMillennium) {
        final String groupId = extImpNextMillennium.getGroupId();
        if (StringUtils.isEmpty(groupId)) {
            return extImpNextMillennium.getPlacementId();
        }

        final String size = formattedSizeFromBanner(bidRequest.getImp().getFirst().getBanner());
        final String domain = ObjectUtils.firstNonNull(
                ObjectUtil.getIfNotNull(bidRequest.getSite(), Site::getDomain),
                ObjectUtil.getIfNotNull(bidRequest.getApp(), App::getDomain),
                StringUtils.EMPTY);

        return "g%s;%s;%s".formatted(groupId, size, domain);
    }

    private static String formattedSizeFromBanner(Banner banner) {
        if (banner == null) {
            return StringUtils.EMPTY;
        }

        final List<Format> formats = banner.getFormat();
        final Format firstFormat = CollectionUtils.isNotEmpty(formats) ? formats.getFirst() : null;

        return ObjectUtils.firstNonNull(
                formatSize(
                        ObjectUtil.getIfNotNull(firstFormat, Format::getW),
                        ObjectUtil.getIfNotNull(firstFormat, Format::getH)),
                formatSize(banner.getW(), banner.getH()),
                StringUtils.EMPTY);
    }

    private static String formatSize(Integer w, Integer h) {
        return w != null && h != null
                ? "%dx%d".formatted(w, h)
                : null;
    }

    private HttpRequest<BidRequest> makeHttpRequest(BidRequest bidRequest) {
        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .headers(headers())
                .payload(bidRequest)
                .body(mapper.encodeToBytes(bidRequest))
                .build();
    }

    private static MultiMap headers() {
        return HttpUtil.headers()
                .add(HttpUtil.X_OPENRTB_VERSION_HEADER, "2.5");
    }

    @Override
    public final Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        final List<BidderError> bidderErrors = new ArrayList<>();
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            if (CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
                return Result.empty();
            }
            return Result.of(bidsFromResponse(bidResponse, bidderErrors), bidderErrors);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> bidsFromResponse(BidResponse bidResponse, List<BidderError> bidderErrors) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> resolveBidderBid(bidResponse, bidderErrors, bid))
                .filter(Objects::nonNull)
                .toList();
    }

    private static BidderBid resolveBidderBid(BidResponse bidResponse, List<BidderError> bidderErrors, Bid bid) {
        final BidType bidType = getBidType(bid, bidderErrors);
        if (bidType == null) {
            return null;
        }

        return BidderBid.of(bid, bidType, bidResponse.getCur());
    }

    private static BidType getBidType(Bid bid, List<BidderError> bidderErrors) {
        final Integer markupType = bid.getMtype();
        if (markupType == null) {
            bidderErrors.add(BidderError.badServerResponse("Missing MType for bid: " + bid.getId()));
            return null;
        }

        return switch (markupType) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            default -> {
                bidderErrors.add(BidderError.badServerResponse(
                        "Unable to fetch mediaType " + bid.getMtype() + " in multi-format: " + bid.getImpid()));
                yield null;
            }
        };
    }
}
