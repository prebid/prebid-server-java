package org.prebid.server.bidder.teal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
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
import org.prebid.server.proto.openrtb.ext.request.teal.ExtImpTeal;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class TealBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpTeal>> TYPE_REFERENCE = new TypeReference<>() {
    };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public TealBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<Imp> modifiedImps = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();
        String account = null;

        for (Imp imp : request.getImp()) {
            final ExtImpTeal extImpTeal;
            try {
                extImpTeal = parseImpExt(imp);
                validateImpExt(extImpTeal);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
                continue;
            }

            account = account == null ? extImpTeal.getAccount() : account;
            modifiedImps.add(modifyImp(imp, extImpTeal.getPlacement()));
        }

        if (modifiedImps.isEmpty()) {
            return Result.withErrors(errors);
        }

        final BidRequest modifiedRequest = modifyBidRequest(request, account, modifiedImps);
        return Result.of(
                Collections.singletonList(BidderUtil.defaultRequest(modifiedRequest, endpointUrl, mapper)),
                errors);
    }

    private ExtImpTeal parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Error parsing imp.ext for impression " + imp.getId());
        }
    }

    private static void validateImpExt(ExtImpTeal extImpTeal) {
        if (StringUtils.isBlank(extImpTeal.getAccount())) {
            throw new PreBidException("account parameter failed validation");
        }

        final String placement = extImpTeal.getPlacement();
        if (placement != null && StringUtils.isBlank(placement)) {
            throw new PreBidException("placement parameter failed validation");
        }
    }

    private static Imp modifyImp(Imp imp, String placement) {
        if (placement == null) {
            return imp;
        }

        final ObjectNode modifiedExt = imp.getExt().deepCopy();
        getOrCreate(getOrCreate(modifiedExt, "prebid"), "storedrequest")
                .put("id", placement);

        return imp.toBuilder().ext(modifiedExt).build();
    }

    private static ObjectNode getOrCreate(ObjectNode parent, String field) {
        final JsonNode child = parent.get(field);
        return child != null && child.isObject()
                ? (ObjectNode) child
                : parent.putObject(field);
    }

    private BidRequest modifyBidRequest(BidRequest request, String account, List<Imp> modifiedImps) {
        final ExtRequest ext = ObjectUtils.defaultIfNull(request.getExt(), ExtRequest.empty());
        ext.addProperty("bids", mapper.mapper().createObjectNode().put("pbs", 1));

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
        } catch (DecodeException e) {
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
                .filter(Objects::nonNull)
                .map(bid -> BidderBid.of(bid, getBidType(bid.getImpid(), bidRequest.getImp()), bidResponse.getCur()))
                .toList();
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
