package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Regs;
import org.prebid.server.hooks.modules.rule.engine.core.request.RequestContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.util.ValidationUtils;
import org.prebid.server.util.StreamUtil;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class GppSidIn implements SchemaFunction<RequestContext> {

    public static final String NAME = "gppSidIn";

    private static final String SIDS_FIELD = "sids";

    @Override
    public String extract(SchemaFunctionArguments<RequestContext> arguments) {
        final Set<Integer> sids = Optional.of(arguments.getOperand().getBidRequest())
                .map(BidRequest::getRegs)
                .map(Regs::getGppSid)
                .stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());

        final boolean matches = StreamUtil.asStream(arguments.getConfig().get(SIDS_FIELD).elements())
                .map(JsonNode::asInt)
                .anyMatch(sids::contains);

        return Boolean.toString(matches);
    }

    @Override
    public void validateConfig(ObjectNode config) {
        ValidationUtils.assertArrayOfIntegers(config, SIDS_FIELD);
    }
}
