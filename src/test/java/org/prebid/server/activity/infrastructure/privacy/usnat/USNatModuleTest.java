package org.prebid.server.activity.infrastructure.privacy.usnat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.activity.Activity;
import org.prebid.server.activity.infrastructure.privacy.usnat.inner.USNatDefault;

import static org.assertj.core.api.Assertions.assertThat;

public class USNatModuleTest extends VertxTest {

    @Rule
    public final MockitoRule rule = MockitoJUnit.rule();

    @Mock
    private USNatGppReader usNatGppReader;

    @Test
    public void asLogEntryShouldReturnExpectedResult() {
        // given
        final USNatModule usNatModule = new USNatModule(Activity.CALL_BIDDER, usNatGppReader);

        // when
        final JsonNode logEntry = usNatModule.asLogEntry(mapper);

        // then
        assertThat(logEntry).isEqualTo(USNatDefault.instance().asLogEntry(mapper));
    }
}
