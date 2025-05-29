package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions;

import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.hooks.modules.rule.engine.core.request.RequestContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;

import java.util.Optional;

public class AdUnitCodeFunction implements SchemaFunction<RequestContext> {

    public static final String NAME = "adUnitCode";

    @Override
    public String extract(SchemaFunctionArguments<RequestContext> arguments) {
        final RequestContext context = arguments.getOperand();
        final String impId = context.getImpId();
        final BidRequest bidRequest = context.getBidRequest();

        final Imp adUnit = ListUtils.emptyIfNull(bidRequest.getImp()).stream()
                .filter(imp -> StringUtils.equals(impId, impId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Critical error in rules engine. Imp id of absent imp supplied"));


        return extractGpid(adUnit)
                .or(() -> extractTagId(adUnit))
                .or(() -> extractPbAdSlot(adUnit))
                .or(() -> extractStoredRequestId(adUnit))
                .orElse(UNDEFINED_RESULT);
    }

    private static Optional<String> extractGpid(Imp imp) {
        return Optional.ofNullable(imp.getExt())
                .map(ext -> ext.get("gpid"))
                .filter(JsonNode::isTextual)
                .map(JsonNode::asText);
    }

    private static Optional<String> extractTagId(Imp imp) {
        return Optional.ofNullable(imp.getTagid())
                .filter(StringUtils::isNotBlank);
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
}
