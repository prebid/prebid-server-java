package org.prebid.server.bidder.richaudience;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import lombok.NonNull;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.richaudience.ExtImpRichaudience;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ObjectUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class RichaudienceBidder implements Bidder<BidRequest> {

    private static final String DEFAULT_CURRENCY = "USD";
    private static final TypeReference<ExtPrebid<?, ExtImpRichaudience>> RICHAUDIENCE_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public RichaudienceBidder(@NonNull String endpointUrl, @NonNull JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(endpointUrl);
        this.mapper = mapper;
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        if (!isDeviseIpPresent(request.getDevice())) {
            return Result.withError(BidderError.badInput("Device IP is required."));
        }

        final boolean isSecure;
        try {
            isSecure = HttpUtil.isSecure(ObjectUtil.getIfNotNull(request.getSite(), Site::getPage));
        } catch (IllegalArgumentException e) {
            return Result.withError(BidderError.badInput("Problem with Request.Site: " + e.getMessage()));
        }

        final List<BidderError> errors = new ArrayList<>();

        final List<Pair<Imp, ExtImpRichaudience>> impsWithExt = request.getImp().stream()
                .filter(Objects::nonNull)
                .filter(correctBannerFilter(errors::add))
                .map(imp -> Pair.of(imp, parseImpExt(imp, errors::add)))
                .filter(pair -> pair.getRight() != null)
                .collect(Collectors.toList());

        final List<Imp> modifiedImps = impsWithExt.stream()
                .map(pair -> modifyImp(pair.getLeft(), pair.getRight(), isSecure))
                .collect(Collectors.toList());

        final boolean isTest = impsWithExt.stream()
                .map(pair -> pair.getRight().getTest())
                .anyMatch(Boolean.TRUE::equals);

        final BidRequest modifiedRequest = modifyBidRequest(request, modifiedImps, isTest);
        return Result.of(List.of(createHttpRequest(modifiedRequest)), errors);
    }

    private static boolean isDeviseIpPresent(Device device) {
        return device != null && (StringUtils.isNotBlank(device.getIp()) || StringUtils.isNotBlank(device.getIpv6()));
    }

    private static Predicate<Imp> correctBannerFilter(Consumer<BidderError> onBrokenBanner) {
        return imp -> {
            final boolean isCorrect = isBannerCorrect(imp.getBanner());
            if (!isCorrect) {
                final String errorMessage = String.format("Banner W/H/Format is required. ImpId: %s", imp.getId());
                onBrokenBanner.accept(BidderError.badInput(errorMessage));
            }
            return isCorrect;
        };
    }

    private static boolean isBannerCorrect(Banner banner) {
        return banner != null
                && (banner.getW() != null || banner.getH() != null
                || CollectionUtils.isNotEmpty(banner.getFormat()));
    }

    private ExtImpRichaudience parseImpExt(Imp imp, Consumer<BidderError> onError) {
        final ObjectNode ext = imp.getExt();
        if (ext == null) {
            final String errorMessage = String.format("Ext not found. ImpId: %s", imp.getId());
            onError.accept(BidderError.badInput(errorMessage));
            return null;
        }

        try {
            return mapper.mapper().convertValue(imp.getExt(), RICHAUDIENCE_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            onError.accept(BidderError.badInput(e.getMessage()));
            return null;
        }
    }

    private static Imp modifyImp(Imp imp, ExtImpRichaudience richaudienceExt, boolean isSecure) {
        final String tagId = richaudienceExt.getPid();
        final String bidFloorCur = richaudienceExt.getBidFloorCur();

        return imp.toBuilder()
                .secure(BooleanUtils.toInteger(isSecure))
                .tagid(StringUtils.isNotBlank(tagId) ? tagId : imp.getTagid())
                .bidfloorcur(StringUtils.isNotBlank(bidFloorCur) ? bidFloorCur : DEFAULT_CURRENCY)
                .build();
    }

    private static BidRequest modifyBidRequest(BidRequest bidRequest, List<Imp> imps, boolean isTest) {
        return bidRequest.toBuilder()
                .imp(imps)
                .test(BooleanUtils.toInteger(isTest))
                .build();
    }

    private HttpRequest<BidRequest> createHttpRequest(BidRequest bidRequest) {
        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .headers(HttpUtil.headers())
                .uri(endpointUrl)
                .body(mapper.encodeToBytes(bidRequest))
                .payload(bidRequest)
                .build();
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = parseBidResponse(httpCall);
            return Result.withValues(extractBids(bidResponse));
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private BidResponse parseBidResponse(HttpCall<BidRequest> httpCall) throws DecodeException {
        return mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
    }

    private List<BidderBid> extractBids(BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> BidderBid.of(bid, BidType.banner, bidResponse.getCur()))
                .collect(Collectors.toList());
    }
}
