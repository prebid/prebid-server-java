package org.rtb.vexing.bidder;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import io.vertx.core.Future;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.rtb.vexing.adapter.Adapter;
import org.rtb.vexing.adapter.HttpConnector;
import org.rtb.vexing.bidder.model.BidderBid;
import org.rtb.vexing.bidder.model.BidderError;
import org.rtb.vexing.bidder.model.BidderSeatBid;
import org.rtb.vexing.bidder.model.Result;
import org.rtb.vexing.cookie.UidsCookie;
import org.rtb.vexing.exception.InvalidRequestException;
import org.rtb.vexing.execution.GlobalTimeout;
import org.rtb.vexing.model.AdUnitBid;
import org.rtb.vexing.model.BidderResult;
import org.rtb.vexing.model.MediaType;
import org.rtb.vexing.model.PreBidRequestContext;
import org.rtb.vexing.model.Uids;
import org.rtb.vexing.model.openrtb.ext.response.BidType;
import org.rtb.vexing.model.openrtb.ext.response.ExtHttpCall;
import org.rtb.vexing.model.request.PreBidRequest;
import org.rtb.vexing.model.request.Video;
import org.rtb.vexing.model.response.BidderDebug;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This class exists to convert OpenRTB request to view supported by {@link Adapter} implementations, make call to
 * bidder and convert response to OpenRTB protocol view.
 */
public class HttpAdapterRequester implements BidderRequester {

    private final Adapter adapter;
    private final HttpConnector httpConnector;

    public HttpAdapterRequester(Adapter adapter, HttpConnector httpConnector) {
        this.adapter = Objects.requireNonNull(adapter);
        this.httpConnector = Objects.requireNonNull(httpConnector);
    }

    /**
     * Transform ORTB {@link BidRequest} to corresponding {@link PreBidRequestContext} and
     * {@link org.rtb.vexing.model.Bidder} to make call to legacy adapters. After response was returned it converts it
     * to {@link BidderSeatBid} format.
     */
    @Override
    public Future<BidderSeatBid> requestBids(BidRequest bidRequest, GlobalTimeout timeout) {
        final PreBidRequestContext preBidRequestContext;
        final Result<org.rtb.vexing.model.Bidder> bidderWithErrors;
        try {
            preBidRequestContext = toPreBidRequestContext(bidRequest, timeout);
            bidderWithErrors = toBidder(bidRequest);
        } catch (InvalidRequestException exception) {
            return Future.succeededFuture(BidderSeatBid.of(Collections.emptyList(), null, Collections.emptyList(),
                    exception.getMessages().stream().map(BidderError::create).collect(Collectors.toList())));
        }
        return httpConnector.call(adapter, bidderWithErrors.getValue(), preBidRequestContext)
                .map(bidderResult -> toBidderSeatBid(bidderResult, bidderWithErrors.getErrors()));
    }

    /**
     * Converts ORTB {@link BidRequest} to {@link PreBidRequestContext}.
     */
    private PreBidRequestContext toPreBidRequestContext(BidRequest bidRequest, GlobalTimeout timeout) {
        return PreBidRequestContext.builder()
                .preBidRequest(PreBidRequest
                        .builder()
                        .accountId(toAccountId(bidRequest))
                        .tid(toTransactionId(bidRequest.getSource()))
                        .secure(toSecure(bidRequest.getImp()))
                        .device(bidRequest.getDevice())
                        .timeoutMillis(bidRequest.getTmax())
                        .app(bidRequest.getApp())
                        .user(bidRequest.getUser())
                        .build())
                .uidsCookie(toUidsCookie(bidRequest))
                .timeout(timeout)
                .domain(bidRequest.getSite() != null ? bidRequest.getSite().getDomain() : null)
                .referer(bidRequest.getSite() != null ? bidRequest.getSite().getPage() : null)
                .isDebug(bidRequest.getTest() == 1)
                .build();
    }

