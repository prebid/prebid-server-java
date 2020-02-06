package org.prebid.server.bidder.adform;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.User;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.adform.model.AdformDigitrust;
import org.prebid.server.bidder.adform.model.AdformDigitrustPrivacy;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.ExtUserDigiTrust;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class AdformRequestUtilTest extends VertxTest {

    private AdformRequestUtil requestUtil;

    @Before
    public void setUp() {
        requestUtil = new AdformRequestUtil(jacksonMapper);
    }

    @Test
    public void getExtUserShouldReturnNullIfUserIsNull() {
        // given and when
        final ExtUser extUser = requestUtil.getExtUser(null);

        // then
        assertThat(extUser).isNull();
    }

    @Test
    public void getExtUserShouldReturnNullIfUserExtIsNull() {
        // given and when
        final ExtUser extUser = requestUtil.getExtUser(User.builder().ext(null).build());

        // then
        assertThat(extUser).isNull();
    }

    @Test
    public void getExtUserShouldReturnNullIfExtUserIsInvalidJSON() throws IOException {
        // given and when
        final ExtUser extUser = requestUtil.getExtUser(
                User.builder().ext((ObjectNode) mapper.readTree("{\"prebid\":1}")).build());

        // then
        assertThat(extUser).isNull();
    }

    @Test
    public void getExtUserShouldReturnExtUser() {
        // given and when
        final ExtUser extUser = requestUtil.getExtUser(
                User.builder().ext(mapper.valueToTree(ExtUser.builder().build())).build());

        // then
        assertThat(extUser).isEqualTo(ExtUser.builder().build());
    }

    @Test
    public void getGdprAppliesShouldReturnEmptyValueWhenRegsIsNull() {
        // given and when
        final String gdpr = requestUtil.getGdprApplies(null);

        // then
        assertThat(gdpr).isEmpty();
    }

    @Test
    public void getGdprAppliesShouldReturnEmptyValueWhenRegsExtIsNull() {
        // given and when
        final String gdpr = requestUtil.getGdprApplies(Regs.of(null, null));

        // then
        assertThat(gdpr).isEmpty();
    }

    @Test
    public void getGdprAppliesShouldReturnEmptyValueWhenRegExtIsNotValidJson() throws IOException {
        // given and when
        final String gdpr = requestUtil.getGdprApplies(
                Regs.of(null, (ObjectNode) mapper.readTree("{\"gdpr\": \"gdpr\"}")));

        // then
        assertThat(gdpr).isEmpty();
    }

    @Test
    public void getGdprAppliesShouldReturnEmptyValueWhenRegsExtGdprIsNoEqualsToOneOrZero() {
        // given and when
        final String gdpr = requestUtil.getGdprApplies(
                Regs.of(null, mapper.valueToTree(ExtRegs.of(2, null))));

        // then
        assertThat(gdpr).isEmpty();
    }

    @Test
    public void getGdprAppliesShouldReturnOne() {
        // given and when
        final String gdpr = requestUtil.getGdprApplies(
                Regs.of(null, mapper.valueToTree(ExtRegs.of(1, null))));

        // then
        assertThat(gdpr).isEqualTo("1");
    }

    @Test
    public void getGdprAppliesShouldReturnZero() {
        // given and when
        final String gdpr = requestUtil.getGdprApplies(
                Regs.of(null, mapper.valueToTree(ExtRegs.of(0, null))));

        // then
        assertThat(gdpr).isEqualTo("0");
    }

    @Test
    public void getConsentShouldReturnEmptyValueWhenExtUserIsNull() {
        // given and when
        final String consent = requestUtil.getConsent(null);

        // then
        assertThat(consent).isEmpty();
    }

    @Test
    public void getConsentShouldReturnEmptyValueWhenConsentIsNull() {
        // given and when
        final String consent = requestUtil.getConsent(ExtUser.builder().build());

        // then
        assertThat(consent).isEmpty();
    }

    @Test
    public void getConsentShouldReturnConsent() {
        // given and when
        final String consent = requestUtil.getConsent(ExtUser.builder().consent("consent").build());

        // then
        assertThat(consent).isEqualTo("consent");
    }

    @Test
    public void getAdformDigiTrustShouldReturnNullIfUserExtIsNull() {
        // given and when
        final AdformDigitrust adformDigitrust = requestUtil.getAdformDigitrust(null);

        // then
        assertThat(adformDigitrust).isNull();
    }

    @Test
    public void getAdformDigiTrustShouldReturnNullIfUserExtDigitrustIsNull() {
        // given and when
        final AdformDigitrust adformDigitrust = requestUtil.getAdformDigitrust(ExtUser.builder().build());

        // then
        assertThat(adformDigitrust).isNull();
    }

    @Test
    public void getAdformDigiTrustShouldReturnAdformDigitrustWithOptOutFalseIfPrefIsZero() {
        // given and when
        final AdformDigitrust adformDigitrust = requestUtil.getAdformDigitrust(ExtUser.builder()
                .digitrust(ExtUserDigiTrust.of("id", 123, 0))
                .build());

        // then
        assertThat(adformDigitrust).isEqualTo(AdformDigitrust.of("id", 1, 123, AdformDigitrustPrivacy.of(false)));
    }

    @Test
    public void getAdformDigiTrustShouldReturnAdformDigitrustWithOptOutTrueIfPrefIsNotZero() {
        // given and when
        final AdformDigitrust adformDigitrust = requestUtil.getAdformDigitrust(ExtUser.builder()
                .digitrust(ExtUserDigiTrust.of("id", 123, 1))
                .build());

        // then
        assertThat(adformDigitrust).isEqualTo(AdformDigitrust.of("id", 1, 123, AdformDigitrustPrivacy.of(true)));
    }
}
