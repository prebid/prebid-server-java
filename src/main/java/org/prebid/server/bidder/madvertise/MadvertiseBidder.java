package org.prebid.server.bidder.madvertise;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
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
import org.prebid.server.proto.openrtb.ext.request.madvertise.ExtImpMadvertise;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Madvertise {@link Bidder} implementation
 */
public class MadvertiseBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpMadvertise>> MADVERTISE_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpMadvertise>>() {
            };

    private static final int ZONE_ID_MIN_LENGTH = 7;
    private static final String X_OPENRTB_VERSION = "2.5";
    private static final String ZONE_ID_MACRO = "{{ZoneId}}";

    private final JacksonMapper mapper;
    private final String endpointUrl;

    public MadvertiseBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final Set<String> zoneIds = new HashSet<>();

        for (Imp imp : request.getImp()) {
            try {
                zoneIds.add(parseImpExt(imp).getZoneId());
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        final String zoneId = zoneIds.stream().findFirst().orElseThrow();
        if (zoneIds.size() != 1) {
            errors.add(BidderError.badInput("Ambiguous zone id provided"));
        }

        if (!errors.isEmpty()) {
            return Result.withErrors(errors);
        }
        return Result.withValue(createRequest(request, zoneId));
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(bidResponse), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private ExtImpMadvertise parseImpExt(Imp imp) {
        final ExtImpMadvertise extImpMadvertise;

        try {
            extImpMadvertise = mapper.mapper().convertValue(imp.getExt(), MADVERTISE_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(String.format("Missing bidder ext in impression with id: %s", imp.getId()));
        }

        if (StringUtils.isBlank(extImpMadvertise.getZoneId())) {
            throw new PreBidException(String.format(
                    "Required Madvertise ext parameter %s is missing in impression with id: %s",
                    "zoneId", imp.getId()));
        }

        if (extImpMadvertise.getZoneId().length() < ZONE_ID_MIN_LENGTH) {
            throw new PreBidException(String.format(
                    "Length on zoneId is less then min length of %s in impression with id: %s",
                    ZONE_ID_MIN_LENGTH, imp.getId()));
        }
        return extImpMadvertise;
    }

    private HttpRequest<BidRequest> createRequest(BidRequest request, String zoneID) {
        final String url = endpointUrl.replace(ZONE_ID_MACRO, HttpUtil.encodeUrl(zoneID));

        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(url)
                .headers(resolveHeaders(request.getDevice()))
                .payload(request)
                .body(mapper.encode(request))
                .build();
    }

    private static MultiMap resolveHeaders(Device device) {
        final MultiMap headers = HttpUtil.headers();

        headers.add(HttpUtil.X_OPENRTB_VERSION_HEADER, X_OPENRTB_VERSION);
        if (device != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER, device.getUa());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIp());
        }

        return headers;
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, getBidMediaType(bid), bidResponse.getCur()))
                .collect(Collectors.toList());
    }

    private static BidType getBidMediaType(Bid bid) {
        final List<Integer> videoBidAttrs = Arrays.asList(16, 6, 7);

        for (int attr : bid.getAttr()) {
            if (videoBidAttrs.contains(attr)) {
                return BidType.video;
            }
        }

        return BidType.banner;
    }
}
