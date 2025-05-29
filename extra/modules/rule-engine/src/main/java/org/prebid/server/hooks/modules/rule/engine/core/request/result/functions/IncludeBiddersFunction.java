package org.prebid.server.hooks.modules.rule.engine.core.request.result.functions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iab.openrtb.request.BidRequest;
import lombok.Value;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.RuleResult;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.ResultFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.ResultFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.util.ConfigurationValidationException;
import org.springframework.util.CollectionUtils;

@Value(staticConstructor = "of")
public class IncludeBiddersFunction implements ResultFunction<BidRequest, AuctionContext> {

    public static final String NAME = "includeBidders";

    ObjectMapper mapper;

    @Override
    public RuleResult<BidRequest> apply(ResultFunctionArguments<BidRequest, AuctionContext> arguments) {
        return RuleResult.unaltered(arguments.getOperand());
    }

    @Override
    public void validateConfig(JsonNode config) {
        final ExcludeBiddersFunctionConfig parsedConfig = parseConfig(config);
        if (parsedConfig == null) {
            throw new ConfigurationValidationException("Configuration is required for excludeBidders function");
        }

        if (CollectionUtils.isEmpty(parsedConfig.getBidders())) {
            throw new ConfigurationValidationException("bidders field is required for excludeBidders function");
        }
    }

    private ExcludeBiddersFunctionConfig parseConfig(JsonNode config) {
        try {
            return mapper.treeToValue(config, ExcludeBiddersFunctionConfig.class);
        } catch (JsonProcessingException e) {
            throw new ConfigurationValidationException(e.getMessage());
        }
    }
}

