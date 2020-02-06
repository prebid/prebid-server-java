package org.prebid.server.privacy;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.User;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.privacy.ccpa.Ccpa;
import org.prebid.server.privacy.model.Privacy;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class PrivacyExtractorTest extends VertxTest {

    private PrivacyExtractor privacyExtractor;

    @Before
    public void setUp() {
        privacyExtractor = new PrivacyExtractor(jacksonMapper);
    }

    @Test
    public void shouldReturnGdprEmptyValueWhenRegsIsNull() {
        // given and when
        final String gdpr = privacyExtractor.validPrivacyFrom(null, null).getGdpr();

        // then
        assertThat(gdpr).isEmpty();
    }

    @Test
    public void shouldReturnGdprEmptyValueWhenRegsExtIsNull() {
        // given and when
        final String gdpr = privacyExtractor.validPrivacyFrom(Regs.of(null, null), null).getGdpr();

        // then
        assertThat(gdpr).isEmpty();
    }

    @Test
    public void shouldReturnGdprEmptyValueWhenRegExtIsNotValidJson() throws IOException {
        // given and when
        final Regs regs = Regs.of(null, (ObjectNode) mapper.readTree("{\"gdpr\": \"gdpr\"}"));
        final String gdpr = privacyExtractor.validPrivacyFrom(regs, null).getGdpr();

        // then
        assertThat(gdpr).isEmpty();
    }

    @Test
    public void shouldReturnGdprEmptyValueWhenRegsExtGdprIsNoEqualsToOneOrZero() {
        // given and when
        final Regs regs = Regs.of(null, mapper.valueToTree(ExtRegs.of(2, null)));
        final String gdpr = privacyExtractor.validPrivacyFrom(regs, null).getGdpr();

        // then
        assertThat(gdpr).isEmpty();
    }

    @Test
    public void shouldReturnGdprOneWhenExtRegsContainsGdprOne() {
        // given and when
        final Regs regs = Regs.of(null, mapper.valueToTree(ExtRegs.of(1, null)));
        final String gdpr = privacyExtractor.validPrivacyFrom(regs, null).getGdpr();

        // then
        assertThat(gdpr).isEqualTo("1");
    }

    @Test
    public void shouldReturnGdprZeroWhenExtRegsContainsGdprZero() {
        // given and when
        final Regs regs = Regs.of(null, mapper.valueToTree(ExtRegs.of(0, null)));
        final String gdpr = privacyExtractor.validPrivacyFrom(regs, null).getGdpr();

        // then
        assertThat(gdpr).isEqualTo("0");
    }

    @Test
    public void shouldReturnConsentEmptyValueWhenExtUserIsNull() {
        // given and when
        final String consent = privacyExtractor.validPrivacyFrom(null, null).getConsent();

        // then
        assertThat(consent).isEmpty();
    }

    @Test
    public void shouldReturnConsentEmptyValueWhenUserConsentIsNull() {
        // given and when
        final User user = User.builder()
                .ext(mapper.valueToTree(ExtUser.builder().build()))
                .build();
        final String consent = privacyExtractor.validPrivacyFrom(null, user).getConsent();

        // then
        assertThat(consent).isEmpty();
    }

    @Test
    public void shouldReturnConsentWhenUserContainsConsent() {
        // given and when
        final User user = User.builder()
                .ext(mapper.valueToTree(ExtUser.builder().consent("consent").build()))
                .build();
        final String consent = privacyExtractor.validPrivacyFrom(null, user).getConsent();

        // then
        assertThat(consent).isEqualTo("consent");
    }

    @Test
    public void shouldReturnPrivacyWithExtractedParameters() {
        // given and when
        final User user = User.builder()
                .ext(mapper.valueToTree(ExtUser.builder().consent("consent").build()))
                .build();
        final Regs regs = Regs.of(null, mapper.valueToTree(ExtRegs.of(0, "YAN")));
        final Privacy privacy = privacyExtractor.validPrivacyFrom(regs, user);

        // then
        assertThat(privacy).isEqualTo(Privacy.of("0", "consent", Ccpa.of("YAN")));
    }
}

