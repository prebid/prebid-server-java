package org.prebid.server.bidder.missena;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
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
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.missena.ExtImpMissena;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class MissenaBidder implements Bidder<MissenaAdRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpMissena>> TYPE_REFERENCE = new TypeReference<>() {
    };
    private static final int AD_REQUEST_DEFAULT_TIMEOUT = 2000;

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public MissenaBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<MissenaAdRequest>>> makeHttpRequests(BidRequest request) {
        final List<HttpRequest<MissenaAdRequest>> requests = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                final ExtImpMissena extImp = parseImpExt(imp);
                requests.add(makeHttpRequest(request, imp.getId(), extImp));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.of(requests, errors);
    }

    private ExtImpMissena parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Error parsing missenaExt parameters");
        }
    }

    private HttpRequest<MissenaAdRequest> makeHttpRequest(BidRequest request, String impId, ExtImpMissena extImp) {
        final Site site = request.getSite();

        final MissenaAdRequest missenaAdRequest = MissenaAdRequest.builder()
                .requestId(request.getId())
                .timeout(AD_REQUEST_DEFAULT_TIMEOUT)
                .referer(site == null ? null : site.getPage())
                .refererCanonical(site == null ? null : site.getDomain())
                .consentString(getUserConsent(request.getUser()))
                .consentRequired(isGdpr(request.getRegs()))
                .placement(extImp.getPlacement())
                .test(extImp.getTestMode())
                .build();

        return HttpRequest.<MissenaAdRequest>builder()
                .method(HttpMethod.POST)
                .uri(makeUrl(extImp.getApiKey()))
                .headers(makeHeaders(request.getDevice(), site))
                .impIds(Collections.singleton(impId))
                .body(mapper.encodeToBytes(missenaAdRequest))
                .payload(missenaAdRequest)
                .build();
    }

    private MultiMap makeHeaders(Device device, Site site) {
        final MultiMap headers = HttpUtil.headers();

        if (device != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER, device.getUa());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIp());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIpv6());
        }

        if (site != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.REFERER_HEADER, site.getPage());
        }

        return headers;
    }

    private String makeUrl(String apiKey) {
        return endpointUrl + "?t=%s".formatted(apiKey);
    }

    private static boolean isGdpr(Regs regs) {
        return Optional.ofNullable(regs)
                .map(Regs::getExt)
                .map(ExtRegs::getGdpr)
                .map(gdpr -> gdpr == 1)
                .orElse(false);
    }

    private static String getUserConsent(User user) {
        return Optional.ofNullable(user)
                .map(User::getExt)
                .map(ExtUser::getConsent)
                .orElse(StringUtils.EMPTY);
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<MissenaAdRequest> httpCall, BidRequest bidRequest) {
        try {
            final MissenaAdResponse bidResponse = mapper.decodeValue(
                    httpCall.getResponse().getBody(),
                    MissenaAdResponse.class);
            return Result.withValues(Collections.singletonList(extractBid(bidRequest, bidResponse)));
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private BidderBid extractBid(BidRequest request, MissenaAdResponse response) {
        final Bid bid = Bid.builder()
                .id(request.getId())
                .price(response.getCpm())
                .impid(request.getImp().getFirst().getId())
                .adm(response.getAd())
                .crid(response.getRequestId())
                .build();

        return BidderBid.of(bid, BidType.banner, response.getCurrency());
    }
}
