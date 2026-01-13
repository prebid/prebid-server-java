package org.prebid.server.bidder.teal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
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
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.teal.ExtImpTeal;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class TealBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpTeal>> TYPE_REFERENCE = new TypeReference<>() {
    };
    private final String endpointUrl;
    private final JacksonMapper mapper;

    public TealBidder(String endpointUrl,
                      JacksonMapper mapper) {

        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final ExtImpTeal params;
        try {
            params = parseImpExt(request.getImp().getFirst());
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }
        final String account = params.getAccount();
        if (!isParameterValid(account)) {
            return Result.withError(BidderError.badInput("account parameter failed validation"));
        }

        final List<Imp> modifiedImps = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            final ExtImpTeal impParams;
            try {
                impParams = parseImpExt(imp);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
                continue;
            }

            final String placement = impParams.getPlacement();
            final boolean passedValidation = placement == null || isParameterValid(placement);

            if (placement != null && !passedValidation) {
                errors.add(BidderError.badInput("placement parameter failed validation"));
                continue;
            }

            if (placement != null) {
                final ObjectNode ext = Optional.ofNullable(imp.getExt()).orElse(mapper.mapper().createObjectNode());
                final ObjectNode prebid = ext.has("prebid") && ext.get("prebid").isObject()
                        ? (ObjectNode) ext.get("prebid")
                        : ext.putObject("prebid");
                prebid.putObject("storedrequest").put("id", placement);
                modifiedImps.add(imp.toBuilder().ext(ext).build());
            } else {
                modifiedImps.add(imp);
            }
        }

        if (CollectionUtils.isEmpty(modifiedImps)) {
            return Result.withErrors(errors);
        }

        final BidRequest modifiedRequest = enrichRequest(request, account, modifiedImps);
        return Result.of(List.of(HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .impIds(BidderUtil.impIds(modifiedRequest))
                .body(mapper.encodeToBytes(modifiedRequest))
                .payload(modifiedRequest)
                .build()), errors);
    }

    private ExtImpTeal parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Error parsing imp.ext for impression " + imp.getId());
        }
    }

    private boolean isParameterValid(String parameter) {
        return !StringUtils.isBlank(parameter);
    }

    private BidRequest enrichRequest(BidRequest request, String account, List<Imp> modifiedImps) {
        final ExtRequest ext = Optional.ofNullable(request.getExt()).orElse(ExtRequest.empty());
        final ObjectNode bids = mapper.mapper().createObjectNode();
        bids.put("pbs", 1);
        ext.addProperty("bids", bids);
        return request.toBuilder()
                .site(modifySite(request.getSite(), account))
                .app(modifyApp(request.getApp(), account))
                .imp(modifiedImps)
                .ext(ext)
                .build();
    }

    private static Site modifySite(Site site, String account) {
        return account != null && site != null
                ? site.toBuilder()
                .publisher(modifyPublisher(site.getPublisher(), account))
                .build()
                : site;
    }

    private static App modifyApp(App app, String account) {
        return account != null && app != null
                ? app.toBuilder()
                .publisher(modifyPublisher(app.getPublisher(), account))
                .build()
                : app;
    }

    private static Publisher modifyPublisher(Publisher publisher, String account) {
        return publisher != null
                ? publisher.toBuilder().id(account).build()
                : Publisher.builder().id(account).build();
    }

    @Override
    public final Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(httpCall.getRequest().getPayload(), bidResponse), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidRequest, bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, getBidType(bid.getImpid(), bidRequest.getImp()), bidResponse.getCur()))
                .collect(Collectors.toList());
    }

    private static BidType getBidType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getBanner() != null) {
                    return BidType.banner;
                } else if (imp.getVideo() != null) {
                    return BidType.video;
                } else if (imp.getAudio() != null) {
                    return BidType.audio;
                } else if (imp.getXNative() != null) {
                    return BidType.xNative;
                }
            }
        }
        return BidType.banner;
    }
}
