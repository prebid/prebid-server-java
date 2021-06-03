package org.prebid.server.bidder.grid;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.OpenrtbBidder;
import org.prebid.server.bidder.grid.model.ExtImp;
import org.prebid.server.bidder.grid.model.ExtImpGrid;
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
        ExtImp extImp = mapper.mapper().convertValue(
                imp.getExt(),
                ExtImp.class
        );

        if (extImp != null
                && extImp.getExtImpData() != null
                && extImp.getExtImpData().getAdServer() != null
                && StringUtils.isNotEmpty(extImp.getExtImpData().getAdServer().getAdSlot())) {

            ExtImp modifiedExtImp = ExtImp.of(extImp.getPrebid(),
                    extImp.getBidder(),
                    extImp.getExtImpData(),
                    extImp.getExtImpData().getAdServer().getAdSlot());

            ObjectNode modifiedExt = mapper.mapper().valueToTree(
                    modifiedExtImp
            );

            return imp.toBuilder().ext(modifiedExt).build();
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
