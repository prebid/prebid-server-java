package org.prebid.server.hooks.modules.rule.engine.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.iab.openrtb.request.BidRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.hooks.modules.rule.engine.core.request.RequestRuleContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.NoOpRule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.PerStageRule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.Rule;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
public class AccountConfigParserTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    private AccountConfigParser target;

    @Mock(strictness = LENIENT)
    private StageConfigParser<BidRequest, RequestRuleContext> processedAuctionRequestStageParser;

    @BeforeEach
    public void setUp() {
        target = new AccountConfigParser(mapper, processedAuctionRequestStageParser);
    }

    @Test
    public void parseShouldReturnNoOpConfigWhenEnabledIsFalse() {
        // when and then
        assertThat(target.parse(mapper.createObjectNode().set("enabled", BooleanNode.getFalse()))).isEqualTo(
                PerStageRule.builder()
                        .timestamp(Instant.EPOCH)
                        .processedAuctionRequestRule(NoOpRule.create())
                        .build());
    }

    @Test
    public void parseShouldParseRuleForEachSupportedStage() {
        // given
        final Rule<BidRequest, RequestRuleContext> rule = (Rule<BidRequest, RequestRuleContext>) mock(Rule.class);
        given(processedAuctionRequestStageParser.parse(any())).willReturn(rule);

        // when and then
        assertThat(target.parse(mapper.createObjectNode())).isEqualTo(
                PerStageRule.builder()
                        .timestamp(Instant.EPOCH)
                        .processedAuctionRequestRule(rule)
                        .build());
    }
}
