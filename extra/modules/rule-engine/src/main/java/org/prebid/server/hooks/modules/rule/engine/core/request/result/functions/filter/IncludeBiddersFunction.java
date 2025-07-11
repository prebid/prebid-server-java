package org.prebid.server.hooks.modules.rule.engine.core.request.result.functions.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Imp;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.util.StreamUtil;

import java.util.Set;
import java.util.stream.Collectors;

public class IncludeBiddersFunction extends FilterBiddersFunction {

    public static final String NAME = "includeBidders";

    public IncludeBiddersFunction(ObjectMapper objectMapper, BidderCatalog bidderCatalog) {
        super(objectMapper, bidderCatalog);
    }

    @Override
    protected Set<String> biddersToRemove(Imp imp, Set<String> bidders, Boolean ifSyncedId, UidsCookie uidsCookie) {
        final ObjectNode biddersNode = FilterUtils.bidderNode(imp.getExt());

        return StreamUtil.asStream(biddersNode.fieldNames())
                .filter(bidder -> !FilterUtils.containsIgnoreCase(bidders.stream(), bidder))
                .filter(bidder ->
                        ifSyncedId == null || ifSyncedId != isBidderIdSynced(bidder.toLowerCase(), uidsCookie))
                .collect(Collectors.toSet());
    }

    @Override
    protected String name() {
        return NAME;
    }
}
