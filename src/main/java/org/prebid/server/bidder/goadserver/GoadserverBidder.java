package org.prebid.server.bidder.goadserver;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import org.apache.commons.collections4.CollectionUtils;
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
import org.prebid.server.proto.openrtb.ext.request.goadserver.ExtImpGoadserver;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.Uri;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * GoAdserver is a self-hosted, multi-tenant ad server. Every deployment runs under its own domain, so the
 * endpoint host and the publisher token are per-impression params. Impressions are grouped by (host, token)
 * and each group is sent to https://{host}/openrtb2/auction with the token in site.publisher.id.
 */
public class GoadserverBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpGoadserver>> GOADSERVER_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private static final String HOST_MACRO = "Host";
    private static final String DEFAULT_CURRENCY = "USD";
    private static final String DSA_FIELD = "dsa";

    // A bare hostname only: no scheme, port, path or userinfo, and no IP literal.
    private static final Pattern HOST_PATTERN =
            Pattern.compile("^([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$");

    private final Uri endpointUrl;
    private final JacksonMapper mapper;

    public GoadserverBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = Uri.of(endpointUrl);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        if (request.getSite() == null) {
            return Result.withError(BidderError.badInput("goadserver supports site requests only"));
        }

        final List<BidderError> errors = new ArrayList<>();
        final Map<GroupKey, List<Imp>> impsByGroup = new LinkedHashMap<>();

        for (Imp imp : request.getImp()) {
            try {
                final ExtImpGoadserver extImp = parseImpExt(imp);
                final GroupKey key = new GroupKey(normalizeHost(extImp.getHost(), imp.getId()),
                        validateToken(extImp.getToken(), imp.getId()));
                impsByGroup.computeIfAbsent(key, _ -> new ArrayList<>()).add(modifyImp(imp, extImp));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        final List<HttpRequest<BidRequest>> httpRequests = impsByGroup.entrySet().stream()
                .map(entry -> makeHttpRequest(request, entry.getKey(), entry.getValue()))
                .toList();

        return Result.of(httpRequests, errors);
    }

    private ExtImpGoadserver parseImpExt(Imp imp) {
        try {
            final ExtImpGoadserver extImp = mapper.mapper()
                    .convertValue(imp.getExt(), GOADSERVER_EXT_TYPE_REFERENCE)
                    .getBidder();
            if (extImp == null) {
                throw new PreBidException("imp %s: missing ext.bidder".formatted(imp.getId()));
            }
            return extImp;
        } catch (IllegalArgumentException e) {
            throw new PreBidException("imp %s: invalid ext.bidder".formatted(imp.getId()));
        }
    }

    private static String normalizeHost(String host, String impId) {
        final String normalized = StringUtils.trimToEmpty(host).toLowerCase(Locale.ROOT);
        if (!HOST_PATTERN.matcher(normalized).matches()) {
            throw new PreBidException("imp %s: invalid host".formatted(impId));
        }
        return normalized;
    }

    private static String validateToken(String token, String impId) {
        if (StringUtils.isBlank(token)) {
            throw new PreBidException("imp %s: missing token".formatted(impId));
        }
        return token;
    }

    private Imp modifyImp(Imp imp, ExtImpGoadserver extImp) {
        final Imp.ImpBuilder builder = imp.toBuilder().ext(makeImpExt(extImp.getSubid()));

        final BigDecimal floor = extImp.getFloor();
        if (BidderUtil.isValidPrice(floor) && !BidderUtil.isValidPrice(imp.getBidfloor())) {
            builder.bidfloor(floor).bidfloorcur(DEFAULT_CURRENCY);
        }
        return builder.build();
    }

    // The GoAdserver endpoint reads an optional sub-identifier from imp.ext.goadserver.subid.
    private ObjectNode makeImpExt(JsonNode subid) {
        final String subidValue = subid == null || subid.isNull() ? null : subid.asText();
        if (StringUtils.isEmpty(subidValue)) {
            return null;
        }
        final ObjectNode impExt = mapper.mapper().createObjectNode();
        impExt.putObject("goadserver").put("subid", subidValue);
        return impExt;
    }

    private HttpRequest<BidRequest> makeHttpRequest(BidRequest request, GroupKey key, List<Imp> imps) {
        final BidRequest outgoingRequest = request.toBuilder()
                .imp(imps)
                .site(modifySite(request.getSite(), key.token()))
                .build();

        final MultiMap headers = HttpUtil.headers()
                .add(HttpUtil.X_OPENRTB_VERSION_HEADER, "2.5");

        return BidderUtil.defaultRequest(outgoingRequest, headers, resolveEndpoint(key.host()), mapper);
    }

    private static Site modifySite(Site site, String token) {
        final Publisher publisher = site.getPublisher();
        final Publisher modifiedPublisher = publisher != null
                ? publisher.toBuilder().id(token).build()
                : Publisher.builder().id(token).build();
        return site.toBuilder().publisher(modifiedPublisher).build();
    }

    private String resolveEndpoint(String host) {
        return endpointUrl.replaceMacro(HOST_MACRO, host).expand();
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
        final String currency = StringUtils.defaultIfEmpty(bidResponse.getCur(), DEFAULT_CURRENCY);
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> makeBidderBid(bid, currency, errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private BidderBid makeBidderBid(Bid bid, String currency, List<BidderError> errors) {
        final BidType bidType = getBidType(bid);
        if (bidType == null) {
            errors.add(BidderError.badServerResponse(
                    "unsupported media type for bid %s on imp %s".formatted(bid.getId(), bid.getImpid())));
            return null;
        }
        return BidderBid.of(bid.toBuilder().ext(keepDsa(bid.getExt())).build(), bidType, currency);
    }

    // Prefers bid.mtype and falls back to bid.ext.prebid.type, which GoAdserver sets on every bid.
    private static BidType getBidType(Bid bid) {
        final BidType byMtype = switch (bid.getMtype()) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            case 4 -> BidType.xNative;
            case null, default -> null;
        };
        if (byMtype != null) {
            return byMtype;
        }

        final ObjectNode ext = bid.getExt();
        final String type = ext != null ? ext.path("prebid").path("type").asText(null) : null;
        return switch (StringUtils.defaultString(type)) {
            case "banner" -> BidType.banner;
            case "video" -> BidType.video;
            case "native" -> BidType.xNative;
            default -> null;
        };
    }

    // Drops the Prebid.js targeting GoAdserver adds for its direct integration and keeps only the
    // DSA transparency object, which Prebid Server validates and relays.
    private ObjectNode keepDsa(ObjectNode ext) {
        final JsonNode dsa = ext != null ? ext.get(DSA_FIELD) : null;
        if (dsa == null || dsa.isNull()) {
            return null;
        }
        final ObjectNode modifiedExt = mapper.mapper().createObjectNode();
        modifiedExt.set(DSA_FIELD, dsa);
        return modifiedExt;
    }

    private record GroupKey(String host, String token) {
    }
}