    /**
     * Creates {@link UidsCookie} from ORTB {@link BidRequest} based on cookieFamily and
     * {@link com.iab.openrtb.request.User} buyeruid.
     */
    private UidsCookie toUidsCookie(BidRequest bidRequest) {
        final User user = bidRequest.getUser();
        UidsCookie uidsCookie = new UidsCookie(Uids.builder().uids(Collections.emptyMap()).build());
        if (user != null) {
            final String buyeruid = user.getBuyeruid();
            final String id = user.getId();
            if (StringUtils.isNotEmpty(buyeruid)) {
                uidsCookie = uidsCookie.updateUid(adapter.cookieFamily(), buyeruid);
            }
            // This shouldn't be appnexus-specific... but this line does correctly invert the
            // logic from org.rtb.vexing.adapter.OpenRtbAdapter.userBuilder(...) method
            if (StringUtils.isNotEmpty(id)) {
                uidsCookie = uidsCookie.updateUid("adnxs", id);
            }
        }
        return uidsCookie;
    }

    /**
     * Creates accountId from {@link BidRequest} based on {@link com.iab.openrtb.request.Publisher} id from
     * {@link Site} or {@link App}. In case accountId was not found {@link InvalidRequestException} will be thrown.
     */
    private String toAccountId(BidRequest bidRequest) {
        final Site site = bidRequest.getSite();
        if (site != null && site.getPublisher() != null && StringUtils.isNotEmpty(site.getPublisher().getId())) {
            return site.getPublisher().getId();
        }
        final App app = bidRequest.getApp();
        if (app != null && app.getPublisher() != null && StringUtils.isNotEmpty(app.getPublisher().getId())) {
            return app.getPublisher().getId();
        }
        throw new InvalidRequestException(
                "bidrequest.site.publisher.id or bidrequest.app.publisher.id required for legacy bidders.");
    }

    /**
     * Creates transactionId from {@link Source}. In case transactionId was not found, {@link InvalidRequestException}
     * will be thrown.
     */
    private String toTransactionId(Source source) {
        if (source != null && StringUtils.isNotEmpty(source.getTid())) {
            return source.getTid();
        }
        throw new InvalidRequestException("bidrequest.source.tid required for legacy bidders.");
    }

    /**
     * Calculates secure type from {@link List<Imp>}. Values of secure in imps should be same, 0 or 1. If different
     * values found {@link InvalidRequestException} will be thrown.
     */
    private Integer toSecure(List<Imp> imps) {
        final List<Integer> secureValues = imps.stream()
                .filter(Objects::nonNull)
                .filter(imp -> imp.getSecure() != null)
                .filter(imp -> imp.getSecure() == 1 || imp.getSecure() == 0)
                .map(Imp::getSecure)
                .collect(Collectors.toList());
        final int sum = secureValues.stream().mapToInt(Integer::intValue).sum();
        if (sum == 0) {
            return 0;
        }
        if (sum == secureValues.size()) {
            return 1;
        }
        throw new InvalidRequestException(
                "bidrequest.imp[i].secure must be consistent for legacy bidders. Mixing 0 and 1 are not allowed.");
    }

    /**
     * Creates {@link Result<org.rtb.vexing.model.Bidder>} from {@link BidRequest}. In case {@link BidRequest} has
     * empty {@link List<Imp>} or neither of was converted to {@link AdUnitBid}, {@link InvalidRequestException} will
     * be thrown.
     */
    private Result<org.rtb.vexing.model.Bidder> toBidder(BidRequest bidRequest) {
        if (bidRequest.getImp().size() == 0) {
            throw new InvalidRequestException(String.format("There no imps in bidRequest for bidder %s",
                    adapter.code()));
        }
        final Result<List<AdUnitBid>> adUnitBidsResult = toAdUnitBids(bidRequest.getImp());
        final List<AdUnitBid> adUnitBids = adUnitBidsResult.getValue();
        final List<BidderError> errors = adUnitBidsResult.getErrors();
        if (adUnitBids.size() > 0) {
            return Result.of(org.rtb.vexing.model.Bidder.of(adapter.code(), adUnitBids), errors);
        }
        throw new InvalidRequestException(messages(errors));
    }

