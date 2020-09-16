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
import org.prebid.server.bidder.smaato.proto.SmaatoImageAd;
import org.prebid.server.bidder.smaato.proto.SmaatoRichMediaAd;
import org.prebid.server.bidder.smaato.proto.SmaatoSiteExt;
import org.prebid.server.bidder.smaato.proto.SmaatoUserExt;
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

        final Site site = request.getSite();
        final Site modifiedSite = site != null ? modifySite(site, firstPublisherId) : null;
        final User user = request.getUser();
        final User modifiedUser = user != null && user.getExt() != null ? modifyUser(user) : null;

        final BidRequest outgoingRequest = request.toBuilder()
                .imp(imps)
                .site(modifiedSite)
                .user(modifiedUser)
                .ext(mapper.fillExtension(ExtRequest.empty(), SmaatoBidRequestExt.of(CLIENT_VERSION)))
                .build();
        final String body = mapper.encode(outgoingRequest);

        return Result.of(Collections.singletonList(
                HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(endpointUrl)
                        .headers(HttpUtil.headers())
                        .payload(outgoingRequest)
                        .body(body)
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

    private Imp modifyImp(Imp imp, String adspaceId) {
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

    private Banner modifyBanner(Banner banner) {
        if (banner.getW() != null && banner.getH() != null) {
            return banner;
        }
        final List<Format> format = banner.getFormat();
        if (CollectionUtils.isEmpty(format)) {
            throw new PreBidException(String.format(
                    "No sizes provided for Banner %s", format));
        }
        final Format firstFormat = format.get(0);
        return Banner.builder().w(firstFormat.getW()).h(firstFormat.getH()).build();
    }

    private Site modifySite(Site site, String firstPublisherId) {
        final Site.SiteBuilder siteBuilder = site.toBuilder();
        siteBuilder.publisher(Publisher.builder().id(firstPublisherId).build());
        final ExtSite ext = site.getExt();
        if (ext != null) {
            final SmaatoSiteExt smaatoSiteExt = convertExt(ext, SmaatoSiteExt.class);
            siteBuilder.keywords(smaatoSiteExt.getData().getKeywords()).ext(null);
        }
        return siteBuilder.build();
    }

    private <T, S> T convertExt(S ext, Class<T> className) {
        return mapper.mapper().convertValue(ext, className);
    }

    private User modifyUser(User user) {
        final ExtUser ext = user.getExt();
        final Map<String, ObjectNode> mapUserExt = parseUserExt(ext);
        final SmaatoUserExt smaatoUserExt = convertExt(ext, SmaatoUserExt.class);
        final User.UserBuilder userBuilder = user.toBuilder();
        final SmaatoUserExtData data = smaatoUserExt.getData();
        final String gender = data.getGender();
        if (StringUtils.isNotBlank(gender)) {
            userBuilder.gender(gender);
        }
        final Integer yob = data.getYob();
        if (yob != 0) {
            userBuilder.yob(yob);
        }
        final String keywords = data.getKeywords();
        if (StringUtils.isNotBlank(keywords)) {
            userBuilder.keywords(keywords);
        }
        mapUserExt.remove("data");
        userBuilder.ext(convertExt(mapUserExt, ExtUser.class));
        return userBuilder.build();
    }

    private Map<String, ObjectNode> parseUserExt(ExtUser ext) {
        return mapper.mapper().convertValue(ext, new TypeReference<Map<String, ObjectNode>>() {
        });
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

    private String getAdMarkupType(MultiMap headers, String adm) {
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
        final SmaatoImageAd image = mapper.decodeValue(adm, SmaatoImageAd.class);
        final StringBuilder clickEvent = new StringBuilder();
        image.getImage().getClickTrackers().forEach(tracker -> clickEvent.append(
                String.format("fetch(decodeURIComponent('%s'.replace(/\\+/g, ' ')), {cache: 'no-cache'});",
                        HttpUtil.encodeUrl(tracker))));

        final StringBuilder impressionTracker = new StringBuilder();
        image.getImage().getImpressionTrackers().forEach(tracker -> impressionTracker.append(
                String.format("<img src=\"%s\" alt=\"\" width=\"0\" height=\"0\"/>", tracker)));

        return String.format("<div style=\"cursor:pointer\" onclick=\"%s;window.open(decodeURIComponent"
                        + "('%s'.replace(/\\+/g, ' ')));\"><img src=\"%s\" width=\"%d\" height=\"%d\"/>%s</div>",
                clickEvent.toString(),
                HttpUtil.encodeUrl(image.getImage().getImg().getCtaurl()), image.getImage().getImg().getUrl(),
                image.getImage().getImg().getW(), image.getImage().getImg().getH(),
                impressionTracker.toString());
    }

    private String extractAdmRichMedia(String adm) {
        final SmaatoRichMediaAd richMediaAd = mapper.decodeValue(adm, SmaatoRichMediaAd.class);
        final StringBuilder clickEvent = new StringBuilder();
        richMediaAd.getRichmedia().getClickTrackers().forEach(tracker -> clickEvent.append(
                String.format("fetch(decodeURIComponent('%s'), {cache: 'no-cache'});",
                        HttpUtil.encodeUrl(tracker))));
        final StringBuilder impressionTracker = new StringBuilder();
        richMediaAd.getRichmedia().getImpressionTrackers().forEach(tracker -> impressionTracker.append(
                String.format("<img src=\"%s\" alt=\"\" width=\"0\" height=\"0\"/>", tracker)));

        return String.format("<div onclick=\"%s\">%s%s</div>", clickEvent,
                richMediaAd.getRichmedia().getMediadata().getContent(),
                impressionTracker);
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}
