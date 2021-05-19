package org.prebid.server.bidder.algorix;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.algorix.ExtImpAlgorix;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.Objects;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * AlgoriX (@link Bidder) implementation.
 * @author xunyunbo@algorix.co
 */
public class AlgorixBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpAlgorix>> ALGORIX_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private static final String URL_HOST_MACRO = "{HOST}";
    private static final String URL_SID_MACRO = "{SID}";
    private static final String URL_TOKEN_MACRO = "{TOKEN}";

    private static final int FIRST_INDEX = 0;

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public AlgorixBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> updatedImps = new ArrayList<>();

        ExtImpAlgorix extImpAlgorix = null;

        for (Imp imp : request.getImp()) {
            try {
                extImpAlgorix = extImpAlgorix == null ? parseImpExt(imp) : extImpAlgorix;
                updatedImps.add(updateImp(imp));
            } catch (PreBidException error) {
                errors.add(BidderError.badInput(error.getMessage()));
            }
        }

        if (extImpAlgorix == null) {
            return Result.withError(BidderError.badInput("Invalid ExtImpAlgoriX value"));
        }

        final BidRequest outgoingRequest = request.toBuilder().imp(updatedImps).build();
        final String body = mapper.encode(outgoingRequest);
        return Result.of(Collections.singletonList(
                HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(resolveUrl(extImpAlgorix))
                        .headers(resolveHeaders())
                        .payload(outgoingRequest)
                        .body(body)
                        .build()),
                errors);
    }

    /**
     * Parse Ext Imp
     * @param imp BidRequest Imp
     * @return Algorix Ext Imp
     */
    private ExtImpAlgorix parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), ALGORIX_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException error) {
            throw new PreBidException(String.format("Impression Id=%s, has invalid Ext", imp.getId()));
        }
    }

    /**
     * Resolve Url
     * @param extImp Algorix Ext Imp
     * @return target Url
     */
    private String resolveUrl(ExtImpAlgorix extImp) {
        return endpointUrl.replace(URL_HOST_MACRO, extImp.getHost())
                .replace(URL_SID_MACRO, extImp.getSid())
                .replace(URL_TOKEN_MACRO, extImp.getToken());
    }

    private MultiMap resolveHeaders() {
        final MultiMap headers = HttpUtil.headers();
        headers.add(HttpUtil.X_OPENRTB_VERSION_HEADER, "2.5");
        return headers;
    }

    /**
     * check Integer valid is not null and Zero
     * @param value value
     * @return true or false
     */
    private boolean checkValid(Integer value) {
        return value == null || value == 0;
    }

    /**
     * update Imp for transform banner Size
     * @param imp imp
     * @return new imp
     */
    private Imp updateImp(Imp imp) {
        if (imp.getBanner() != null) {
            final Banner banner = imp.getBanner();
            boolean formatValid = checkValid(banner.getW()) || checkValid(banner.getH());
            boolean formatFlag = CollectionUtils.isNotEmpty(banner.getFormat());
            if (formatValid && formatFlag) {
                final Format format = banner.getFormat().get(FIRST_INDEX);
                return imp.toBuilder()
                        .banner(banner.toBuilder()
                                .w(format.getW())
                                .h(format.getH())
                                .build())
                        .build();
            }
        }
        return imp;
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(httpCall.getRequest().getPayload(), bidResponse), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidRequest, bidResponse);
    }

    private List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, getBidType(bid.getImpid(), bidRequest.getImp()), bidResponse.getCur()))
                .collect(Collectors.toList());
    }

    protected BidType getBidType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getBanner() != null) {
                    return BidType.banner;
                } else if (imp.getVideo() != null) {
                    return BidType.video;
                } else if (imp.getXNative() != null) {
                    return BidType.xNative;
                }
            }
        }
        return BidType.banner;
    }
}
