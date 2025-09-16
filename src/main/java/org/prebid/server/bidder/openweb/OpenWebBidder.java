package org.prebid.server.bidder.openweb;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
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
import org.prebid.server.proto.openrtb.ext.request.openweb.ExtImpOpenweb;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class OpenWebBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpOpenweb>> OPENWEB_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public OpenWebBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        String org = null;

        for (Imp imp : request.getImp()) {
            try {
                final ExtImpOpenweb extImpOpenweb = parseImpExt(imp);
                validateImpExt(extImpOpenweb);

                org = orgFrom(extImpOpenweb);
                if (org != null) {
                    break;
                }
            } catch (PreBidException e) {
                return Result.withError(BidderError.badInput("checkExtAndExtractOrg: " + e.getMessage()));
            }
        }

        if (org == null) {
            return Result.withError(BidderError.badInput("checkExtAndExtractOrg: no org or aid supplied"));
        }

        return Result.withValue(BidderUtil.defaultRequest(request, resolveEndpoint(org), mapper));
    }

    private ExtImpOpenweb parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), OPENWEB_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("unmarshal ExtImpOpenWeb: " + e.getMessage());
        }
    }

    private static void validateImpExt(ExtImpOpenweb extImpOpenweb) {
        if (StringUtils.isBlank(extImpOpenweb.getPlacementId())) {
            throw new PreBidException("no placement id supplied");
        }
    }

    private static String orgFrom(ExtImpOpenweb extImpOpenweb) {
        final String org = extImpOpenweb.getOrg();
        if (StringUtils.isNotBlank(org)) {
            return StringUtils.trim(org);
        }

        final Integer aid = extImpOpenweb.getAid();
        return aid != null && aid != 0
                ? aid.toString()
                : null;
    }

    private String resolveEndpoint(String org) {
        return "%s?publisher_id=%s".formatted(endpointUrl, HttpUtil.encodeUrl(org));
    }

    @Override
    public final Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final List<BidderError> errors = new ArrayList<>();
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
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
                .map(bid -> toBidderBid(bid, bidResponse.getCur(), errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private BidderBid toBidderBid(Bid bid, String currency, List<BidderError> errors) {
        try {
            return BidderBid.of(bid, getBidType(bid.getMtype()), currency);
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }
    }

    private static BidType getBidType(Integer mType) {
        return switch (mType) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            case null, default -> throw new PreBidException("unsupported MType " + mType);
        };
    }
}
