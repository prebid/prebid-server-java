package org.prebid.server.bidder.ttx;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.OpenrtbBidder;
import org.prebid.server.bidder.model.ImpWithExt;
import org.prebid.server.bidder.ttx.proto.TtxImpExt;
import org.prebid.server.bidder.ttx.proto.TtxImpExtTtx;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.ttx.ExtImpTtx;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class TtxBidder extends OpenrtbBidder<ExtImpTtx> {

    public TtxBidder(String endpointUrl, JacksonMapper mapper) {
        super(endpointUrl, RequestCreationStrategy.SINGLE_REQUEST, ExtImpTtx.class, mapper);
    }

    @Override
    protected void modifyRequest(BidRequest bidRequest, BidRequest.BidRequestBuilder requestBuilder,
                                 List<ImpWithExt<ExtImpTtx>> impsWithExts) {
        final List<Imp> modifiedImps = impsWithExts.stream()
                .map(ImpWithExt::getImp)
                .collect(Collectors.toList());
        final Imp firstImp = modifiedImps.get(0);
        final ExtImpTtx firstImpExt = impsWithExts.get(0).getImpExt();

        final String zoneId = firstImpExt.getZoneId();
        final TtxImpExt ttxImpExt = TtxImpExt.of(
                TtxImpExtTtx.of(firstImpExt.getProductId(), StringUtils.isNotBlank(zoneId) ? zoneId : null));

        final Imp modifiedFirstImp = firstImp.toBuilder().ext(mapper.mapper().valueToTree(ttxImpExt)).build();

        if (modifiedImps.size() == 1) {
            requestBuilder.imp(Collections.singletonList(modifiedFirstImp));
        } else {
            final List<Imp> subList = modifiedImps.subList(1, modifiedImps.size());
            final List<Imp> finalizedImps = new ArrayList<>(subList.size() + 1);
            finalizedImps.add(modifiedFirstImp);
            finalizedImps.addAll(subList);
            requestBuilder.imp(finalizedImps);
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
