package org.prebid.server.bidder.pulsepoint;

import com.iab.openrtb.request.App;
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
        return imp.toBuilder().tagid(Objects.toString(extImpPulsepoint.getTagId())).build();
    }

    @Override
    protected void modifyRequest(BidRequest bidRequest, BidRequest.BidRequestBuilder requestBuilder,
                                 List<ImpWithExt<ExtImpPulsepoint>> impsWithExts) {
        final String pubId = impsWithExts.stream()
                .map(ImpWithExt::getImpExt)
                .map(ExtImpPulsepoint::getPublisherId)
                .filter(this::isValidPublisherId)
                .findFirst()
                .map(Objects::toString)
                .orElse(StringUtils.EMPTY);

        final Site site = bidRequest.getSite();
        final App app = bidRequest.getApp();
        if (site != null) {
            requestBuilder.site(modifySite(site, pubId));
        } else if (app != null) {
            requestBuilder.app(modifyApp(app, pubId));
        }
    }

    private boolean isValidPublisherId(Integer publisherId) {
        return publisherId != null && publisherId > 0;
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

    protected BidType getBidType(Bid bid, List<Imp> imps) {
        final String impId = bid.getImpid();
        BidType bidType = null;
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getBanner() != null) {
                    bidType = BidType.banner;
                } else if (imp.getVideo() != null) {
                    bidType = BidType.video;
                } else if (imp.getAudio() != null) {
                    bidType = BidType.audio;
                } else if (imp.getXNative() != null) {
                    bidType = BidType.xNative;
                }
            }
        }
        return bidType;
    }
}
