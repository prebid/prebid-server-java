package org.prebid.server.bidder.grid;

import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.OpenrtbBidder;
import org.prebid.server.bidder.grid.model.ExtImpGrid;
import org.prebid.server.bidder.grid.model.GridExtImp;
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
        final GridExtImp gridExtImp = mapper.mapper().convertValue(
                imp.getExt(),
                GridExtImp.class
        );

        String adSlot = getAdSlot(gridExtImp);

        if (adSlot != null) {

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

    private static String getAdSlot(GridExtImp gridExtImp) {
        if (gridExtImp != null
                && gridExtImp.getGridExtImpData() != null
                && gridExtImp.getGridExtImpData().getAdServer() != null
                && StringUtils.isNotEmpty(gridExtImp.getGridExtImpData().getAdServer().getAdSlot())) {
            return gridExtImp.getGridExtImpData().getAdServer().getAdSlot();
        }

        return null;
    }
}
