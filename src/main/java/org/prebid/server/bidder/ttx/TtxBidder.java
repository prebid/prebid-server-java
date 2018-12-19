package org.prebid.server.bidder.ttx;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import io.vertx.core.json.Json;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.OpenrtbBidder;
import org.prebid.server.bidder.ttx.proto.TtxImpExt;
import org.prebid.server.bidder.ttx.proto.TtxImpExtTtx;
import org.prebid.server.proto.openrtb.ext.request.ttx.ExtImpTtx;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.util.Collections;
import java.util.List;

public class TtxBidder extends OpenrtbBidder<ExtImpTtx> {

    public TtxBidder(String endpointUrl) {
        super(endpointUrl, RequestCreationStrategy.SINGLE_REQUEST, ExtImpTtx.class);
    }

    @Override
    protected void modifyRequest(BidRequest bidRequest, BidRequest.BidRequestBuilder requestBuilder,
                                 List<Imp> modifiedImps, List<ExtImpTtx> impExts) {
        final Imp firstImp = modifiedImps.get(0);
        final ExtImpTtx firstImpExt = impExts.get(0);

        final String zoneId = firstImpExt.getZoneId();
        final TtxImpExt ttxImpExt = TtxImpExt.of(
                TtxImpExtTtx.of(firstImpExt.getProductId(), StringUtils.isNotBlank(zoneId) ? zoneId : null));

        final Imp modifiedFirstImp = firstImp.toBuilder().ext(Json.mapper.valueToTree(ttxImpExt)).build();

        if (modifiedImps.size() == 1) {
            requestBuilder.imp(Collections.singletonList(modifiedFirstImp));
        } else {
            modifiedImps.set(0, modifiedFirstImp);
            requestBuilder.imp(modifiedImps);
        }
        requestBuilder.site(modifySite(bidRequest.getSite(), firstImpExt.getSiteId()));
    }

    private static Site modifySite(Site site, String siteId) {
        final Site.SiteBuilder siteBuilder = site == null ? Site.builder() : site.toBuilder();
        return siteBuilder
                .id(siteId)
                .build();
    }

    @Override
    protected BidType getBidType(String impId, List<Imp> imps) {
        return BidType.banner;
    }
}