    /**
     * Converts {@link List}&lt;{@link BidderError}&gt; to {@link List}&lt;{@link String}&gt; error messages
     */
    private static List<String> messages(List<BidderError> errors) {
        return CollectionUtils.emptyIfNull(errors).stream().map(BidderError::getMessage).collect(Collectors.toList());
    }

    /**
     * Converts {@link List}&lt;{@link Imp}&gt; to {@link Result}&lt;{@link List}&lt;{@link AdUnitBid}&gt;&gt;.
     * In case error occurred during {@link AdUnitBid} initialization, list with error will be returned in result.
     */
    private Result<List<AdUnitBid>> toAdUnitBids(List<Imp> imps) {
        final List<AdUnitBid> adUnitBids = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();
        for (final Imp imp : imps) {
            try {
                adUnitBids.add(toAdUnitBid(imp));
            } catch (InvalidRequestException exception) {
                errors.addAll(exception.getMessages().stream()
                        .map(BidderError::create)
                        .collect(Collectors.toList()));
            }
        }
        return Result.of(adUnitBids, errors);
    }

    /**
     * Creates {@link AdUnitBid} from {@link Imp}.
     */
    private AdUnitBid toAdUnitBid(Imp imp) {
        final Set<MediaType> mediaTypes = toMediaTypes(imp);
        return AdUnitBid.builder()
                .adUnitCode(imp.getId())
                .video(toVideo(imp))
                .sizes(toAdUnitBidSizes(imp))
                .topframe(imp.getBanner() != null && imp.getBanner().getTopframe() != null
                        ? imp.getBanner().getTopframe() : 0)
                .bidderCode(adapter.code())
                .bidId(imp.getId())
                .params((ObjectNode) imp.getExt().at("/bidder"))
                .instl(imp.getInstl())
                .mediaTypes(mediaTypes)
                .build();
    }

    /**
     * Creates sizes {@link List<Format>} from {@link Imp}. In case sizes list is empty, {@link InvalidRequestException}
     * will be thrown.
     */
    private List<Format> toAdUnitBidSizes(Imp imp) {
        final List<Format> sizes = new ArrayList<>();
        if (imp.getBanner() != null && imp.getBanner().getFormat() != null) {
            sizes.addAll(imp.getBanner().getFormat());
        }
        if (sizes.isEmpty()) {
            throw new InvalidRequestException("legacy bidders should have at least one defined size Format");
        }
        return sizes;
    }

    /**
     * Creates {@link Set<MediaType>} from {@link Imp}. In case any {@link MediaType} was found,
     * {@link InvalidRequestException} will be thrown.
     */
    private Set<MediaType> toMediaTypes(Imp imp) {
        final Set<MediaType> mediaTypes = new HashSet<>();
        if (imp.getBanner() != null) {
            mediaTypes.add(MediaType.banner);
        }
        if (imp.getVideo() != null) {
            mediaTypes.add(MediaType.video);
        }
        if (mediaTypes.isEmpty()) {
            throw new InvalidRequestException("legacy bidders can only bid on banner and video ad units");
        }
        return mediaTypes;
    }

    /**
     * Creates {@link Video} from {@link Imp}.
     */
    private Video toVideo(Imp imp) {
        final com.iab.openrtb.request.Video impVideo = imp.getVideo();
        if (impVideo == null) {
            return null;
        }
        return Video.builder()
                .mimes(impVideo.getMimes())
                .minduration(impVideo.getMinduration())
                .maxduration(impVideo.getMaxduration())
                .startdelay(impVideo.getStartdelay())
                .skippable(impVideo.getSkip())
                .playbackMethod(impVideo.getPlaybackmethod() != null
                        ? impVideo.getPlaybackmethod().get(0)
                        : null)
                .protocols(impVideo.getProtocols())
                .build();
    }

