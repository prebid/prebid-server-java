package org.prebid.server.bidder.adform;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.User;
import org.junit.Test;
import org.prebid.server.bidder.adform.model.AdformDigitrust;
import org.prebid.server.bidder.adform.model.AdformDigitrustPrivacy;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.ExtUserDigiTrust;

import java.io.IOException;

import static io.vertx.core.json.Json.mapper;
import static org.assertj.core.api.Assertions.assertThat;

public class AdformRequestUtilTest {

    @Test
    public void getExtUserShouldReturnNullIfUserIsNull() {
        // given and when
        final ExtUser extUser = AdformRequestUtil.getExtUser(null);

        // then
        assertThat(extUser).isNull();
    }

    @Test
    public void getExtUserShouldReturnNullIfUserExtIsNull() {
        // given and when
        final ExtUser extUser = AdformRequestUtil.getExtUser(User.builder().ext(null).build());

        // then
        assertThat(extUser).isNull();
    }

    @Test
    public void getExtUserShouldReturnNullIfExtUserIsInvalidJSON() throws IOException {
        // given and when
        final ExtUser extUser = AdformRequestUtil.getExtUser(User.builder().ext(
                (ObjectNode) mapper.readTree("{\"prebid\":1}")).build());

        // then
        assertThat(extUser).isNull();
    }

    @Test
    public void getExtUserShouldReturnExtUser() {
        // given and when
        final ExtUser extUser = AdformRequestUtil.getExtUser(User.builder()
                .ext(mapper.valueToTree(ExtUser.of(null, null, null, null, null))).build());

        // then
        assertThat(extUser).isEqualTo(ExtUser.of(null, null, null, null, null));
    }

    @Test
    public void getGdprAppliesShouldReturnEmptyValueWhenRegsIsNull() {
        // given and when
        final String gdpr = AdformRequestUtil.getGdprApplies(null);

        // then
        assertThat(gdpr).isEmpty();
    }

    @Test
    public void getGdprAppliesShouldReturnEmptyValueWhenRegsExtIsNull() {
        // given and when
        final String gdpr = AdformRequestUtil.getGdprApplies(Regs.of(null, null));

        // then
        assertThat(gdpr).isEmpty();
    }

    @Test
    public void getGdprAppliesShouldReturnEmptyValueWhenRegExtIsNotValidJson() throws IOException {
        // given and when
        final String gdpr = AdformRequestUtil.getGdprApplies(
                Regs.of(null, (ObjectNode) mapper.readTree("{\"gdpr\": \"gdpr\"}")));

        // then
        assertThat(gdpr).isEmpty();
    }

    @Test
    public void getGdprAppliesShouldReturnEmptyValueWhenRegsExtGdprIsNoEqualsToOneOrZero() {
        // given and when
        final String gdpr = AdformRequestUtil.getGdprApplies(
                Regs.of(null, mapper.valueToTree(ExtRegs.of(2))));

        // then
        assertThat(gdpr).isEmpty();
    }

    @Test
    public void getGdprAppliesShouldReturnOne() {
        // given and when
        final String gdpr = AdformRequestUtil.getGdprApplies(
                Regs.of(null, mapper.valueToTree(ExtRegs.of(1))));

        // then
        assertThat(gdpr).isEqualTo("1");
    }

    @Test
    public void getGdprAppliesShouldReturnZero() {
        // given and when
        final String gdpr = AdformRequestUtil.getGdprApplies(
                Regs.of(null, mapper.valueToTree(ExtRegs.of(0))));

        // then
        assertThat(gdpr).isEqualTo("0");
    }

    @Test
    public void getConsentShouldReturnEmptyValueWhenExtUserIsNull() {
        // given and when
        final String consent = AdformRequestUtil.getConsent(null);

        // then
        assertThat(consent).isEmpty();
    }

    @Test
    public void getConsentShouldReturnEmptyValueWhenConsentIsNull() {
        // given and when
        final String consent = AdformRequestUtil.getConsent(ExtUser.of(null, null, null, null, null));

        // then
        assertThat(consent).isEmpty();
    }

    @Test
    public void getConsentShouldReturnConsent() {
        // given and when
        final String consent = AdformRequestUtil.getConsent(ExtUser.of(null, "consent", null, null, null));

        // then
        assertThat(consent).isEqualTo("consent");
    }

    @Test
    public void getAdformDigiTrustShouldReturnNullIfUserExtIsNull() {
        // given and when
        final AdformDigitrust adformDigitrust = AdformRequestUtil.getAdformDigitrust(null);

        // then
        assertThat(adformDigitrust).isNull();
    }

    @Test
    public void getAdformDigiTrustShouldReturnNullIfUserExtDigitrustIsNull() {
        // given and when
        final AdformDigitrust adformDigitrust = AdformRequestUtil.getAdformDigitrust(
                ExtUser.of(null, null, null, null, null));

        // then
        assertThat(adformDigitrust).isNull();
    }

    @Test
    public void getAdformDigiTrustShouldReturnAdformDigitrustWithOptOutFalseIfPrefIsZero() {
        // given and when
        final AdformDigitrust adformDigitrust = AdformRequestUtil.getAdformDigitrust(ExtUser.of(
                null, null, ExtUserDigiTrust.of("id", 123, 0), null, null));

        // then
        assertThat(adformDigitrust).isEqualTo(AdformDigitrust.of("id", 1, 123, AdformDigitrustPrivacy.of(false)));
    }

    @Test
    public void getAdformDigiTrustShouldReturnAdformDigitrustWithOptOutTrueIfPrefIsNotZero() {
        // given and when
        final AdformDigitrust adformDigitrust = AdformRequestUtil.getAdformDigitrust(ExtUser.of(
                null, null, ExtUserDigiTrust.of("id", 123, 1), null, null));

        // then
        assertThat(adformDigitrust).isEqualTo(AdformDigitrust.of("id", 1, 123, AdformDigitrustPrivacy.of(true)));
    }
}
