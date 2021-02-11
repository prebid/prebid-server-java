package org.prebid.server.bidder.pulsepoint;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.OpenrtbBidder;
import org.prebid.server.bidder.model.ImpWithExt;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.pulsepoint.ExtImpPulsepoint;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.util.List;
import java.util.Objects;

public class PulsepointBidder extends OpenrtbBidder<ExtImpPulsepoint> {

    public PulsepointBidder(String endpointUrl, JacksonMapper mapper) {
        super(endpointUrl, RequestCreationStrategy.SINGLE_REQUEST, ExtImpPulsepoint.class, mapper);
    }

    @Override
    protected Imp modifyImp(Imp imp, ExtImpPulsepoint extImpPulsepoint) throws PreBidException {
        // imp validation
        if (imp.getBanner() == null) {
            throw new PreBidException(String.format("Invalid MediaType. Pulsepoint supports only Banner type. "
                    + "Ignoring ImpID=%s", imp.getId()));
        }

        // imp.ext validation
        final Integer publisherId = extImpPulsepoint.getPublisherId();
        if (publisherId == null || publisherId == 0) {
            throw new PreBidException("Missing PublisherId param cp");
        }
        final Integer tagId = extImpPulsepoint.getTagId();
        if (tagId == null || tagId == 0) {
            throw new PreBidException("Missing TagId param ct");
        }
        final String adSize = extImpPulsepoint.getAdSize();
        if (StringUtils.isEmpty(adSize)) {
            throw new PreBidException("Missing AdSize param cf");
        }
        if (adSize.toLowerCase().split("x").length != 2) {
            throw new PreBidException(String.format("Invalid AdSize param %s", adSize));
        }

        // impression modifications and additional validation
        final String[] sizes = extImpPulsepoint.getAdSize().toLowerCase().split("x");
        final int width;
        final int height;
        try {
            width = Integer.parseInt(sizes[0]);
            height = Integer.parseInt(sizes[1]);
        } catch (NumberFormatException e) {
            throw new PreBidException(String.format("Invalid Width or Height param %s x %s", sizes[0], sizes[1]));
        }
        final Banner modifiedBanner = imp.getBanner().toBuilder().w(width).h(height).build();

        return imp.toBuilder()
                .tagid(String.valueOf(extImpPulsepoint.getTagId()))
                .banner(modifiedBanner)
                .build();
    }

    @Override
    protected void modifyRequest(BidRequest bidRequest, BidRequest.BidRequestBuilder requestBuilder,
                                 List<ImpWithExt<ExtImpPulsepoint>> impsWithExts) {
        final Integer pubId = impsWithExts.stream()
                .map(ImpWithExt::getImpExt)
                .map(ExtImpPulsepoint::getPublisherId)
                .filter(Objects::nonNull)
                .reduce((first, second) -> second)
                .orElse(null);
        final Site site = bidRequest.getSite();
        final App app = bidRequest.getApp();
        if (site != null) {
            requestBuilder.site(modifySite(site, String.valueOf(pubId)));
        } else if (app != null) {
            requestBuilder.app(modifyApp(app, String.valueOf(pubId)));
        }
    }

    private static Site modifySite(Site site, String publisherId) {
        return site.toBuilder()
                .publisher(site.getPublisher() == null
                        ? Publisher.builder().id(publisherId).build()
                        : site.getPublisher().toBuilder().id(publisherId).build())
                .build();
    }

    private static App modifyApp(App app, String publisherId) {
        return app.toBuilder()
                .publisher(app.getPublisher() == null
                        ? Publisher.builder().id(publisherId).build()
                        : app.getPublisher().toBuilder().id(publisherId).build())
                .build();
    }

    @Override
    protected BidType getBidType(Bid bid, List<Imp> imps) {
        return BidType.banner;
    }
}