    /**
     * Converts {@link BidderResult} response from legacy adapters to {@link BidderSeatBid} objects for ORTB response
     * format.
     */
    private BidderSeatBid toBidderSeatBid(BidderResult bidderResult, List<BidderError> errors) {
        final Result<List<BidderBid>> bidderBidsResult = toBidderBids(bidderResult);
        final List<BidderError> bidderErrors = new ArrayList<>(errors);
        bidderErrors.addAll(bidderBidsResult.getErrors());

        return BidderSeatBid.of(bidderBidsResult.getValue(), null, bidderResult.getBidderStatus().getDebug() != null
                ? toExtHttpCalls(bidderResult.getBidderStatus().getDebug())
                : null, bidderErrors);
    }

    /**
     * Converts {@link BidderResult} to {@link Result}&lt;{@link List}&lt;{@link BidderBid}&gt;&gt;.
     */
    private Result<List<BidderBid>> toBidderBids(BidderResult bidderResult) {
        final List<BidderBid> bidderBids = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();
        for (org.rtb.vexing.model.response.Bid bid : bidderResult.getBids()) {
            final Result<BidType> bidTypeResult = toBidType(bid);
            if (bidTypeResult.getErrors().isEmpty()) {
                bidderBids.add(BidderBid.of(toOrtbBid(bid), bidTypeResult.getValue()));
            } else {
                errors.addAll(bidTypeResult.getErrors());
            }
        }
        return Result.of(bidderBids, errors);
    }

    /**
     * Converts {@link org.rtb.vexing.model.response.Bid} to {@link Result<BidType>}. In case {@link MediaType} is not
     * defined in {@link org.rtb.vexing.model.response.Bid} or incorrect {@link MediaType} value, {@link Result} with
     * error list ad null value will be returned.
     */
    private Result<BidType> toBidType(org.rtb.vexing.model.response.Bid bid) {
        if (bid.getMediaType() == null) {
            return Result.of(null, Collections.singletonList(BidderError.create("Media Type is not defined for Bid")));
        }
        switch (bid.getMediaType()) {
            case video:
                return Result.of(BidType.video, Collections.emptyList());
            case banner:
                return Result.of(BidType.banner, Collections.emptyList());
            default:
                return Result.of(null, Collections.singletonList(BidderError.create(
                        "legacy bidders can only bid on banner and video ad units")));
        }
    }

    /**
     * Converts {@link org.rtb.vexing.model.response.Bid} to {@link Bid}.
     */
    private Bid toOrtbBid(org.rtb.vexing.model.response.Bid bid) {
        return Bid.builder().id(bid.getBidId())
                .impid(bid.getCode())
                .crid(bid.getCreativeId())
                .price(bid.getPrice())
                .nurl(bid.getNurl())
                .adm(bid.getAdm())
                .w(bid.getWidth())
                .h(bid.getHeight())
                .dealid(bid.getDealId())
                .build();
    }

    /**
     * Converts {@link List<BidderDebug>} to {@link List<ExtHttpCall>}.
     */
    private List<ExtHttpCall> toExtHttpCalls(List<BidderDebug> bidderDebugs) {
        return bidderDebugs.stream().map(this::toExtHttpCall).collect(Collectors.toList());
    }

    /**
     * Creates {@link ExtHttpCall} from {@link BidderDebug}
     */
    private ExtHttpCall toExtHttpCall(BidderDebug bidderDebug) {
        return ExtHttpCall.builder()
                .uri(bidderDebug.getRequestUri())
                .requestbody(bidderDebug.getRequestBody())
                .responsebody(bidderDebug.getResponseBody())
                .status(bidderDebug.getStatusCode())
                .build();
    }

    /**
     * Returns cookieFamilyName by adapters cookieFamily
     */
    @Override
    public String cookieFamilyName() {
        return adapter.cookieFamily();
    }

    /**
     * Returns bidders name
     */
    @Override
    public String name() {
        return adapter.code();
    }
}
