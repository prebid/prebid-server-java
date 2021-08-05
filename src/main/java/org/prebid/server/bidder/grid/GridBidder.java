package org.prebid.server.bidder.grid;

import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.OpenrtbBidder;
import org.prebid.server.bidder.grid.model.ExtImpGrid;
import org.prebid.server.bidder.grid.model.GridExtImp;
import org.prebid.server.bidder.grid.model.GridExtImpData;
import org.prebid.server.bidder.grid.model.GridExtImpDataAdServer;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.util.List;

public class GridBidder extends OpenrtbBidder<ExtImpGrid> {

    public GridBidder(String endpointUrl, JacksonMapper mapper) {
        super(endpointUrl, RequestCreationStrategy.SINGLE_REQUEST, ExtImpGrid.class, mapper);
    }

    @Override
    protected Imp modifyImp(Imp imp, ExtImpGrid impExt) {
        if (impExt.getUid() == null || impExt.getUid() == 0) {
            throw new PreBidException("uid is empty");
        }

        final GridExtImp gridExtImp;
        try {
            gridExtImp = mapper.mapper().convertValue(imp.getExt(), GridExtImp.class);
        } catch (Exception e) {
            throw new PreBidException(e.getMessage());
        }

        final GridExtImpData extImpData = gridExtImp != null ? gridExtImp.getData() : null;
        final GridExtImpDataAdServer adServer = extImpData != null ? extImpData.getAdServer() : null;
        final String adSlot = adServer != null ? adServer.getAdSlot() : null;
        if (StringUtils.isNotEmpty(adSlot)) {

            final GridExtImp modifiedGridExtImp = gridExtImp.toBuilder()
                    .gpid(adSlot)
                    .build();

            return imp.toBuilder().ext(mapper.mapper().valueToTree(modifiedGridExtImp)).build();
        }

        return imp;
    }

    @Override
    protected BidType getBidType(Bid bid, List<Imp> imps) {
        final String impId = bid.getImpid();
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getBanner() != null) {
                    return BidType.banner;
                }
                if (imp.getVideo() != null) {
                    return BidType.video;
                }
                throw new PreBidException(String.format("Unknown impression type for ID: %s", impId));
            }
        }
        throw new PreBidException(String.format("Failed to find impression for ID: %s", impId));
    }
}
