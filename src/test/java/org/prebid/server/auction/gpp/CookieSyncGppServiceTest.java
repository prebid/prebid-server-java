package org.prebid.server.auction.gpp;

import com.iab.gpp.encoder.GppModel;
import com.iab.gpp.encoder.error.EncodingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.Rule;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.auction.gpp.model.GppContextCreator;
import org.prebid.server.auction.gpp.model.GppContextWrapper;
import org.prebid.server.auction.gpp.model.privacy.TcfEuV2Privacy;
import org.prebid.server.auction.gpp.model.privacy.UspV1Privacy;
import org.prebid.server.cookie.model.CookieSyncContext;
import org.prebid.server.proto.request.CookieSyncRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;

public class CookieSyncGppServiceTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private GppService gppService;

    private CookieSyncGppService target;

    @BeforeEach
    public void setUp() {
        target = new CookieSyncGppService(gppService);
    }

    @Test
    public void contextFromShouldReturnExpectedGppContext() {
        // given
        given(gppService.processContext(
                argThat(gppContextWrapperMatcher(
                        Set.of(2, 6),
                        TcfEuV2Privacy.of(1, "consent"),
                        UspV1Privacy.of("usPrivacy"),
                        Collections.emptyList()))))
                .willReturn(givenGppContextWrapper(
                        List.of(2, 6),
                        TcfEuV2Privacy.of(2, "gppConsent"),
                        UspV1Privacy.of("gppUsPrivacy"),
                        Collections.emptyList()));

        final CookieSyncRequest cookieSyncRequest = givenCookieSyncRequest(
                givenValidGppString(), List.of(2, 6), 1, "consent", "usPrivacy");
        final CookieSyncContext cookieSyncContext = givenCookieSyncContext(cookieSyncRequest, true);

        // when
        final GppContext result = target.contextFrom(cookieSyncContext);

        // then
        assertThat(result.scope().getSectionsIds()).containsExactlyInAnyOrder(2, 6);
        assertThat(result.regions().getTcfEuV2Privacy()).isEqualTo(TcfEuV2Privacy.of(2, "gppConsent"));
        assertThat(result.regions().getUspV1Privacy()).isEqualTo(UspV1Privacy.of("gppUsPrivacy"));
        assertThat(cookieSyncContext.getWarnings()).isEmpty();
    }

    @Test
    public void contextFromShouldAddWarningsInCookieSyncContextIfDebugEnabled() {
        // given
        given(gppService.processContext(any()))
                .willReturn(givenGppContextWrapper(
                        List.of(2, 6),
                        TcfEuV2Privacy.of(2, "gppConsent"),
                        UspV1Privacy.of("gppUsPrivacy"),
                        singletonList("Ups, something went wrong")));

        final CookieSyncRequest cookieSyncRequest = givenCookieSyncRequest(
                givenValidGppString(), List.of(2, 6), 1, "consent", "usPrivacy");
        final CookieSyncContext cookieSyncContext = givenCookieSyncContext(cookieSyncRequest, true);

        // when
        target.contextFrom(cookieSyncContext);

        // then
        assertThat(cookieSyncContext.getWarnings()).containsExactly("Ups, something went wrong");
    }

    @Test
    public void contextFromShouldNotAddWarningsInCookieSyncContextIfDebugDisabled() {
        // given
        given(gppService.processContext(any()))
                .willReturn(givenGppContextWrapper(
                        List.of(2, 6),
                        TcfEuV2Privacy.of(2, "gppConsent"),
                        UspV1Privacy.of("gppUsPrivacy"),
                        singletonList("Ups, something went wrong")));

        final CookieSyncRequest cookieSyncRequest = givenCookieSyncRequest(
                givenValidGppString(), List.of(2, 6), 1, "consent", "usPrivacy");
        final CookieSyncContext cookieSyncContext = givenCookieSyncContext(cookieSyncRequest, false);

        // when
        target.contextFrom(cookieSyncContext);

        // then
        assertThat(cookieSyncContext.getWarnings()).isEmpty();
    }

    @Test
    public void updateCookieSyncRequestShouldReturnSameCookieSyncRequest() {
        // given
        final CookieSyncRequest cookieSyncRequest = givenCookieSyncRequest(
                givenValidGppString(), List.of(2, 6), 1, "consent", "usPrivacy");
        final CookieSyncContext cookieSyncContext = givenCookieSyncContext(givenGppContext(
                List.of(2, 6),
                TcfEuV2Privacy.of(2, "gppConsent"),
                UspV1Privacy.of("gppUsPrivacy")));

        // when
        final CookieSyncRequest result = target.updateCookieSyncRequest(cookieSyncRequest, cookieSyncContext);

        // then
        assertThat(result).isSameAs(cookieSyncRequest);
        assertThat(cookieSyncContext.getWarnings()).isEmpty();
    }

    @Test
    public void updateCookieSyncRequestShouldUpdateCookieSyncRequestGdpr() {
        // given
        final CookieSyncRequest cookieSyncRequest = givenCookieSyncRequest(
                givenValidGppString(), List.of(2, 6), null, "consent", "usPrivacy");
        final CookieSyncContext cookieSyncContext = givenCookieSyncContext(givenGppContext(
                List.of(2, 6),
                TcfEuV2Privacy.of(2, "gppConsent"),
                null));

        // when
        final CookieSyncRequest result = target.updateCookieSyncRequest(cookieSyncRequest, cookieSyncContext);

        // then
        assertThat(result.getGdpr()).isEqualTo(2);
        assertThat(result.getGdprConsent()).isSameAs(cookieSyncRequest.getGdprConsent());
        assertThat(result.getUsPrivacy()).isSameAs(cookieSyncRequest.getUsPrivacy());
        assertThat(cookieSyncContext.getWarnings()).isEmpty();
    }

    @Test
    public void updateCookieSyncRequestShouldUpdateCookieSyncRequestConsent() {
        // given
        final CookieSyncRequest cookieSyncRequest = givenCookieSyncRequest(
                givenValidGppString(), List.of(2, 6), 1, null, "usPrivacy");
        final CookieSyncContext cookieSyncContext = givenCookieSyncContext(givenGppContext(
                List.of(2, 6),
                TcfEuV2Privacy.of(2, "gppConsent"),
                UspV1Privacy.of("gppUsPrivacy")));

        // when
        final CookieSyncRequest result = target.updateCookieSyncRequest(cookieSyncRequest, cookieSyncContext);

        // then
        assertThat(result.getGdpr()).isSameAs(cookieSyncRequest.getGdpr());
        assertThat(result.getGdprConsent()).isEqualTo("gppConsent");
        assertThat(result.getUsPrivacy()).isSameAs(cookieSyncRequest.getUsPrivacy());
        assertThat(cookieSyncContext.getWarnings()).isEmpty();
    }

    @Test
    public void updateCookieSyncRequestShouldUpdateCookieSyncRequestUsPrivacy() {
        // given
        final CookieSyncRequest cookieSyncRequest = givenCookieSyncRequest(
                givenValidGppString(), List.of(2, 6), 1, "consent", null);
        final CookieSyncContext cookieSyncContext = givenCookieSyncContext(givenGppContext(
                List.of(2, 6),
                null,
                UspV1Privacy.of("gppUsPrivacy")));

        // when
        final CookieSyncRequest result = target.updateCookieSyncRequest(cookieSyncRequest, cookieSyncContext);

        // then
        assertThat(result.getGdpr()).isSameAs(cookieSyncRequest.getGdpr());
        assertThat(result.getGdprConsent()).isSameAs(cookieSyncRequest.getGdprConsent());
        assertThat(result.getUsPrivacy()).isEqualTo("gppUsPrivacy");
        assertThat(cookieSyncContext.getWarnings()).isEmpty();
    }

    @Test
    public void updateCookieSyncRequestShouldUpdateCookieSyncRequest() {
        // given
        final CookieSyncRequest cookieSyncRequest = givenCookieSyncRequest(
                givenValidGppString(), List.of(2, 6), null, null, null);
        final CookieSyncContext cookieSyncContext = givenCookieSyncContext(givenGppContext(
                List.of(2, 6),
                TcfEuV2Privacy.of(2, "gppConsent"),
                UspV1Privacy.of("gppUsPrivacy")));

        // when
        final CookieSyncRequest result = target.updateCookieSyncRequest(cookieSyncRequest, cookieSyncContext);

        // then
        assertThat(result.getGdpr()).isEqualTo(2);
        assertThat(result.getGdprConsent()).isEqualTo("gppConsent");
        assertThat(result.getUsPrivacy()).isEqualTo("gppUsPrivacy");
        assertThat(cookieSyncContext.getWarnings()).isEmpty();
    }

    private static ArgumentMatcher<GppContextWrapper> gppContextWrapperMatcher(Set<Integer> sectionsIds,
                                                                               TcfEuV2Privacy tcfEuV2Privacy,
                                                                               UspV1Privacy uspV1Privacy,
                                                                               List<String> errors) {

        return wrapper -> Objects.equals(wrapper.getGppContext().scope().getSectionsIds(), sectionsIds)
                && Objects.equals(wrapper.getGppContext().regions().getTcfEuV2Privacy(), tcfEuV2Privacy)
                && Objects.equals(wrapper.getGppContext().regions().getUspV1Privacy(), uspV1Privacy)
                && Objects.equals(wrapper.getErrors(), errors);
    }

    private static GppContextWrapper givenGppContextWrapper(List<Integer> sectionsIds,
                                                            TcfEuV2Privacy tcfEuV2Privacy,
                                                            UspV1Privacy uspV1Privacy,
                                                            List<String> errors) {

        final GppContextWrapper gppContextWrapper = GppContextCreator.from(givenValidGppString(), sectionsIds)
                .with(tcfEuV2Privacy)
                .with(uspV1Privacy)
                .build();
        gppContextWrapper.getErrors().addAll(errors);

        return gppContextWrapper;
    }

    private static GppContext givenGppContext(List<Integer> sectionsIds,
                                              TcfEuV2Privacy tcfEuV2Privacy,
                                              UspV1Privacy uspV1Privacy) {

        return givenGppContextWrapper(sectionsIds, tcfEuV2Privacy, uspV1Privacy, Collections.emptyList())
                .getGppContext();
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

    private static CookieSyncContext givenCookieSyncContext(CookieSyncRequest cookieSyncRequest, boolean debug) {
        return CookieSyncContext.builder()
                .cookieSyncRequest(cookieSyncRequest)
                .debug(debug)
                .warnings(new ArrayList<>())
                .build();
    }

    private static CookieSyncContext givenCookieSyncContext(GppContext gppContext) {
        return CookieSyncContext.builder()
                .gppContext(gppContext)
                .warnings(new ArrayList<>())
                .build();
    }
}
