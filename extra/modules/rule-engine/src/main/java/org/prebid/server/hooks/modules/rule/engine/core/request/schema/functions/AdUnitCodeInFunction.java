package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.hooks.modules.rule.engine.core.request.util.AdUnitCodeUtils;
import org.prebid.server.hooks.modules.rule.engine.core.request.RequestContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.util.ValidationUtils;
import org.prebid.server.util.StreamUtil;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AdUnitCodeInFunction implements SchemaFunction<RequestContext> {

    public static final String NAME = "adUnitCodeIn";

    private static final String CODES_FIELD = "codes";

    @Override
    public String extract(SchemaFunctionArguments<RequestContext> arguments) {
        final RequestContext context = arguments.getOperand();
        final String impId = context.getImpId();
        final BidRequest bidRequest = context.getBidRequest();

        final Imp adUnit = ListUtils.emptyIfNull(bidRequest.getImp()).stream()
                .filter(imp -> StringUtils.equals(imp.getId(), impId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Critical error in rules engine. Imp id of absent imp supplied"));

        final Set<String> adUnitPotentialCodes = Stream.of(
                        AdUnitCodeUtils.extractGpid(adUnit),
                        AdUnitCodeUtils.extractTagId(adUnit),
                        AdUnitCodeUtils.extractPbAdSlot(adUnit),
                        AdUnitCodeUtils.extractStoredRequestId(adUnit))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());

        final boolean matches = StreamUtil.asStream(arguments.getConfig().get(CODES_FIELD).elements())
                .map(JsonNode::asText)
                .anyMatch(adUnitPotentialCodes::contains);

        return Boolean.toString(matches);
    }

    @Override
    public void validateConfig(ObjectNode config) {
        ValidationUtils.assertArrayOfStrings(config, CODES_FIELD);
    }
}
