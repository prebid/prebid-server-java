package org.prebid.server.activity.infrastructure.privacy.usnat.debug;

import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.gpp.encoder.GppModel;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.activity.infrastructure.privacy.PrivacyModule;
import org.prebid.server.activity.infrastructure.privacy.usnat.USNatGppReader;
import org.prebid.server.activity.infrastructure.privacy.usnat.inner.USNatDefault;
import org.prebid.server.activity.infrastructure.privacy.usnat.inner.USNatSyncUser;
import org.prebid.server.activity.infrastructure.privacy.usnat.reader.USNationalGppReader;
import org.prebid.server.activity.infrastructure.rule.Rule;

import static org.assertj.core.api.Assertions.assertThat;


public class USNatModuleLogEntryTest {

    @org.junit.Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private GppModel gppModel;

    @Test
    public void fromShouldReturnExpectedTextNode() {
        // given
        final USNatGppReader gppReader = new USNationalGppReader(gppModel);
        final PrivacyModule privacyModule = new USNatSyncUser(gppReader);

        // when
        final TextNode result = USNatModuleLogEntry.from(privacyModule, gppReader, Rule.Result.ALLOW);

        // then
        assertThat(result)
                .isEqualTo(TextNode.valueOf("USNatSyncUser with USNationalGppReader. Precomputed result: ALLOW."));
    }

    @Test
    public void fromShouldReturnExpectedTextNodeForUSNatDefault() {
        // given
        final PrivacyModule privacyModule = USNatDefault.instance();

        // when
        final TextNode result = USNatModuleLogEntry.from(privacyModule, Rule.Result.ABSTAIN);

        // then
        assertThat(result)
                .isEqualTo(TextNode.valueOf("USNatDefault. Precomputed result: ABSTAIN."));
    }
}
