package org.prebid.server.bidder.aps;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
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
import org.prebid.server.proto.openrtb.ext.request.aps.ExtImpAps;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class ApsBidder implements Bidder<BidRequest> {

    private static final String ADAPTER_VERSION = "1.0.0";
    private static final String SOURCE = "prebid-server";
    private static final String DEFAULT_CURRENCY = "USD";
    private static final String DEFAULT_REGION = "na";
    private static final Set<String> VALID_REGIONS = Set.of("na", "eu", "fe");
    private static final String REGION_MACRO = "{{Region}}";

    private static final TypeReference<ExtPrebid<Void, ExtImpAps>> APS_EXT_TYPE_REFERENCE =
            new TypeReference<>() { };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public ApsBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = Objects.requireNonNull(endpointUrl);
        this.mapper = Objects.requireNonNull(mapper);
    }

    /*
     * Sample OpenRTB request this adapter accepts (dummy values):
     * {
     *   "id": "req-1",
     *   "test": 1,
     *   "site": {"page": "https://example.com"},
     *   "imp": [
     *     {"id": "imp-1", "banner": {"format": [{"w": 300, "h": 250}]},
     *      "ext": {"prebid": {"bidder": {"aps": {"accountID": "1234", "region": "na"}}}}},
     *     {"id": "imp-2", "video": {"mimes": ["video/mp4"], "w": 640, "h": 480, "protocols": [2, 3, 5, 6]},
     *      "ext": {"prebid": {"bidder": {"aps": {"accountID": "1234"}}}}}
     *   ]
     * }
     * accountID is a per-imp bidder param (or set once via ext.prebid.bidderparams.aps.accountID); all imps
     * must resolve to the same account. region is an optional per-imp param (default na); it fills the
     * endpoint macro and is validated against a fixed set of valid regions (na, eu, fe); others are rejected.
     * test:1 appends amzn_debug_mode=1 to the endpoint.
     */
    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        if (bidRequest.getApp() != null) {
            return Result.withError(BidderError.badInput(
                    "the APS adapter supports web (site) inventory only; app requests are not supported"));
        }

        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> validImps = new ArrayList<>();
        String accountID = null;
        String region = DEFAULT_REGION;

        for (Imp imp : bidRequest.getImp()) {
            final Imp sanitizedImp;
            final ImpParams params;
            try {
                sanitizedImp = sanitizeMediaTypes(imp);
                params = parseImpParams(imp);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
                continue;
            }

            if (accountID == null) {
                accountID = params.accountID();
            } else if (!accountID.equals(params.accountID())) {
                errors.add(BidderError.badInput(
                        "imp %s: all imps in a request must use the same APS accountID (got %s and %s)"
                                .formatted(imp.getId(), accountID, params.accountID())));
                return Result.withErrors(errors);
            }
            region = params.region();

            validImps.add(sanitizedImp.getBanner() != null
                    ? backfillBannerSize(sanitizedImp)
                    : sanitizedImp);
        }

        if (validImps.isEmpty()) {
            return Result.withErrors(errors);
        }

        final String resolvedEndpoint = resolveEndpoint(region);
        final String requestUrl = isTestMode(bidRequest) ? appendDebugMode(resolvedEndpoint) : resolvedEndpoint;
        final HttpRequest<BidRequest> httpRequest = BidderUtil.defaultRequest(
                modifyBidRequest(bidRequest, validImps, accountID), requestUrl, mapper);

        return Result.of(Collections.singletonList(httpRequest), errors);
    }

    private ImpParams parseImpParams(Imp imp) {
        final ExtImpAps extImpAps;
        try {
            extImpAps = mapper.mapper().convertValue(imp.getExt(), APS_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(
                    "imp %s: invalid aps bidder params: %s".formatted(imp.getId(), e.getMessage()));
        }
        final String accountID = extImpAps == null ? null : StringUtils.trimToNull(extImpAps.getAccountID());
        if (accountID == null) {
            throw new PreBidException(
                    "imp %s: the APS bidder param \"accountID\" is required".formatted(imp.getId()));
        }
        final String rawRegion = StringUtils.trimToNull(extImpAps.getRegion());
        final String region = rawRegion == null ? DEFAULT_REGION : rawRegion;
        if (!VALID_REGIONS.contains(region)) {
            throw new PreBidException(
                    "imp %s: invalid APS region \"%s\"".formatted(imp.getId(), region));
        }
        return new ImpParams(accountID, region);
    }

    private String resolveEndpoint(String region) {
        return endpointUrl.replace(REGION_MACRO, region);
    }

    private record ImpParams(String accountID, String region) {
    }

    private static boolean isTestMode(BidRequest bidRequest) {
        return Objects.equals(bidRequest.getTest(), 1);
    }

    // Appends the amzn_debug_mode=1 query param for test requests.
    private static String appendDebugMode(String url) {
        return url + (url.contains("?") ? "&" : "?") + "amzn_debug_mode=1";
    }

    private static Imp sanitizeMediaTypes(Imp imp) {
        if (imp.getBanner() == null && imp.getVideo() == null) {
            throw new PreBidException(
                    "imp %s: the APS adapter supports only banner and video media types".formatted(imp.getId()));
        }
        return imp.toBuilder()
                .audio(null)
                .xNative(null)
                .build();
    }

    private static Imp backfillBannerSize(Imp imp) {
        final Banner banner = imp.getBanner();
        if (CollectionUtils.isEmpty(banner.getFormat())
                || (banner.getW() != null && banner.getH() != null)) {
            return imp;
        }

        final Format firstFormat = banner.getFormat().get(0);
        final Banner modifiedBanner = banner.toBuilder()
                .w(firstFormat.getW())
                .h(firstFormat.getH())
                .build();
        return imp.toBuilder().banner(modifiedBanner).build();
    }

    private BidRequest modifyBidRequest(BidRequest bidRequest, List<Imp> imps, String accountID) {
        return bidRequest.toBuilder()
                .imp(imps)
                .user(sanitizeUserObject(bidRequest.getUser()))
                .device(sanitizeDeviceObject(bidRequest.getDevice()))
                .cur(resolveCurrencies(bidRequest.getCur()))
                .ext(modifyExtRequest(bidRequest.getExt(), accountID))
                .build();
    }

    private static User sanitizeUserObject(User user) {
        return user == null
                ? null
                : user.toBuilder()
                .gender(null)
                .yob(null)
                .customdata(null)
                .geo(null)
                .build();
    }

    private static Device sanitizeDeviceObject(Device device) {
        if (device == null || device.getGeo() == null) {
            return device;
        }
        final Geo strippedGeo = device.getGeo().toBuilder()
                .lat(null)
                .lon(null)
                .build();
        return device.toBuilder().geo(strippedGeo).build();
    }

    private static List<String> resolveCurrencies(List<String> currencies) {
        return CollectionUtils.isEmpty(currencies)
                ? Collections.singletonList(DEFAULT_CURRENCY)
                : currencies;
    }

    private ExtRequest modifyExtRequest(ExtRequest extRequest, String accountID) {
        final ExtRequest baseExt = ObjectUtils.defaultIfNull(extRequest, ExtRequest.empty());

        final ObjectNode apsNode = mapper.mapper().createObjectNode();
        apsNode.put("account", accountID);
        final ObjectNode sdkNode = apsNode.putObject("sdk");
        sdkNode.put("version", ADAPTER_VERSION);
        sdkNode.put("source", SOURCE);

        return mapper.fillExtension(baseExt, apsNode);
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            final List<BidderError> errors = new ArrayList<>();
            return Result.of(extractBids(bidResponse, errors), errors);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse, List<BidderError> errors) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> makeBidderBid(bid, bidResponse.getCur(), errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private static BidderBid makeBidderBid(Bid bid, String currency, List<BidderError> errors) {
        final BidType bidType = resolveBidType(bid.getMtype());
        if (bidType == null) {
            errors.add(BidderError.badServerResponse(
                    "Unsupported MType for impression %s".formatted(bid.getImpid())));
            return null;
        }
        return BidderBid.of(bid, bidType, currency);
    }

    private static BidType resolveBidType(Integer mtype) {
        if (mtype == null) {
            return null;
        }
        return switch (mtype) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            default -> null;
        };
    }
}
