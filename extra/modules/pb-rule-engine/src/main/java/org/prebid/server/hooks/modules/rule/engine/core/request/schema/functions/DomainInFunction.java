package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import org.prebid.server.hooks.modules.rule.engine.core.request.context.RequestSchemaContext;
import org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions.util.DomainUtils;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.util.ValidationUtils;
import org.prebid.server.util.StreamUtil;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DomainInFunction implements SchemaFunction<RequestSchemaContext> {

    public static final String NAME = "domainIn";

    private static final String DOMAINS_FIELD = "domains";

    @Override
    public String extract(SchemaFunctionArguments<RequestSchemaContext> arguments) {
        final BidRequest bidRequest = arguments.getOperand().getBidRequest();

        final Set<String> suppliedDomains = Stream.of(
                        DomainUtils.extractSitePublisherDomain(bidRequest),
                        DomainUtils.extractAppPublisherDomain(bidRequest),
                        DomainUtils.extractDoohPublisherDomain(bidRequest),
                        DomainUtils.extractSiteDomain(bidRequest),
                        DomainUtils.extractAppDomain(bidRequest),
                        DomainUtils.extractDoohDomain(bidRequest))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());

        final boolean matches = StreamUtil.asStream(arguments.getConfig().get(DOMAINS_FIELD).elements())
                .map(JsonNode::asText)
                .anyMatch(suppliedDomains::contains);

        return Boolean.toString(matches);
    }

    @Override
    public void validateConfig(ObjectNode config) {
        ValidationUtils.assertArrayOfStrings(config, DOMAINS_FIELD);
    }
}
