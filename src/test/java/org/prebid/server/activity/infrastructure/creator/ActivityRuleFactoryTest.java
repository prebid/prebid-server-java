package org.prebid.server.activity.infrastructure.creator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.activity.infrastructure.creator.rule.RuleCreator;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

public class ActivityRuleFactoryTest {

    @org.junit.Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private RuleCreator<Integer> ruleCreator1;

    @Mock
    private RuleCreator<String> ruleCreator2;

    private ActivityRuleFactory target;

    @BeforeEach
    public void setUp() {
        given(ruleCreator1.relatedConfigurationClass()).willReturn(Integer.class);
        given(ruleCreator2.relatedConfigurationClass()).willReturn(String.class);

        target = new ActivityRuleFactory(List.of(ruleCreator1, ruleCreator2));
    }

    @Test
    public void fromShouldThrowExceptionIfRuleCreatorNotFoundForConfiguration() {
        // when and then
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> target.from(new Object(), null));
    }

    @Test
    public void fromShouldReturnExpectedResult() {
        // given
        final Integer config = 1;

        // when
        target.from(config, null);

        // then
        verify(ruleCreator1).from(same(config), any());
    }
}
