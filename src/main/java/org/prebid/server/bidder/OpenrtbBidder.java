package org.prebid.server.bidder;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.ImpWithExt;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public abstract class OpenrtbBidder<T> implements Bidder<BidRequest> {

    private static final String DEFAULT_BID_CURRENCY = "USD";

    private final String endpointUrl;
    private final RequestCreationStrategy requestCreationStrategy;
    private final Class<T> extType;
    protected final JacksonMapper mapper;

    protected OpenrtbBidder(String endpointUrl,
                            RequestCreationStrategy requestCreationStrategy,
                            Class<T> extType,
                            JacksonMapper mapper) {

        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.requestCreationStrategy = Objects.requireNonNull(requestCreationStrategy);
        this.extType = extType;
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public final Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        try {
            validateRequest(bidRequest);
        } catch (PreBidException e) {
            return Result.emptyWithError(BidderError.badInput(e.getMessage()));
        }

        final List<BidderError> errors = new ArrayList<>();
        final List<ImpWithExt<T>> modifiedImpsWithExts = new ArrayList<>();
        for (Imp imp : bidRequest.getImp()) {
            try {
                final T impExt = parseImpExt(imp);
                final Imp modifiedImp = modifyImp(imp, impExt);
                modifiedImpsWithExts.add(new ImpWithExt<>(modifiedImp, impExt));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }
        if (modifiedImpsWithExts.isEmpty()) {
            return Result.of(Collections.emptyList(), errors);
        }

        return Result.of(createHttpRequests(bidRequest, modifiedImpsWithExts), errors);
    }

    /**
     * A hook for bidder-specific request validation if any is required.
     * <p>
     * E.g., if bidder accepts only Site requests (not App), you might want to check
     * either request.site == null or request.app != null and throw PreBidException to terminate
     * processing invalid request:
     * <p>
     * if (bidRequest.getApp() != null) {
     * throw new PreBidException("Biddder doesn't support app requests);
     * }
     *
     * @param bidRequest - incoming request
     * @throws PreBidException - if request is invalid
     */
    protected void validateRequest(BidRequest bidRequest) throws PreBidException {
    }

    private T parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt().get("bidder"), extType);
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    /**
     * A hook for bidder-specific impression changes, impression validation and
     * impression extension validation if any are required.
     * <p>
     * By default - doesn't apply any changes(returns incoming imp) or validations.
     * <p>
     * This method is executed in a loop, therefore, if given impression
     * or its extension is invalid - throwing an exception will skip it.
     * <p>
     * E.g., if bidder accepts only banner requests - check if request.imp[i].banner !=null
     * <p>
     * if (imp.getBanner() == null) {
     * throw new PreBidException("Bidder supports only banner impressions. Skipping imp.id = xxx");
     * }
     * <p>
     * E.g., a client provided incorrect value for one of the parameters:
     * <p>
     * if (StringUtils.isBlank(impExt.getParameter())) {
     * throw new PreBidException("Invalid 'parameter' value. Skipping imp.id = xxx");
     * }
     * <p>
     * Can be overridden to implement a part of bidder-specific request transformation logic,
     * such as setting some value from extension to impression itself, for example:
     * <p>
     * return imp.toBuilder()
     *           .id(impExt.getSpecialId)
     *           .build();
     * <p>
     * NOTE: It's not the only place to apply bidder-specific changes to impressions.
     * Additionally, there's an option to do these impressions' transformations later,
     * when modifying other outgoing request fields and properties.
     *
     * @param imp    - original request impression that can be modified
     * @param impExt - impression extension that can contain an information to modify its impression
     * @return - unmodified (default) or modified impression
     * @throws PreBidException - if any validation is required
     */
    protected Imp modifyImp(Imp imp, T impExt) throws PreBidException {
        return imp;
    }

    private List<HttpRequest<BidRequest>> createHttpRequests(BidRequest bidRequest, List<ImpWithExt<T>> impsWithExts) {
        switch (requestCreationStrategy) {
            case REQUEST_PER_IMP:
                return impsWithExts.stream()
                        .map(impWithExt -> makeRequest(bidRequest, Collections.singletonList(impWithExt)))
                        .collect(Collectors.toList());
            case SINGLE_REQUEST:
                return Collections.singletonList(makeRequest(bidRequest, impsWithExts));
            default:
                throw new IllegalArgumentException(String.format("Invalid request creation strategy: %s",
                        requestCreationStrategy));
        }
    }

    private HttpRequest<BidRequest> makeRequest(BidRequest bidRequest, List<ImpWithExt<T>> impsWithExts) {
        final BidRequest.BidRequestBuilder requestBuilder = bidRequest.toBuilder();

        requestBuilder.imp(impsWithExts.stream()
                .map(ImpWithExt::getImp)
                .collect(Collectors.toList()));

        modifyRequest(bidRequest, requestBuilder, impsWithExts);

        final BidRequest outgoingRequest = requestBuilder.build();
        final String body = mapper.encode(outgoingRequest);

        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .body(body)
                .headers(HttpUtil.headers())
                .payload(outgoingRequest)
                .build();
    }

    /**
     * A hook for any request changes other than Imps (although Impressions can be modified as well)
     * <p>
     * By default - applies no changes (other than imps, prior this method call)
     * <p>
     * Bidder-specific extensions, which are passed along with modified impressions, might contain
     * information not only for impressions' changes, but also for other request fields,
     * e.g. request.site.id, request.app.publisher.id, etc.
     * <p>
     * For example:
     * <p>
     * final String idFromExt = impExts.get(0).siteId;
     * requestBuilder.site(request.getSite().toBuilder().id(idFromExt).build());
     * <p>
     * Or if any extra changes are needed to already modified impressions:
     * <p>
     * final String placementId = impExts.get(0).getPlacementId();
     * final List<Imp> impsWithTagId = modifiedImps.stream()
     *              .map(Imp::toBuilder)
     *              .map(impBuilder -> impBuilder.tagid(placementId).build())
     *              .collect(Collectors.toList());
     * requestBuilder.imp(impsWithTagId);
     *
     * @param bidRequest     - original incoming bid request
     * @param requestBuilder - a builder to be used for modifying outgoing request,
     *                       received by calling bidRequest.toBuilder, i.e. contains incoming request information
     * @param impsWithExts   - a list of previously modified impressions and their extensions to be used for
     *                       any necessary request modifications.
     */
    protected void modifyRequest(BidRequest bidRequest, BidRequest.BidRequestBuilder requestBuilder,
                                 List<ImpWithExt<T>> impsWithExts) {
    }

    @Override
    public final Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(httpCall.getRequest().getPayload(), bidResponse), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || bidResponse.getSeatbid() == null) {
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
                .map(bid -> BidderBid.of(bid, getBidType(bid.getImpid(), bidRequest.getImp()), getBidCurrency()))
                .collect(Collectors.toList());
    }

    /**
     * A hook for resolving bidder-specific bid type.
     * <p>
     * By default - checks request's imp[i].id and bid.impId first and then determines
     * bid type based on impression details with the following priority:
     * 1.Banner, 2.Video, 3.Native, 4.Audio.
     *
     * @param impId - impression id taken from bid.getImpid()
     * @param imps  - list of impressions from outgoing request
     * @return - bid type depending on impression's content
     */
    protected BidType getBidType(String impId, List<Imp> imps) {
        BidType bidType = BidType.banner;
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getBanner() != null) {
                    return bidType;
                } else if (imp.getVideo() != null) {
                    bidType = BidType.video;
                } else if (imp.getXNative() != null) {
                    bidType = BidType.xNative;
                } else if (imp.getAudio() != null) {
                    bidType = BidType.audio;
                }
            }
        }
        return bidType;
    }

    /**
     * A hook for defining a bid currency.
     * <p>
     * By default - USD.
     *
     * @return - bid currency
     */
    protected String getBidCurrency() {
        return DEFAULT_BID_CURRENCY;
    }

    @Override
    public final Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }

    public enum RequestCreationStrategy {
        SINGLE_REQUEST,
        REQUEST_PER_IMP
    }
}
