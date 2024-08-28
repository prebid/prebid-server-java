package org.prebid.server.hooks.modules.greenbids.real.time.data.model.result;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.Partner;
import org.prebid.server.hooks.v1.InvocationAction;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class GreenbidsInvocationResult {

    private static final int RANGE_16_BIT_INTEGER_DIVISION_BASIS = 0x10000;

    public BidRequest updatedBidRequest;

    public InvocationAction invocationAction;

    public AnalyticsResult analyticsResult;

    public GreenbidsInvocationResult() {

    }

    public void prepareInvocationResult(
            Partner partner,
            BidRequest bidRequest,
            Map<String, Map<String, Boolean>> impsBiddersFilterMap) {
        final String greenbidsId = UUID.randomUUID().toString();
        final boolean isExploration = determineIsExploration(partner, greenbidsId);

        final List<Imp> impsWithFilteredBidders = updateImps(bidRequest, impsBiddersFilterMap);
        updatedBidRequest = !isExploration
                ? bidRequest.toBuilder().imp(impsWithFilteredBidders).build()
                : bidRequest;
        invocationAction = !isExploration
                ? InvocationAction.update
                : InvocationAction.no_action;
        final Map<String, Map<String, Boolean>> impsBiddersFilterMapToAnalyticsTag = !isExploration
                ? impsBiddersFilterMap
                : keepAllBiddersForAnalyticsResult(impsBiddersFilterMap);
        final Map<String, Ortb2ImpExtResult> ort2ImpExtResultMap = createOrtb2ImpExt(
                bidRequest, impsBiddersFilterMapToAnalyticsTag, greenbidsId, isExploration);
        analyticsResult = AnalyticsResult.of(
                "success", ort2ImpExtResultMap, null, null);
    }

    private Boolean determineIsExploration(Partner partner, String greenbidsId) {
        final int hashInt = Integer.parseInt(
                greenbidsId.substring(greenbidsId.length() - 4), 16);
        return hashInt < partner.getExplorationRate() * RANGE_16_BIT_INTEGER_DIVISION_BASIS;
    }

    private List<Imp> updateImps(BidRequest bidRequest, Map<String, Map<String, Boolean>> impsBiddersFilterMap) {
        return bidRequest.getImp().stream()
                .map(imp -> updateImp(imp, impsBiddersFilterMap.get(imp.getId())))
                .toList();
    }

    private Imp updateImp(Imp imp, Map<String, Boolean> bidderFilterMap) {
        return imp.toBuilder()
                .ext(updateImpExt(imp.getExt(), bidderFilterMap))
                .build();
    }

    private ObjectNode updateImpExt(ObjectNode impExt, Map<String, Boolean> bidderFilterMap) {
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

    private Map<String, Map<String, Boolean>> keepAllBiddersForAnalyticsResult(
            Map<String, Map<String, Boolean>> impsBiddersFilterMap) {
        impsBiddersFilterMap.replaceAll((impId, biddersMap) ->
                biddersMap
                        .entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, entry -> true)));
        return impsBiddersFilterMap;
    }

    private Map<String, Ortb2ImpExtResult> createOrtb2ImpExt(
            BidRequest bidRequest,
            Map<String, Map<String, Boolean>> impsBiddersFilterMap,
            String greenbidsId,
            Boolean isExploration) {
        return bidRequest.getImp().stream()
                .collect(Collectors.toMap(
                        Imp::getId,
                        imp -> {
                            final String tid = imp.getExt().get("tid").asText();
                            final Map<String, Boolean> impBiddersFilterMap = impsBiddersFilterMap.get(imp.getId());
                            final ExplorationResult explorationResult = ExplorationResult.of(
                                    greenbidsId, impBiddersFilterMap, isExploration);
                            return Ortb2ImpExtResult.of(
                                    explorationResult, tid);
                        }));
    }
}
