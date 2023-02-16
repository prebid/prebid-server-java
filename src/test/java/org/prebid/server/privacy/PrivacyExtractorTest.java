package org.prebid.server.privacy;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.User;
import io.vertx.core.http.HttpServerRequest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.privacy.ccpa.Ccpa;
import org.prebid.server.privacy.model.Privacy;
import org.prebid.server.proto.request.CookieSyncRequest;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

public class PrivacyExtractorTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private PrivacyExtractor privacyExtractor;

    @Before
    public void setUp() {
        privacyExtractor = new PrivacyExtractor();
    }

    @Test
    public void shouldReturnGdprEmptyValueWhenRegsIsNull() {
        // given and when
        final String gdpr =
                privacyExtractor.validPrivacyFrom(BidRequest.builder().build(), new ArrayList<>()).getGdpr();

        // then
        assertThat(gdpr).isEmpty();
    }

    @Test
    public void shouldReturnGdprEmptyValueWhenRegsExtIsNull() {
        // given and when
        final String gdpr = privacyExtractor.validPrivacyFrom(
                        BidRequest.builder().regs(Regs.builder().build()).build(), new ArrayList<>())
                .getGdpr();

        // then
        assertThat(gdpr).isEmpty();
    }

    @Test
    public void shouldReturnGdprEmptyValueWhenRegsExtGdprIsNoEqualsToOneOrZero() {
        // given
        final Regs regs = Regs.builder().gdpr(2).build();

        // when
        final String gdpr =
                privacyExtractor.validPrivacyFrom(BidRequest.builder().regs(regs).build(), new ArrayList<>()).getGdpr();

        // then
        assertThat(gdpr).isEmpty();
    }

    @Test
    public void shouldReturnGdprOneWhenExtRegsContainsGdprOne() {
        // given
        final Regs regs = Regs.builder().gdpr(1).build();

        // when
        final String gdpr =
                privacyExtractor.validPrivacyFrom(BidRequest.builder().regs(regs).build(), new ArrayList<>()).getGdpr();

        // then
        assertThat(gdpr).isEqualTo("1");
    }

    @Test
    public void shouldReturnGdprZeroWhenExtRegsContainsGdprZero() {
        // given
        final Regs regs = Regs.builder().gdpr(0).build();

        // when
        final String gdpr =
                privacyExtractor.validPrivacyFrom(BidRequest.builder().regs(regs).build(), new ArrayList<>()).getGdpr();

        // then
        assertThat(gdpr).isEqualTo("0");
    }

    @Test
    public void shouldReturnConsentEmptyValueWhenExtUserIsNull() {
        // given and when
        final String consent = privacyExtractor.validPrivacyFrom(BidRequest.builder().build(), new ArrayList<>())
                .getConsentString();

        // then
        assertThat(consent).isEmpty();
    }

    @Test
    public void shouldReturnConsentEmptyValueWhenUserConsentIsNull() {
        // given
        final User user = User.builder().build();

        // when
        final String consent =
                privacyExtractor.validPrivacyFrom(BidRequest.builder().user(user).build(), new ArrayList<>())
                        .getConsentString();

        // then
        assertThat(consent).isEmpty();
    }

    @Test
    public void shouldReturnConsentWhenUserContainsConsent() {
        // given
        final User user = User.builder().consent("consent").build();

        // when
        final String consent =
                privacyExtractor.validPrivacyFrom(BidRequest.builder().user(user).build(), new ArrayList<>())
                        .getConsentString();

        // then
        assertThat(consent).isEqualTo("consent");
    }

    @Test
    public void shouldReturnDefaultCcpaWhenNotValidAndAddError() {
        // given
        final Regs regs = Regs.builder().usPrivacy("invalid").build();
        final ArrayList<String> errors = new ArrayList<>();

        // when
        final Ccpa ccpa =
                privacyExtractor.validPrivacyFrom(BidRequest.builder().regs(regs).build(), errors).getCcpa();

        // then
        assertThat(ccpa).isEqualTo(Ccpa.EMPTY);
        assertThat(errors).containsOnly(
                "CCPA consent invalid has invalid format: us_privacy must contain 4 characters");
    }

    @Test
    public void shouldReturnDefaultCoppaIfNull() {
        // given
        final Regs regs = Regs.builder().build();

        // when
        final Integer coppa =
                privacyExtractor.validPrivacyFrom(BidRequest.builder().regs(regs).build(), new ArrayList<>())
                        .getCoppa();

        // then
        assertThat(coppa).isZero();
    }

    @Test
    public void shouldReturnCoppaIfNotNull() {
        // given
        final Regs regs = Regs.builder().coppa(42).build();

        // when
        final Integer coppa =
                privacyExtractor.validPrivacyFrom(BidRequest.builder().regs(regs).build(), new ArrayList<>())
                        .getCoppa();

        // then
        assertThat(coppa).isEqualTo(42);
    }

    @Test
    public void shouldReturnPrivacyWithParametersExtractedFromBidRequest() {
        // given
        final Regs regs = Regs.builder().gdpr(0).usPrivacy("1Yn-").build();
        final User user = User.builder().consent("consent").build();

        // when
        final Privacy privacy = privacyExtractor
                .validPrivacyFrom(BidRequest.builder().regs(regs).user(user).build(), new ArrayList<>());

        // then
        assertThat(privacy).isEqualTo(
                Privacy.builder()
                        .gdpr("0")
                        .consentString("consent")
                        .ccpa(Ccpa.of("1Yn-"))
                        .coppa(0)
                        .gpp("")
                        .gppSid(emptyList())
                        .build());
    }

    @Test
    public void shouldReturnPrivacyWithParametersExtractedFromSetuidRequest() {
        // given
        final HttpServerRequest request = mock(HttpServerRequest.class);
        given(request.getParam(eq("gdpr"))).willReturn("0");
        given(request.getParam(eq("gdpr_consent"))).willReturn("consent");

        // when
        final Privacy privacy = privacyExtractor.validPrivacyFromSetuidRequest(request);

        // then
        assertThat(privacy).isEqualTo(
                Privacy.builder()
                        .gdpr("0")
                        .consentString("consent")
                        .ccpa(Ccpa.EMPTY)
                        .coppa(0)
                        .gpp("")
                        .gppSid(emptyList())
                        .build());
    }

    @Test
    public void shouldReturnPrivacyWithParametersExtractedFromCookieSyncRequest() {
        // given
        final CookieSyncRequest request = CookieSyncRequest.builder()
                .gdpr(0)
                .gdprConsent("consent")
                .usPrivacy("1Yn-")
                .gpp("gpp")
                .gppSid("1, 2, 3")
                .build();

        // when
        final Privacy privacy = privacyExtractor.validPrivacyFrom(request);

        // then
        assertThat(privacy).isEqualTo(
                Privacy.builder()
                        .gdpr("0")
                        .consentString("consent")
                        .ccpa(Ccpa.of("1Yn-"))
                        .coppa(0)
                        .gpp("gpp")
                        .gppSid(List.of(1, 2, 3))
                        .build());
    }
}
