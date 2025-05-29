package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions;

import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.hooks.modules.rule.engine.core.request.RequestContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.util.ValidationUtils;

import java.util.List;
import java.util.Optional;

public class AdUnitCodeFunction implements SchemaFunction<RequestContext> {

    public static final String NAME = "adUnitCode";

    @Override
    public String extract(SchemaFunctionArguments<RequestContext> arguments) {
        final RequestContext context = arguments.getOperand();
        final String impId = context.getImpId();
        final BidRequest bidRequest = context.getBidRequest();

        final Optional<Imp> adUnit = ListUtils.emptyIfNull(bidRequest.getImp()).stream()
                .filter(imp -> StringUtils.equals(impId, impId))
                .findFirst();

        if (adUnit.isEmpty()) {
            return UNDEFINED_RESULT;
        }

        return adUnit.flatMap(AdUnitCodeFunction::extractGpid)
                .or(() -> adUnit.map(AdUnitCodeFunction::extractTagId))
                .or(() -> adUnit.flatMap(AdUnitCodeFunction::extractPbAdSlot))
                .or(() -> adUnit.flatMap(AdUnitCodeFunction::extractStoredRequestId))
                .orElse(UNDEFINED_RESULT);
    }

    private static Optional<String> extractGpid(Imp imp) {
        return Optional.ofNullable(imp.getExt())
                .map(ext -> ext.get("gpid"))
                .filter(JsonNode::isTextual)
                .map(JsonNode::asText);
    }

    private static String extractTagId(Imp imp) {
        return StringUtils.trimToNull(imp.getTagid());
    }

    private static Optional<String> extractPbAdSlot(Imp imp) {
        return Optional.ofNullable(imp.getExt())
                .map(ext -> ext.get("data"))
                .map(data -> data.get("pbadslot"))
                .filter(JsonNode::isTextual)
                .map(JsonNode::asText);
    }

    private static Optional<String> extractStoredRequestId(Imp imp) {
        return Optional.ofNullable(imp.getExt())
                .map(ext -> ext.get("prebid"))
                .map(prebid -> prebid.get("storedrequest"))
                .map(storedRequest -> storedRequest.get("id"))
                .filter(JsonNode::isTextual)
                .map(JsonNode::asText);
    }

    @Override
    public void validateConfigArguments(List<JsonNode> configArguments) {
        ValidationUtils.assertNoArgs(configArguments);
    }
}
