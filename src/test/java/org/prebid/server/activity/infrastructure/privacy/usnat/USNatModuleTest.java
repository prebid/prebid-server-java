package org.prebid.server.activity.infrastructure.privacy.usnat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.activity.Activity;
import org.prebid.server.activity.infrastructure.privacy.usnat.inner.USNatDefault;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class USNatModuleTest extends VertxTest {

    @Mock
    private USNatGppReader usNatGppReader;

    @Test
    public void asLogEntryShouldReturnExpectedResult() {
        // given
        final USNatModule usNatModule = new USNatModule(Activity.CALL_BIDDER, usNatGppReader, null);

        // when
        final JsonNode logEntry = usNatModule.asLogEntry(mapper);

        // then
        assertThat(logEntry).isEqualTo(USNatDefault.instance().asLogEntry(mapper));
    }
}
