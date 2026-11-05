package org.prebid.server.bidder.adswag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
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
import org.prebid.server.proto.openrtb.ext.request.adswag.ExtImpAdswag;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public class AdswagBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpAdswag>> TYPE_REFERENCE = new TypeReference<>() {
    };

    private static final String DEFAULT_CURRENCY = "EUR";
    private static final Pattern VAST_MARKUP = Pattern.compile("<\\s*VAST[\\s/>]", Pattern.CASE_INSENSITIVE);

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public AdswagBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final Map<String, List<Imp>> impsByPublisher = new LinkedHashMap<>();
        final List<BidderError> errors = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            final ExtImpAdswag extImpAdswag;
            try {
                extImpAdswag = parseImpExt(imp);
                validateImpExt(extImpAdswag, imp.getId());
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
                continue;
            }

            impsByPublisher
                    .computeIfAbsent(extImpAdswag.getPublisherId(), _ -> new ArrayList<>())
                    .add(modifyImp(imp, extImpAdswag.getPlacementId()));
        }

        if (impsByPublisher.isEmpty()) {
            return Result.withErrors(errors);
        }

        if (request.getSite() == null && request.getApp() == null) {
            errors.add(BidderError.badInput("request must contain either site or app"));
            return Result.withErrors(errors);
        }

        final List<HttpRequest<BidRequest>> httpRequests = impsByPublisher.entrySet().stream()
                .map(group -> modifyBidRequest(request, group.getKey(), group.getValue()))
                .map(modifiedRequest -> BidderUtil.defaultRequest(modifiedRequest, endpointUrl, mapper))
                .toList();

        return Result.of(httpRequests, errors);
    }

    private ExtImpAdswag parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Error parsing imp.ext for impression " + imp.getId());
        }
    }

    private static void validateImpExt(ExtImpAdswag extImpAdswag, String impId) {
        if (StringUtils.isBlank(extImpAdswag.getPublisherId())) {
            throw new PreBidException("missing publisherId for imp " + impId);
        }
    }

    private static Imp modifyImp(Imp imp, String placementId) {
        final ObjectNode modifiedExt = imp.getExt().deepCopy();
        modifiedExt.remove("bidder");
        modifiedExt.remove("prebid");
        if (StringUtils.isNotBlank(placementId)) {
            modifiedExt.putObject("adswag").put("placement_id", placementId);
        }

        return imp.toBuilder().ext(modifiedExt.isEmpty() ? null : modifiedExt).build();
    }

    private static BidRequest modifyBidRequest(BidRequest request, String publisherId, List<Imp> modifiedImps) {
        return request.toBuilder()
                .site(modifySite(request.getSite(), publisherId))
                .app(modifyApp(request.getApp(), publisherId))
                .imp(modifiedImps)
                .build();
    }

    private static Site modifySite(Site site, String publisherId) {
        return site != null
                ? site.toBuilder().publisher(modifyPublisher(site.getPublisher(), publisherId)).build()
                : null;
    }

    private static App modifyApp(App app, String publisherId) {
        return app != null
                ? app.toBuilder().publisher(modifyPublisher(app.getPublisher(), publisherId)).build()
                : null;
    }

    private static Publisher modifyPublisher(Publisher publisher, String publisherId) {
        return Optional.ofNullable(publisher)
                .map(Publisher::toBuilder)
                .orElseGet(Publisher::builder)
                .id(publisherId)
                .build();
    }

    @Override
    public final Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        final String body = httpCall.getResponse().getBody();
        if (StringUtils.isBlank(body)) {
            return Result.empty();
        }

        try {
            final BidResponse bidResponse = mapper.decodeValue(body, BidResponse.class);
            final List<BidderError> errors = new ArrayList<>();
            final List<BidderBid> bids = extractBids(httpCall.getRequest().getPayload(), bidResponse, errors);
            return Result.of(bids, errors);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse, List<BidderError> errors) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        final String currency = StringUtils.defaultIfBlank(bidResponse.getCur(), DEFAULT_CURRENCY);
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .map(bid -> makeBidderBid(bid, bidRequest.getImp(), currency, errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private BidderBid makeBidderBid(Bid bid, List<Imp> imps, String currency, List<BidderError> errors) {
        final Imp imp = findImp(imps, bid.getImpid());
        if (imp == null) {
            errors.add(BidderError.badServerResponse(
                    "bid %s references unknown imp %s".formatted(bid.getId(), bid.getImpid())));
            return null;
        }

        final String serveUrl = serveUrl(bid);
        if (StringUtils.isBlank(bid.getAdm()) && StringUtils.isBlank(serveUrl)) {
            errors.add(BidderError.badServerResponse(
                    "bid %s has neither adm nor ext.adswag.serve_url".formatted(bid.getId())));
            return null;
        }

        final BidType bidType;
        try {
            bidType = resolveBidType(imp, bid);
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }

        final Bid modifiedBid = switch (bidType) {
            case banner -> makeBannerBid(bid, serveUrl, imp.getBanner());
            case video -> makeVideoBid(bid, serveUrl, imp.getVideo());
            case audio -> makeAudioBid(bid, serveUrl);
            // should never happen
            default -> throw new AssertionError();
        };

        return BidderBid.of(modifiedBid, bidType, currency);
    }

    private static Imp findImp(List<Imp> imps, String impId) {
        return imps.stream()
                .filter(imp -> Objects.equals(imp.getId(), impId))
                .findFirst()
                .orElse(null);
    }

    private static String serveUrl(Bid bid) {
        final JsonNode serveUrlNode = bid.getExt() != null
                ? bid.getExt().path("adswag").path("serve_url")
                : null;
        return serveUrlNode != null && serveUrlNode.isTextual() ? serveUrlNode.textValue() : null;
    }

    private static BidType resolveBidType(Imp imp, Bid bid) {
        final Integer mtype = bid.getMtype();
        if (mtype != null) {
            return switch (mtype) {
                case 1 -> BidType.banner;
                case 2 -> BidType.video;
                case 3 -> BidType.audio;
                default -> throw new PreBidException(
                        "unsupported bid.mtype %d for impression %s".formatted(mtype, bid.getImpid()));
            };
        }

        final boolean isVast = bid.getAdm() != null && VAST_MARKUP.matcher(bid.getAdm()).find();
        if (imp.getAudio() != null && (isVast || imp.getBanner() == null)) {
            return BidType.audio;
        }
        if (imp.getVideo() != null && (isVast || imp.getBanner() == null)) {
            return BidType.video;
        }
        if (imp.getBanner() != null) {
            return BidType.banner;
        }
        throw new PreBidException("unable to resolve media type for impression " + bid.getImpid());
    }

    private static Bid makeBannerBid(Bid bid, String serveUrl, Banner banner) {
        final Format size = bannerSize(banner);
        final boolean updateSize = hasNoSize(bid) && size != null;

        return bid.toBuilder()
                .adm(StringUtils.isBlank(bid.getAdm()) ? iframeMarkup(serveUrl, size) : bid.getAdm())
                .w(updateSize ? size.getW() : bid.getW())
                .h(updateSize ? size.getH() : bid.getH())
                .mtype(1)
                .build();
    }

    private static Format bannerSize(Banner banner) {
        if (banner == null) {
            return null;
        }
        if (CollectionUtils.isNotEmpty(banner.getFormat())) {
            return banner.getFormat().getFirst();
        }
        if (banner.getW() != null && banner.getH() != null) {
            return Format.builder().w(banner.getW()).h(banner.getH()).build();
        }
        return null;
    }

    private static String iframeMarkup(String serveUrl, Format size) {
        final StringBuilder markup = new StringBuilder()
                .append("<iframe src=\"").append(escapeHtmlAttribute(serveUrl)).append("\" ");

        if (size != null) {
            markup
                    .append("width=\"").append(size.getW()).append("\" ")
                    .append("height=\"").append(size.getH()).append("\" ");
        }
        markup.append("""
                frameborder="0" \
                scrolling="no" \
                marginheight="0" \
                marginwidth="0" \
                style="border:0" \
                title="Advertisement"></iframe>""");

        return markup.toString();
    }

    // Mirrors Go's html.EscapeString so both server adapters emit identical markup.
    private static String escapeHtmlAttribute(String value) {
        return value
                .replace("&", "&amp;")
                .replace("'", "&#39;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&#34;");
    }

    private static boolean hasNoSize(Bid bid) {
        return bid.getW() == null || bid.getW() <= 0 || bid.getH() == null || bid.getH() <= 0;
    }

    private static Bid makeVideoBid(Bid bid, String serveUrl, Video video) {
        final boolean updateSize = hasNoSize(bid) && video != null && video.getW() != null && video.getH() != null;
        return bid.toBuilder()
                .nurl(StringUtils.isBlank(bid.getAdm()) ? serveUrl : bid.getNurl())
                .w(updateSize ? video.getW() : bid.getW())
                .h(updateSize ? video.getH() : bid.getH())
                .mtype(2)
                .build();
    }

    private static Bid makeAudioBid(Bid bid, String serveUrl) {
        return bid.toBuilder()
                .nurl(StringUtils.isBlank(bid.getAdm()) ? serveUrl : bid.getNurl())
                .mtype(3)
                .build();
    }
}
