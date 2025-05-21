package org.prebid.server.hooks.modules.rule.engine.core.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.hooks.modules.rule.engine.core.config.model.AccountConfig;
import org.prebid.server.hooks.modules.rule.engine.core.rules.PerStageRule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.request.RequestContext;

import java.util.Objects;

public class AccountConfigParser {

    private final ObjectMapper mapper;
    private final StageConfigParser<RequestContext, BidRequest> processedAuctionRequestStageParser;

    public AccountConfigParser(ObjectMapper mapper,
                               StageConfigParser<RequestContext, BidRequest> processedAuctionRequestStageParser) {

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
                .processedAuctionRequestRule(processedAuctionRequestStageParser.parse(parsedConfig))
                .build();
    }
}
