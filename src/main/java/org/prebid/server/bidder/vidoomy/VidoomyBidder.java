package org.prebid.server.bidder.vidoomy;

import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ObjectUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public class VidoomyBidder implements Bidder<BidRequest> {

    private static final String OPENRTB_VERSION = "2.5";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public VidoomyBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<HttpRequest<BidRequest>> requests = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                final Imp modifiedImp = modifyImp(imp);
                requests.add(createRequest(request, modifiedImp));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.of(requests, errors);
    }

    private static Imp modifyImp(Imp imp) {
        final Banner banner = imp.getBanner();
        if (banner == null) {
            return imp;
        }

        final Integer width = banner.getW();
        final Integer height = banner.getH();
        final List<Format> formats = banner.getFormat();
        validateBannerSizes(width, height, formats);

        final boolean useFormatSize = width == null || height == null;
        final Format firstFormat = useFormatSize ? formats.get(0) : null;
        return imp.toBuilder()
                .banner(banner.toBuilder()
                        .w(useFormatSize ? zeroIfFormatMeasureNull(firstFormat, Format::getW) : width)
                        .h(useFormatSize ? zeroIfFormatMeasureNull(firstFormat, Format::getH) : height)
                        .build())
                .build();
    }

    private static void validateBannerSizes(Integer width, Integer height, List<Format> formats) {
        final boolean sizePresent = width != null && height != null;
        if (sizePresent && (width == 0 || height == 0)) {
            throw new PreBidException(String.format("invalid sizes provided for Banner %s x %s", width, height));
        }

        if (!sizePresent && CollectionUtils.isEmpty(formats)) {
            throw new PreBidException("no sizes provided for Banner []");
        }
    }

    private static Integer zeroIfFormatMeasureNull(Format format, Function<Format, Integer> measureExtractor) {
        return ObjectUtils.defaultIfNull(ObjectUtil.getIfNotNull(format, measureExtractor), 0);
    }

    private HttpRequest<BidRequest> createRequest(BidRequest bidRequest, Imp imp) {
        final BidRequest outgoingRequest = bidRequest.toBuilder()
                .imp(Collections.singletonList(imp))
                .build();

        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .headers(headers(bidRequest.getDevice()))
                .payload(outgoingRequest)
                .body(mapper.encodeToBytes(outgoingRequest))
                .build();
    }

    private static MultiMap headers(Device device) {
        final MultiMap headers = HttpUtil.headers()
                .add(HttpUtil.X_OPENRTB_VERSION_HEADER, OPENRTB_VERSION);

        if (device != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER, device.getUa());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIpv6());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIp());
        }

        return headers;
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidResponse, bidRequest));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse, BidRequest bidRequest) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> BidderBid.of(bid, getBidType(bid.getImpid(), bidRequest.getImp()), bidResponse.getCur()))
                .collect(Collectors.toList());
    }

    private static BidType getBidType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (Objects.equals(impId, imp.getId())) {
                if (imp.getVideo() != null) {
                    return BidType.video;
                }
                if (imp.getBanner() != null) {
                    return BidType.banner;
                }
            }
        }

        throw new PreBidException(String.format("Unknown ad unit code '%s'", impId));
    }
}
