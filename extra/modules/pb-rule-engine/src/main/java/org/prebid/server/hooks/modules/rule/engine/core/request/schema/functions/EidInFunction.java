package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.User;
import org.prebid.server.hooks.modules.rule.engine.core.request.RequestRuleContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.util.ValidationUtils;
import org.prebid.server.util.StreamUtil;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class EidInFunction implements SchemaFunction<BidRequest, RequestRuleContext> {

    public static final String NAME = "eidIn";

    private static final String SOURCES_FIELD = "sources";

    @Override
    public String extract(SchemaFunctionArguments<BidRequest, RequestRuleContext> arguments) {
        final Set<String> sources = Optional.of(arguments.getOperand())
                .map(BidRequest::getUser)
                .map(User::getEids)
                .stream()
                .flatMap(Collection::stream)
                .map(Eid::getSource)
                .collect(Collectors.toSet());

        final boolean matches = StreamUtil.asStream(arguments.getConfig().get(SOURCES_FIELD).elements())
                .map(JsonNode::asText)
                .anyMatch(sources::contains);

        return Boolean.toString(matches);
    }

    @Override
    public void validateConfig(ObjectNode config) {
        ValidationUtils.assertArrayOfStrings(config, SOURCES_FIELD);
    }
}
