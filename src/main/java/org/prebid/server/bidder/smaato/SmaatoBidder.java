package org.prebid.server.bidder.smaato;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.smaato.proto.SmaatoBidRequestExt;
import org.prebid.server.bidder.smaato.proto.SmaatoImage;
import org.prebid.server.bidder.smaato.proto.SmaatoImageAd;
import org.prebid.server.bidder.smaato.proto.SmaatoImg;
import org.prebid.server.bidder.smaato.proto.SmaatoMediaData;
import org.prebid.server.bidder.smaato.proto.SmaatoRichMediaAd;
import org.prebid.server.bidder.smaato.proto.SmaatoRichmedia;
import org.prebid.server.bidder.smaato.proto.SmaatoSiteExtData;
import org.prebid.server.bidder.smaato.proto.SmaatoUserExtData;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.smaato.ExtImpSmaato;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SmaatoBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpSmaato>> SMAATO_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpSmaato>>() {
            };

    private static final String CLIENT_VERSION = "prebid_server_0.1";
    private static final String SMT_AD_TYPE_IMG = "Img";
    private static final String SMT_ADTYPE_RICHMEDIA = "Richmedia";
    private static final String SMT_ADTYPE_VIDEO = "Video";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public SmaatoBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> imps = new ArrayList<>();

        String firstPublisherId = null;
        for (Imp imp : request.getImp()) {
            try {
                final ExtImpSmaato extImpSmaato = parseImpExt(imp);
                firstPublisherId = firstPublisherId == null ? extImpSmaato.getPublisherId() : firstPublisherId;
                final Imp modifiedImp = modifyImp(imp, extImpSmaato.getAdspaceId());
                imps.add(modifiedImp);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        final Site modifiedSite;
        final User modifiedUser;
        try {
            modifiedSite = request.getSite() != null ? modifySite(request.getSite(), firstPublisherId) : null;
            modifiedUser = request.getUser() != null ? modifyUser(request.getUser()) : null;
        } catch (PreBidException e) {
            errors.add(BidderError.badInput(e.getMessage()));
            return Result.of(Collections.emptyList(), errors);
        }

        final BidRequest outgoingRequest = request.toBuilder()
                .imp(imps)
                .site(modifiedSite)
                .user(modifiedUser)
                .ext(mapper.fillExtension(ExtRequest.empty(), SmaatoBidRequestExt.of(CLIENT_VERSION)))
                .build();

        return Result.of(Collections.singletonList(
                HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(endpointUrl)
                        .headers(HttpUtil.headers())
                        .payload(outgoingRequest)
                        .body(mapper.encode(outgoingRequest))
                        .build()),
                errors);
    }

    private ExtImpSmaato parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), SMAATO_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private static Imp modifyImp(Imp imp, String adspaceId) {
        final Imp.ImpBuilder impBuilder = imp.toBuilder();
        if (imp.getBanner() != null) {
            return impBuilder.banner(modifyBanner(imp.getBanner())).tagid(adspaceId).ext(null).build();
        }

        if (imp.getVideo() != null) {
            return impBuilder.tagid(adspaceId).ext(null).build();
        }
        throw new PreBidException(String.format(
                "invalid MediaType. SMAATO only supports Banner and Video. Ignoring ImpID=%s", imp.getId()));
    }

    private static Banner modifyBanner(Banner banner) {
        if (banner.getW() != null && banner.getH() != null) {
            return banner;
        }
        final List<Format> format = banner.getFormat();
        if (CollectionUtils.isEmpty(format)) {
            throw new PreBidException(String.format("No sizes provided for Banner %s", format));
        }
        final Format firstFormat = format.get(0);
        return banner.toBuilder().w(firstFormat.getW()).h(firstFormat.getH()).build();
    }

    private Site modifySite(Site site, String firstPublisherId) {
        final Site.SiteBuilder siteBuilder = site.toBuilder()
                .publisher(Publisher.builder().id(firstPublisherId).build());

        final ExtSite siteExt = site.getExt();
        if (siteExt != null) {
            final SmaatoSiteExtData data = convertExt(siteExt.getData(), SmaatoSiteExtData.class);
            final String keywords = data != null ? data.getKeywords() : null;
            siteBuilder.keywords(keywords).ext(null);
        }

        return siteBuilder.build();
    }

    private User modifyUser(User user) {
        final ExtUser ext = user.getExt();
        if (ext == null) {
            return user;
        }

        final ObjectNode extDataNode = ext.getData();
        if (extDataNode == null) {
            return user;
        }

        final User.UserBuilder userBuilder = user.toBuilder();
        final SmaatoUserExtData data = convertExt(extDataNode, SmaatoUserExtData.class);

        final String gender = data != null ? data.getGender() : null;
        if (StringUtils.isNotBlank(gender)) {
            userBuilder.gender(gender);
        }

        final Integer yob = data != null ? data.getYob() : null;
        if (yob != null && yob != 0) {
            userBuilder.yob(yob);
        }

        final String keywords = data != null ? data.getKeywords() : null;
        if (StringUtils.isNotBlank(keywords)) {
            userBuilder.keywords(keywords);
        }

        userBuilder.ext(ext.toBuilder().data(null).build());

        return userBuilder.build();
    }

    private <T> T convertExt(ObjectNode ext, Class<T> className) {
        try {
            return mapper.mapper().convertValue(ext, className);
        } catch (IllegalArgumentException e) {
            throw new PreBidException(String.format("Cannot decode extension: %s", e.getMessage()), e);
        }
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        final int statusCode = httpCall.getResponse().getStatusCode();
        if (statusCode == HttpResponseStatus.NO_CONTENT.code()) {
            return Result.of(Collections.emptyList(), Collections.emptyList());
        } else if (statusCode == HttpResponseStatus.BAD_REQUEST.code()) {
            return Result.emptyWithError(BidderError.badInput("Invalid request."));
        } else if (statusCode != HttpResponseStatus.OK.code()) {
            return Result.emptyWithError(BidderError.badServerResponse(String.format("Unexpected HTTP status %s.",
                    statusCode)));
        }

        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return extractBids(bidResponse, httpCall.getResponse().getHeaders());
        } catch (DecodeException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private Result<List<BidderBid>> extractBids(BidResponse bidResponse, MultiMap headers) {
        if (bidResponse == null || bidResponse.getSeatbid() == null) {
            return Result.of(Collections.emptyList(), Collections.emptyList());
        }

        final List<BidderError> errors = new ArrayList<>();
        final List<BidderBid> bidderBids = bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> bidderBid(bid, bidResponse.getCur(), headers, errors))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return Result.of(bidderBids, errors);
    }

    private BidderBid bidderBid(Bid bid, String currency, MultiMap headers, List<BidderError> errors) {
        try {
            final String markupType = getAdMarkupType(headers, bid.getAdm());
            final Bid updateBid = bid.toBuilder().adm(renderAdMarkup(markupType, bid.getAdm())).build();
            return BidderBid.of(updateBid, getBidType(markupType), currency);
        } catch (PreBidException e) {
            errors.add(BidderError.badInput(e.getMessage()));
            return null;
        }
    }

    private static String getAdMarkupType(MultiMap headers, String adm) {
        final String adMarkupType = headers.get("X-SMT-ADTYPE");
        if (StringUtils.isNotBlank(adMarkupType)) {
            return adMarkupType;
        }
        if (adm.startsWith("image")) {
            return SMT_AD_TYPE_IMG;
        }
        if (adm.startsWith("richmedia")) {
            return SMT_ADTYPE_RICHMEDIA;
        }
        if (adm.startsWith("<?xml")) {
            return SMT_ADTYPE_VIDEO;
        }
        throw new PreBidException(String.format("Invalid ad markup %s", adm));
    }

    private String renderAdMarkup(String markupType, String adm) {
        switch (markupType) {
            case SMT_AD_TYPE_IMG:
                return extractAdmImage(adm);
            case SMT_ADTYPE_RICHMEDIA:
                return extractAdmRichMedia(adm);
            case SMT_ADTYPE_VIDEO:
                return markupType;
            default:
                throw new PreBidException(String.format("Unknown markup type %s", markupType));
        }
    }

    private static BidType getBidType(String markupType) {
        switch (markupType) {
            case SMT_AD_TYPE_IMG:
            case SMT_ADTYPE_RICHMEDIA:
                return BidType.banner;
            case SMT_ADTYPE_VIDEO:
                return BidType.video;
            default:
                throw new PreBidException(String.format("Invalid markupType %s", markupType));
        }
    }

    private String extractAdmImage(String adm) {
        final SmaatoImageAd imageAd = admToAd(adm, SmaatoImageAd.class);
        final SmaatoImage image = imageAd.getImage();
        if (image == null) {
            return adm;
        }

        final StringBuilder clickEvent = new StringBuilder();
        CollectionUtils.emptyIfNull(image.getClickTrackers())
                .forEach(tracker -> clickEvent.append(String.format(
                        "fetch(decodeURIComponent('%s'.replace(/\\+/g, ' ')), {cache: 'no-cache'});",
                        HttpUtil.encodeUrl(tracker))));

        final StringBuilder impressionTracker = new StringBuilder();
        CollectionUtils.emptyIfNull(image.getImpressionTrackers())
                .forEach(tracker -> impressionTracker.append(
                        String.format("<img src=\"%s\" alt=\"\" width=\"0\" height=\"0\"/>", tracker)));

        final SmaatoImg img = image.getImg();

        return String.format("<div style=\"cursor:pointer\" onclick=\"%s;window.open(decodeURIComponent"
                        + "('%s'.replace(/\\+/g, ' ')));\"><img src=\"%s\" width=\"%d\" height=\"%d\"/>%s</div>",
                clickEvent.toString(),
                HttpUtil.encodeUrl(StringUtils.stripToEmpty(getIfNotNull(img, SmaatoImg::getCtaurl))),
                StringUtils.stripToEmpty(getIfNotNull(img, SmaatoImg::getUrl)),
                getIfNotNull(img, SmaatoImg::getW),
                getIfNotNull(img, SmaatoImg::getH),
                impressionTracker.toString());
    }

    private String extractAdmRichMedia(String adm) {
        final SmaatoRichMediaAd richMediaAd = admToAd(adm, SmaatoRichMediaAd.class);
        final SmaatoRichmedia richmedia = richMediaAd.getRichmedia();
        if (richmedia == null) {
            return adm;
        }

        final StringBuilder clickEvent = new StringBuilder();
        CollectionUtils.emptyIfNull(richmedia.getClickTrackers())
                .forEach(tracker -> clickEvent.append(
                        String.format("fetch(decodeURIComponent('%s'), {cache: 'no-cache'});",
                                HttpUtil.encodeUrl(tracker))));

        final StringBuilder impressionTracker = new StringBuilder();
        CollectionUtils.emptyIfNull(richmedia.getImpressionTrackers())
                .forEach(tracker -> impressionTracker.append(
                        String.format("<img src=\"%s\" alt=\"\" width=\"0\" height=\"0\"/>",
                                tracker)));

        return String.format("<div onclick=\"%s\">%s%s</div>",
                clickEvent.toString(),
                StringUtils.stripToEmpty(getIfNotNull(richmedia.getMediadata(), SmaatoMediaData::getContent)),
                impressionTracker.toString());
    }

    private <T> T admToAd(String value, Class<T> className) {
        try {
            return mapper.decodeValue(value, className);
        } catch (DecodeException e) {
            throw new PreBidException(String.format("Cannot decode bid.adm: %s", e.getMessage()), e);
        }
    }

    private static <T, R> R getIfNotNull(T target, Function<T, R> getter) {
        return target != null ? getter.apply(target) : null;
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}
