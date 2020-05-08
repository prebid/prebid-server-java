package org.prebid.server.metric;

import org.junit.Before;
import org.junit.Test;
import org.prebid.server.metric.model.AccountMetricsVerbosityLevel;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

public class AccountMetricsVerbosityTest {

    private AccountMetricsVerbosity verbosity;

    @Before
    public void setUp() {
        verbosity = new AccountMetricsVerbosity(AccountMetricsVerbosityLevel.none, singletonList("1"),
                singletonList("2"));
    }

    @Test
    public void forAccountShouldReturnBasicLevel() {
        assertThat(verbosity.forAccount("1")).isEqualTo(AccountMetricsVerbosityLevel.basic);
    }

    @Test
    public void forAccountShouldReturnDetailedLevel() {
        assertThat(verbosity.forAccount("2")).isEqualTo(AccountMetricsVerbosityLevel.detailed);
    }

    @Test
    public void forAccountShouldReturnDefaultLevel() {
        assertThat(verbosity.forAccount("3")).isEqualTo(AccountMetricsVerbosityLevel.none);
    }
}
