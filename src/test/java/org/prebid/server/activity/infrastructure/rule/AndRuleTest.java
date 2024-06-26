package org.prebid.server.activity.infrastructure.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
public class AndRuleTest extends VertxTest {

    @Mock(strictness = LENIENT)
    private Rule allowRule;

    @Mock(strictness = LENIENT)
    private Rule disallowRule;

    @Mock(strictness = LENIENT)
    private Rule abstainRule;

    @BeforeEach
    public void setUp() {
        given(allowRule.proceed(any())).willReturn(Rule.Result.ALLOW);
        given(disallowRule.proceed(any())).willReturn(Rule.Result.DISALLOW);
        given(abstainRule.proceed(any())).willReturn(Rule.Result.ABSTAIN);
    }

    @Test
    public void proceedShouldReturnDisallowOnFirstOccurrence() {
        // given
        final Rule rule = new AndRule(asList(allowRule, disallowRule, abstainRule));

        // when
        final Rule.Result result = rule.proceed(null);

        // then
        assertThat(result).isEqualTo(Rule.Result.DISALLOW);
        verifyNoInteractions(abstainRule);
    }

    @Test
    public void proceedShouldReturnAllowAndProceedAllRules() {
        // given
        final Rule rule = new AndRule(asList(allowRule, abstainRule, allowRule));

        // when
        final Rule.Result result = rule.proceed(null);

        // then
        assertThat(result).isEqualTo(Rule.Result.ALLOW);
        verify(allowRule, times(2)).proceed(any());
        verify(abstainRule).proceed(any());
    }

    @Test
    public void proceedShouldReturnAbstain() {
        // given
        final Rule rule = new AndRule(singletonList(abstainRule));

        // when
        final Rule.Result result = rule.proceed(null);

        // then
        assertThat(result).isEqualTo(Rule.Result.ABSTAIN);
        verify(abstainRule).proceed(any());
    }

    @Test
    public void asLogEntryShouldReturnExpectedObjectNode() {
        // given
        final AndRule rule = new AndRule(asList(
                TestRule.allowIfMatches(payload -> true),
                TestRule.disallowIfMatches(payload -> false)));

        // when
        final JsonNode result = rule.asLogEntry(mapper);

        // then
        assertThat(result.get("and"))
                .isInstanceOf(ArrayNode.class)
                .containsExactly(TextNode.valueOf("TestRule"), TextNode.valueOf("TestRule"));
    }
}
