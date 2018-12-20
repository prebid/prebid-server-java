package org.prebid.server.bidder.conversant;

import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.OpenrtbBidder;
import org.prebid.server.bidder.model.ImpWithExt;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.request.conversant.ExtImpConversant;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ConversantBidder extends OpenrtbBidder<ExtImpConversant> {

    // List of API frameworks supported by the publisher
    private static final Set<Integer> APIS = IntStream.range(1, 7).boxed().collect(Collectors.toSet());

    // Options for the various bid response protocols that could be supported by an exchange
    private static final Set<Integer> PROTOCOLS = IntStream.range(1, 11).boxed().collect(Collectors.toSet());

    // Position of the ad as a relative measure of visibility or prominence
    private static final Set<Integer> AD_POSITIONS = IntStream.range(0, 8).boxed().collect(Collectors.toSet());

    private static final String DISPLAY_MANAGER = "prebid-s2s";
    private static final String DISPLAY_MANAGER_VER = "1.0.1";

    public ConversantBidder(String endpointUrl) {
        super(endpointUrl, RequestCreationStrategy.SINGLE_REQUEST, ExtImpConversant.class);
    }

    @Override
    protected void validateRequest(BidRequest bidRequest) throws PreBidException {
        if (bidRequest.getApp() != null) {
            throw new PreBidException("Conversant doesn't support App requests");
        }
    }

    @Override
    protected Imp modifyImp(Imp imp, ExtImpConversant impExt) throws PreBidException {
        // imp validation
        if (imp.getBanner() == null && imp.getVideo() == null) {
            throw new PreBidException(String.format("Invalid MediaType. Conversant supports only Banner and Video. "
                    + "Ignoring ImpID=%s", imp.getId()));
        }

        //imp.ext validation
        if (StringUtils.isBlank(impExt.getSiteId())) {
            throw new PreBidException("Missing site id");
        }

        // imp transformations
        final BigDecimal extBidfloor = impExt.getBidfloor();
        final String extTagId = impExt.getTagId();
        final Integer extSecure = impExt.getSecure();
        final boolean shouldChangeSecure = extSecure != null && (imp.getSecure() == null || imp.getSecure() == 0);
        final Banner impBanner = imp.getBanner();
        final Integer extPosition = impExt.getPosition();
        final Video impVideo = imp.getVideo();

        final Imp.ImpBuilder impBuilder = imp.toBuilder();

        if (impBanner != null && extPosition != null) {
            impBuilder.banner(impBanner.toBuilder().pos(extPosition).build());
        }

        return impBuilder
                .displaymanager(DISPLAY_MANAGER)
                .displaymanagerver(DISPLAY_MANAGER_VER)
                .bidfloor(extBidfloor != null ? extBidfloor : imp.getBidfloor())
                .tagid(extTagId != null ? extTagId : imp.getTagid())
                .secure(shouldChangeSecure ? extSecure : imp.getSecure())
                .video(impVideo != null ? modifyVideo(impVideo, impExt) : null)
                .build();
    }

    private static Video modifyVideo(Video video, ExtImpConversant impExt) {
        final List<String> extMimes = impExt.getMimes();
        final Integer extMaxduration = impExt.getMaxduration();
        final Integer extPosition = impExt.getPosition();
        final List<Integer> extProtocols = impExt.getProtocols();
        final List<Integer> extApi = impExt.getApi();
        return video.toBuilder()
                .mimes(extMimes != null ? extMimes : video.getMimes())
                .maxduration(extMaxduration != null ? extMaxduration : video.getMaxduration())
                .pos(makePosition(extPosition, video.getPos()))
                .api(makeApi(extApi, video.getApi()))
                .protocols(makeProtocols(extProtocols, video.getProtocols()))
                .build();
    }

    private static Integer makePosition(Integer position, Integer videoPos) {
        return isValidPosition(position) ? position : isValidPosition(videoPos) ? videoPos : null;
    }

    private static boolean isValidPosition(Integer position) {
        return position != null && AD_POSITIONS.contains(position);
    }

    private static List<Integer> makeApi(List<Integer> extApi, List<Integer> videoApi) {
        final List<Integer> protocols = CollectionUtils.isNotEmpty(extApi) ? extApi : videoApi;
        return CollectionUtils.isNotEmpty(protocols)
                ? protocols.stream().filter(APIS::contains).collect(Collectors.toList()) : videoApi;
    }

    private static List<Integer> makeProtocols(List<Integer> extProtocols, List<Integer> videoProtocols) {
        final List<Integer> protocols = CollectionUtils.isNotEmpty(extProtocols) ? extProtocols : videoProtocols;
        return CollectionUtils.isNotEmpty(protocols)
                ? protocols.stream().filter(PROTOCOLS::contains).collect(Collectors.toList()) : videoProtocols;
    }

    @Override
    protected void modifyRequest(BidRequest bidRequest, BidRequest.BidRequestBuilder requestBuilder,
                                 List<ImpWithExt<ExtImpConversant>> impsWithExts) {
        final String extSiteId = impsWithExts.stream()
                .map(ImpWithExt::getImpExt)
                .map(ExtImpConversant::getSiteId)
                .reduce((first, second) -> second).orElse(null);
        final Integer extMobile = impsWithExts.stream()
                .map(ImpWithExt::getImpExt)
                .map(ExtImpConversant::getMobile)
                .filter(Objects::nonNull)
                .reduce((first, second) -> second).orElse(null);

        requestBuilder.site(modifySite(bidRequest.getSite(), extSiteId, extMobile));
    }

    private static Site modifySite(Site site, String extSiteId, Integer extMobile) {
        final Site.SiteBuilder siteBuilder = site == null ? Site.builder() : site.toBuilder();
        if (extMobile != null) {
            siteBuilder.mobile(extMobile);
        }
        return siteBuilder
                .id(extSiteId)
                .build();
    }

    @Override
    protected BidType getBidType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId) && imp.getVideo() != null) {
                return BidType.video;
            }
        }
        return BidType.banner;
    }
}
