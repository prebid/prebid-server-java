package org.prebid.server.bidder.nextmillennium;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
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
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredRequest;
import org.prebid.server.proto.openrtb.ext.request.nextmillennium.ExtImpNextMillennium;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ObjectUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class NextMillenniumBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpNextMillennium>> NEXTMILLENNIUM_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public NextMillenniumBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    private BidRequest updateBidRequest(BidRequest bidRequest, ExtImpNextMillennium ext) {

        final ExtRequest extRequest = ExtRequest.of(ExtRequestPrebid.builder()
                .storedrequest(ExtStoredRequest.of(resolveStoredRequestId(bidRequest, ext)))
                .build());

        final ObjectNode impExt = mapper.mapper().valueToTree(extRequest);

        final List<Imp> imps = bidRequest.getImp().stream()
                .map(imp -> imp.toBuilder().ext(impExt).build())
                .toList();

        return bidRequest.toBuilder().imp(imps).ext(extRequest).build();
    }

    private static String resolveStoredRequestId(BidRequest bidRequest, ExtImpNextMillennium extImpNextMillennium) {
        final String groupId = extImpNextMillennium.getGroupId();
        if (StringUtils.isEmpty(groupId)) {
            return extImpNextMillennium.getPlacementId();
        }

        final String size = formattedSizeFromBanner(bidRequest.getImp().get(0).getBanner());
        final String domain = ObjectUtils.firstNonNull(
                ObjectUtil.getIfNotNull(bidRequest.getSite(), Site::getDomain),
                ObjectUtil.getIfNotNull(bidRequest.getApp(), App::getDomain),
                StringUtils.EMPTY);

        return "g%s;%s;%s".formatted(groupId, size, domain);
    }

    @Override
    public final Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();
        final List<ExtImpNextMillennium> impExts = getImpExts(bidRequest, errors);

        return errors.isEmpty()
                ? Result.withValues(makeRequests(bidRequest, impExts))
                : Result.withErrors(errors);
    }

    private List<ExtImpNextMillennium> getImpExts(BidRequest bidRequest, List<BidderError> errors) {
        return bidRequest.getImp().stream()
                .map(imp -> convertExt(imp, errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private ExtImpNextMillennium convertExt(Imp imp, List<BidderError> errors) {
        try {
            return mapper.mapper()
                    .convertValue(imp.getExt(), NEXTMILLENNIUM_EXT_TYPE_REFERENCE)
                    .getBidder();
        } catch (IllegalArgumentException e) {
            errors.add(BidderError.badInput(e.getMessage()));
        }
        return null;
    }

    private List<HttpRequest<BidRequest>> makeRequests(BidRequest bidRequest, List<ExtImpNextMillennium> extImps) {
        return extImps.stream()
                .map(extImp -> makeHttpRequest(updateBidRequest(bidRequest, extImp)))
                .toList();
    }

    private static String formattedSizeFromBanner(Banner banner) {
        if (banner == null) {
            return StringUtils.EMPTY;
        }

        final List<Format> formats = banner.getFormat();
        final Format firstFormat = CollectionUtils.isNotEmpty(formats) ? formats.get(0) : null;

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
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            if (CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
                return Result.empty();
            }
            return Result.withValues(bidsFromResponse(bidResponse));
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> bidsFromResponse(BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, BidType.banner, bidResponse.getCur()))
                .toList();
    }
}
