package org.prebid.server.bidder.gumgum;

import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.bidder.OpenrtbBidder;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.request.gumgum.ExtImpGumgum;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.util.List;
import java.util.Objects;

public class GumgumBidder extends OpenrtbBidder<ExtImpGumgum> {

    public GumgumBidder(String endpointUrl) {
        super(endpointUrl, RequestCreationStrategy.SINGLE_REQUEST, ExtImpGumgum.class);
    }

    @Override
    protected Imp modifyImp(Imp imp, ExtImpGumgum impExt) throws PreBidException {
        if (imp.getBanner() != null) {
            final Banner banner = imp.getBanner();
            final List<Format> format = banner.getFormat();
            if (banner.getH() == null && banner.getW() == null && CollectionUtils.isNotEmpty(format)) {
                final Format firstFormat = format.get(0);
                final Banner modifiedBanner = banner.toBuilder().w(firstFormat.getW()).h(firstFormat.getH()).build();
                return imp.toBuilder()
                        .banner(modifiedBanner)
                        .build();
            } else {
                return imp;
            }
        }
        return null;
    }

    @Override
    protected void modifyRequest(BidRequest bidRequest, BidRequest.BidRequestBuilder requestBuilder,
                                 List<Imp> modifiedImps, List<ExtImpGumgum> impExts) {

        final String trackingId = impExts.stream()
                .map(ExtImpGumgum::getZone)
                .filter(Objects::nonNull)
                .reduce((first, second) -> second)
                .orElse(null);

        requestBuilder.site(modifySite(bidRequest.getSite(), trackingId));
    }

    private static Site modifySite(Site site, String trackingId) {
        if (site != null) {
            return site.toBuilder().id(trackingId).build();
        }
        return null;
    }

    @Override
    protected BidType getBidType(String impId, List<Imp> imps) {
        return BidType.banner;
    }
}
