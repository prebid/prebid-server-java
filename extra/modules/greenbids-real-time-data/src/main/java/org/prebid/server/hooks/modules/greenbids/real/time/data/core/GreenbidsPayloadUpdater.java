package org.prebid.server.hooks.modules.greenbids.real.time.data.core;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class GreenbidsPayloadUpdater {

    private GreenbidsPayloadUpdater() {

    }

    public static BidRequest update(BidRequest bidRequest, Map<String, Map<String, Boolean>> impsBiddersFilterMap) {
        return bidRequest.toBuilder()
                .imp(updateImps(bidRequest, impsBiddersFilterMap))
                .build();
    }

    private static List<Imp> updateImps(BidRequest bidRequest, Map<String, Map<String, Boolean>> impsBiddersFilterMap) {
        return bidRequest.getImp().stream()
                .filter(imp -> isImpKept(impsBiddersFilterMap.get(imp.getId())))
                .map(imp -> updateImp(imp, impsBiddersFilterMap.get(imp.getId())))
                .toList();
    }

    private static boolean isImpKept(Map<String, Boolean> bidderFilterMap) {
        return bidderFilterMap.values().stream().anyMatch(isKept -> isKept);
    }

    private static Imp updateImp(Imp imp, Map<String, Boolean> bidderFilterMap) {
        return imp.toBuilder()
                .ext(updateImpExt(imp.getExt(), bidderFilterMap))
                .build();
    }

    private static ObjectNode updateImpExt(ObjectNode impExt, Map<String, Boolean> bidderFilterMap) {
        final ObjectNode updatedExt = impExt.deepCopy();
        Optional.ofNullable((ObjectNode) updatedExt.get("prebid"))
                .map(prebidNode -> (ObjectNode) prebidNode.get("bidder"))
                .ifPresent(bidderNode ->
                        bidderFilterMap.entrySet().stream()
                                .filter(entry -> !entry.getValue())
                                .map(Map.Entry::getKey)
                                .forEach(bidderNode::remove));
        return updatedExt;
    }
}
