package org.prebid.server.hooks.modules.greenbids.real.time.data.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.analytics.reporter.greenbids.model.ExplorationResult;
import org.prebid.server.analytics.reporter.greenbids.model.Ortb2ImpExtResult;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.data.GreenbidsConfig;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.result.AnalyticsResult;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.result.GreenbidsInvocationResult;
import org.prebid.server.hooks.v1.InvocationAction;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class GreenbidsInvocationResultCreator {

    private static final int RANGE_16_BIT_INTEGER_DIVISION_BASIS = 0x10000;
    private static final double DEFAULT_EXPLORATION_RATE = 1.0;

    private GreenbidsInvocationResultCreator() {

    }

    public static GreenbidsInvocationResult create(GreenbidsConfig greenbidsConfig,
                                                   BidRequest bidRequest,
                                                   Map<String, Map<String, Boolean>> impsBiddersFilterMap) {

        final String greenbidsId = UUID.randomUUID().toString();
        final boolean isExploration = isExploration(greenbidsConfig, greenbidsId);

        final boolean allRejected = bidRequest.getImp().stream()
                .noneMatch(imp -> impsBiddersFilterMap.get(imp.getId()).values().stream().anyMatch(isKept -> isKept));

        final InvocationAction invocationAction = isExploration
                ? InvocationAction.no_action
                : allRejected
                    ? InvocationAction.reject
                    : InvocationAction.update;

        final Map<String, Ortb2ImpExtResult> ort2ImpExtResultMap = createOrtb2ImpExtForImps(
                bidRequest, impsBiddersFilterMap, greenbidsId, isExploration);
        final AnalyticsResult analyticsResult = AnalyticsResult.of("success", ort2ImpExtResultMap);
        return GreenbidsInvocationResult.of(invocationAction, analyticsResult);
    }

    private static boolean isExploration(GreenbidsConfig greenbidsConfig, String greenbidsId) {
        final double explorationRate = ObjectUtils.defaultIfNull(greenbidsConfig.getExplorationRate(), DEFAULT_EXPLORATION_RATE);
        final int hashInt = Integer.parseInt(greenbidsId.substring(greenbidsId.length() - 4), 16);
        return hashInt < explorationRate * RANGE_16_BIT_INTEGER_DIVISION_BASIS;
    }

    private static Map<String, Ortb2ImpExtResult> createOrtb2ImpExtForImps(
            BidRequest bidRequest,
            Map<String, Map<String, Boolean>> impsBiddersFilterMap,
            String greenbidsId,
            boolean isExploration) {

        return bidRequest.getImp().stream()
                .collect(Collectors.toMap(
                        Imp::getId,
                        imp -> createOrtb2ImpExt(imp, impsBiddersFilterMap, greenbidsId, isExploration)));
    }

    private static Ortb2ImpExtResult createOrtb2ImpExt(Imp imp,
                                                       Map<String, Map<String, Boolean>> impsBiddersFilterMap,
                                                       String greenbidsId,
                                                       boolean isExploration) {

        final String tid = Optional.ofNullable(imp)
                .map(Imp::getExt)
                .map(impExt -> impExt.get("tid"))
                .map(JsonNode::asText)
                .orElse(StringUtils.EMPTY);
        final Map<String, Boolean> impBiddersFilterMap = impsBiddersFilterMap.get(imp.getId());
        final ExplorationResult explorationResult = ExplorationResult.of(
                greenbidsId, impBiddersFilterMap, isExploration);
        return Ortb2ImpExtResult.of(explorationResult, tid);
    }
}
