package org.prebid.server.auction.gpp;

import com.iab.gpp.encoder.GppModel;
import com.iab.gpp.encoder.error.EncodingException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.auction.gpp.model.GppContextCreator;
import org.prebid.server.auction.gpp.model.privacy.TcfEuV2Privacy;
import org.prebid.server.auction.gpp.model.privacy.UspV1Privacy;
import org.prebid.server.cookie.model.CookieSyncContext;
import org.prebid.server.proto.request.CookieSyncRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;

public class CookieSyncGppServiceTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private GppService gppService;

    private CookieSyncGppService cookieSyncGppService;

    @Before
    public void setUp() {
        cookieSyncGppService = new CookieSyncGppService(gppService);
    }

    @Test
    public void applyShouldReturnSameCookieSyncRequest() {
        // given
        given(gppService.processContext(
                argThat(gppContextMatcher(
                        Set.of(2, 6),
                        TcfEuV2Privacy.of(1, "consent"),
                        UspV1Privacy.of("usPrivacy"),
                        Collections.emptyList()))))
                .willReturn(givenGppContext(
                        List.of(2, 6),
                        TcfEuV2Privacy.of(2, "gppConsent"),
                        UspV1Privacy.of("gppUsPrivacy"),
                        Collections.emptyList()));

        final CookieSyncRequest cookieSyncRequest = givenCookieSyncRequest(
                givenValidGppString(), List.of(2, 6), 1, "consent", "usPrivacy");
        final CookieSyncContext cookieSyncContext = givenCookieSyncContext(true);

        // when
        final CookieSyncRequest result = cookieSyncGppService.apply(cookieSyncRequest, cookieSyncContext);

        // then
        assertThat(result).isSameAs(cookieSyncRequest);
        assertThat(cookieSyncContext.getWarnings()).isEmpty();
    }

    @Test
    public void applyShouldUpdateCookieSyncRequestGdpr() {
        // given
        given(gppService.processContext(any()))
                .willReturn(givenGppContext(
                        List.of(2, 6),
                        TcfEuV2Privacy.of(2, "gppConsent"),
                        UspV1Privacy.of("gppUsPrivacy"),
                        Collections.emptyList()));

        final CookieSyncRequest cookieSyncRequest = givenCookieSyncRequest(
                givenValidGppString(), List.of(2, 6), null, "consent", "usPrivacy");
        final CookieSyncContext cookieSyncContext = givenCookieSyncContext(true);

        // when
        final CookieSyncRequest result = cookieSyncGppService.apply(cookieSyncRequest, cookieSyncContext);

        // then
        assertThat(result.getGdpr()).isEqualTo(2);
        assertThat(result.getGdprConsent()).isSameAs(cookieSyncRequest.getGdprConsent());
        assertThat(result.getUsPrivacy()).isSameAs(cookieSyncRequest.getUsPrivacy());
        assertThat(cookieSyncContext.getWarnings()).isEmpty();
    }

    @Test
    public void applyShouldUpdateCookieSyncRequestConsent() {
        // given
        given(gppService.processContext(any()))
                .willReturn(givenGppContext(
                        List.of(2, 6),
                        TcfEuV2Privacy.of(2, "gppConsent"),
                        UspV1Privacy.of("gppUsPrivacy"),
                        Collections.emptyList()));

        final CookieSyncRequest cookieSyncRequest = givenCookieSyncRequest(
                givenValidGppString(), List.of(2, 6), 1, null, "usPrivacy");
        final CookieSyncContext cookieSyncContext = givenCookieSyncContext(true);

        // when
        final CookieSyncRequest result = cookieSyncGppService.apply(cookieSyncRequest, cookieSyncContext);

        // then
        assertThat(result.getGdpr()).isSameAs(cookieSyncRequest.getGdpr());
        assertThat(result.getGdprConsent()).isEqualTo("gppConsent");
        assertThat(result.getUsPrivacy()).isSameAs(cookieSyncRequest.getUsPrivacy());
        assertThat(cookieSyncContext.getWarnings()).isEmpty();
    }

    @Test
    public void applyShouldUpdateCookieSyncRequestUsPrivacy() {
        // given
        given(gppService.processContext(any()))
                .willReturn(givenGppContext(
                        List.of(2, 6),
                        TcfEuV2Privacy.of(2, "gppConsent"),
                        UspV1Privacy.of("gppUsPrivacy"),
                        Collections.emptyList()));

        final CookieSyncRequest cookieSyncRequest = givenCookieSyncRequest(
                givenValidGppString(), List.of(2, 6), 1, "consent", null);
        final CookieSyncContext cookieSyncContext = givenCookieSyncContext(true);

        // when
        final CookieSyncRequest result = cookieSyncGppService.apply(cookieSyncRequest, cookieSyncContext);

        // then
        assertThat(result.getGdpr()).isSameAs(cookieSyncRequest.getGdpr());
        assertThat(result.getGdprConsent()).isSameAs(cookieSyncRequest.getGdprConsent());
        assertThat(result.getUsPrivacy()).isEqualTo("gppUsPrivacy");
        assertThat(cookieSyncContext.getWarnings()).isEmpty();
    }

    @Test
    public void applyShouldUpdateCookieSyncRequest() {
        // given
        given(gppService.processContext(any()))
                .willReturn(givenGppContext(
                        List.of(2, 6),
                        TcfEuV2Privacy.of(2, "gppConsent"),
                        UspV1Privacy.of("gppUsPrivacy"),
                        Collections.emptyList()));

        final CookieSyncRequest cookieSyncRequest = givenCookieSyncRequest(
                givenValidGppString(), List.of(2, 6), null, null, null);
        final CookieSyncContext cookieSyncContext = givenCookieSyncContext(true);

        // when
        final CookieSyncRequest result = cookieSyncGppService.apply(cookieSyncRequest, cookieSyncContext);

        // then
        assertThat(result.getGdpr()).isEqualTo(2);
        assertThat(result.getGdprConsent()).isEqualTo("gppConsent");
        assertThat(result.getUsPrivacy()).isEqualTo("gppUsPrivacy");
        assertThat(cookieSyncContext.getWarnings()).isEmpty();
    }

    @Test
    public void applyShouldAddWarningsInCookieSyncContextIfDebugEnabled() {
        // given
        given(gppService.processContext(
                argThat(gppContextMatcher(
                        Set.of(2, 6),
                        TcfEuV2Privacy.of(1, "consent"),
                        UspV1Privacy.of("usPrivacy"),
                        Collections.emptyList()))))
                .willReturn(givenGppContext(
                        List.of(2, 6),
                        TcfEuV2Privacy.of(2, "gppConsent"),
                        UspV1Privacy.of("gppUsPrivacy"),
                        List.of("warning")));

        final CookieSyncRequest cookieSyncRequest = givenCookieSyncRequest(
                givenValidGppString(), List.of(2, 6), 1, "consent", "usPrivacy");
        final CookieSyncContext cookieSyncContext = givenCookieSyncContext(true);

        // when
        final CookieSyncRequest result = cookieSyncGppService.apply(cookieSyncRequest, cookieSyncContext);

        // then
        assertThat(result).isSameAs(cookieSyncRequest);
        assertThat(cookieSyncContext.getWarnings()).containsExactly("warning");
    }

    @Test
    public void applyShouldNotAddWarningsInCookieSyncContextIfDebugDisabled() {
        // given
        given(gppService.processContext(
                argThat(gppContextMatcher(
                        Set.of(2, 6),
                        TcfEuV2Privacy.of(1, "consent"),
                        UspV1Privacy.of("usPrivacy"),
                        Collections.emptyList()))))
                .willReturn(givenGppContext(
                        List.of(2, 6),
                        TcfEuV2Privacy.of(2, "gppConsent"),
                        UspV1Privacy.of("gppUsPrivacy"),
                        List.of("warning")));

        final CookieSyncRequest cookieSyncRequest = givenCookieSyncRequest(
                givenValidGppString(), List.of(2, 6), 1, "consent", "usPrivacy");
        final CookieSyncContext cookieSyncContext = givenCookieSyncContext(false);

        // when
        final CookieSyncRequest result = cookieSyncGppService.apply(cookieSyncRequest, cookieSyncContext);

        // then
        assertThat(result).isSameAs(cookieSyncRequest);
        assertThat(cookieSyncContext.getWarnings()).isEmpty();
    }

    private static ArgumentMatcher<GppContext> gppContextMatcher(Set<Integer> sectionsIds,
                                                                 TcfEuV2Privacy tcfEuV2Privacy,
                                                                 UspV1Privacy uspV1Privacy,
                                                                 List<String> errors) {

        return gppContext -> Objects.equals(gppContext.scope().getSectionsIds(), sectionsIds)
                && Objects.equals(gppContext.regions().getTcfEuV2Privacy(), tcfEuV2Privacy)
                && Objects.equals(gppContext.regions().getUspV1Privacy(), uspV1Privacy)
                && Objects.equals(gppContext.errors(), errors);
    }

    private static GppContext givenGppContext(List<Integer> sectionsIds,
                                              TcfEuV2Privacy tcfEuV2Privacy,
                                              UspV1Privacy uspV1Privacy,
                                              List<String> errors) {

        final GppContext gppContext = GppContextCreator.from(givenValidGppString(), sectionsIds)
                .with(tcfEuV2Privacy)
                .with(uspV1Privacy)
                .build();
        gppContext.errors().addAll(errors);

        return gppContext;
    }

    private static String givenValidGppString() {
        try {
            return new GppModel().encode();
        } catch (EncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private static CookieSyncRequest givenCookieSyncRequest(String gpp,
                                                            List<Integer> gppSid,
                                                            Integer gdpr,
                                                            String consent,
                                                            String usPrivacy) {

        return CookieSyncRequest.builder()
                .gdpr(gdpr)
                .gdprConsent(consent)
                .usPrivacy(usPrivacy)
                .gpp(gpp)
                .gppSid(gppSid)
                .build();
    }

    private static CookieSyncContext givenCookieSyncContext(boolean debug) {
        return CookieSyncContext.builder()
                .debug(debug)
                .warnings(new ArrayList<>())
                .build();
    }
}
