package org.prebid.server.hooks.modules.rule.engine.core.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.hooks.modules.rule.engine.core.config.model.AccountConfig;
import org.prebid.server.hooks.modules.rule.engine.core.request.RequestRuleContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.NoOpRule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.PerStageRule;

import java.util.Objects;

public class AccountConfigParser {

    private final ObjectMapper mapper;
    private final StageConfigParser<BidRequest, RequestRuleContext> processedAuctionRequestStageParser;

    public AccountConfigParser(
            ObjectMapper mapper,
            StageConfigParser<BidRequest, RequestRuleContext> processedAuctionRequestStageParser) {

        this.mapper = Objects.requireNonNull(mapper);
        this.processedAuctionRequestStageParser = Objects.requireNonNull(processedAuctionRequestStageParser);
    }

    public PerStageRule parse(ObjectNode accountConfig) {
        final AccountConfig parsedConfig;
        try {
            parsedConfig = mapper.treeToValue(accountConfig, AccountConfig.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException(e.getMessage());
        }

        if (!parsedConfig.isEnabled()) {
            return PerStageRule.builder()
                    .timestamp(parsedConfig.getTimestamp())
                    .processedAuctionRequestRule(NoOpRule.create())
                    .build();
        }

        return PerStageRule.builder()
                .timestamp(parsedConfig.getTimestamp())
                .processedAuctionRequestRule(processedAuctionRequestStageParser.parse(parsedConfig))
                .build();
    }
}
