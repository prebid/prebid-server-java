package org.prebid.server.hooks.modules.rule.engine.core.request.result.functions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Imp;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.model.UpdateResult;

import java.util.List;

public class ExcludeBiddersFunction extends FilterBiddersFunction {

    public static final String NAME = "excludeBidders";

    public ExcludeBiddersFunction(ObjectMapper objectMapper, BidderCatalog bidderCatalog) {
        super(objectMapper, bidderCatalog);
    }

    @Override
    protected UpdateResult<Imp> filterBidders(Imp imp,
                                              List<String> bidders,
                                              Boolean ifSyncedId,
                                              UidsCookie uidsCookie) {

        boolean updated = false;
        final ObjectNode updatedExt = imp.getExt().deepCopy();
        for (String bidder : bidders) {
            if (ifSyncedId != null && ifSyncedId != isBidderIdSynced(bidder, uidsCookie)) {
                continue;
            }

            updatedExt.remove(bidder);
            updated = true;
        }

        return updated
                ? UpdateResult.updated(imp.toBuilder().ext(updatedExt).build())
                : UpdateResult.unaltered(imp);
    }
}
