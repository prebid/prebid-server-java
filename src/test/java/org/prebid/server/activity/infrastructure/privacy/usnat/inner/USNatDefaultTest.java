package org.prebid.server.activity.infrastructure.privacy.usnat.inner;

import org.junit.Test;
import org.prebid.server.activity.infrastructure.rule.Rule;

import static org.assertj.core.api.Assertions.assertThat;

public class USNatDefaultTest {

    @Test
    public void proceedShouldAlwaysReturnAbstain() {
        // when
        final Rule.Result result = new USNatDefault().proceed(null);

        // then
        assertThat(result).isEqualTo(Rule.Result.ABSTAIN);
    }
}
