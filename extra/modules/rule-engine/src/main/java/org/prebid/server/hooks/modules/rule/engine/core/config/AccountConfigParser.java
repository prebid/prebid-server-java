package org.prebid.server.hooks.modules.rule.engine.core.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.hooks.modules.rule.engine.core.config.model.AccountConfig;
import org.prebid.server.hooks.modules.rule.engine.core.request.context.RequestResultContext;
import org.prebid.server.hooks.modules.rule.engine.core.request.context.RequestSchemaContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.PerStageRule;

import java.util.Objects;

public class AccountConfigParser {

    private final ObjectMapper mapper;
    private final StageConfigParser<
            RequestSchemaContext, BidRequest, RequestResultContext> processedAuctionRequestStageParser;

    public AccountConfigParser(
            ObjectMapper mapper,
            StageConfigParser<
                    RequestSchemaContext, BidRequest, RequestResultContext> processedAuctionRequestStageParser) {

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

        return PerStageRule.builder()
                .version(parsedConfig.getTimestamp())
                .processedAuctionRequestRule(processedAuctionRequestStageParser.parse(parsedConfig))
                .build();
    }
}
