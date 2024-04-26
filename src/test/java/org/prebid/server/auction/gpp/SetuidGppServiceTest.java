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
import org.prebid.server.auction.model.SetuidContext;
import org.prebid.server.privacy.model.Privacy;
import org.prebid.server.privacy.model.PrivacyContext;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;

public class SetuidGppServiceTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private GppService gppService;

    private SetuidGppService target;

    @BeforeEach
    public void setUp() {
        target = new SetuidGppService(gppService);
    }

    @Test
    public void contextFromShouldReturnExpectedGppContext() {
        // given
        given(gppService.processContext(
                argThat(gppContextWrapperMatcher(
                        Set.of(2, 6),
                        TcfEuV2Privacy.of(1, "consent"),
                        Collections.emptyList()))))
                .willReturn(givenGppContextWrapper(
                        List.of(2, 6),
                        TcfEuV2Privacy.of(2, "gppConsent"),
                        Collections.emptyList()));

        final Privacy privacy = givenPrivacy(givenValidGppString(), List.of(2, 6), 1, "consent");
        final SetuidContext setuidContext = givenSetuidContext(privacy);

        // when
        final GppContext result = target.contextFrom(setuidContext).result();

        // then
        assertThat(result.scope().getSectionsIds()).containsExactlyInAnyOrder(2, 6);
        assertThat(result.regions().getTcfEuV2Privacy()).isEqualTo(TcfEuV2Privacy.of(2, "gppConsent"));
    }

    @Test
    public void updateSetuidContextShouldReturnSameSetuidContext() {
        // given
        final Privacy privacy = givenPrivacy(givenValidGppString(), List.of(2, 6), 1, "consent");
        final SetuidContext setuidContext = givenSetuidContext(
                privacy, givenGppContext(List.of(2, 6), TcfEuV2Privacy.of(2, "gppConsent")));

        // when
        final SetuidContext result = target.updateSetuidContext(setuidContext);

        // then
        assertThat(result).isSameAs(setuidContext);
    }

    @Test
    public void updateSetuidContextShouldUpdateSetuidContextGdpr() {
        // given
        final Privacy initialPrivacy = givenPrivacy(givenValidGppString(), List.of(2, 6), null, "consent");
        final SetuidContext setuidContext = givenSetuidContext(
                initialPrivacy, givenGppContext(List.of(2, 6), TcfEuV2Privacy.of(2, "gppConsent")));

        // when
        final SetuidContext result = target.updateSetuidContext(setuidContext);

        // then
        assertThat(result.getPrivacyContext().getPrivacy()).satisfies(privacy -> {
            assertThat(privacy.getGdpr()).isEqualTo("2");
            assertThat(privacy.getConsentString()).isSameAs(initialPrivacy.getConsentString());
        });
    }

    @Test
    public void updateSetuidContextShouldUpdateSetuidContextConsent() {
        // given
        final Privacy initialPrivacy = givenPrivacy(givenValidGppString(), List.of(2, 6), 1, null);
        final SetuidContext setuidContext = givenSetuidContext(
                initialPrivacy, givenGppContext(List.of(2, 6), TcfEuV2Privacy.of(2, "gppConsent")));

        // when
        final SetuidContext result = target.updateSetuidContext(setuidContext);

        // then
        assertThat(result.getPrivacyContext().getPrivacy()).satisfies(privacy -> {
            assertThat(privacy.getGdpr()).isEqualTo(initialPrivacy.getGdpr());
            assertThat(privacy.getConsentString()).isEqualTo("gppConsent");
        });
    }

    @Test
    public void updateSetuidContextShouldUpdateSetuidContext() {
        // given
        final Privacy initialPrivacy = givenPrivacy(givenValidGppString(), List.of(2, 6), null, null);
        final SetuidContext setuidContext = givenSetuidContext(
                initialPrivacy, givenGppContext(List.of(2, 6), TcfEuV2Privacy.of(2, "gppConsent")));

        // when
        final SetuidContext result = target.updateSetuidContext(setuidContext);

        // then
        assertThat(result.getPrivacyContext().getPrivacy()).satisfies(privacy -> {
            assertThat(privacy.getGdpr()).isEqualTo("2");
            assertThat(privacy.getConsentString()).isEqualTo("gppConsent");
        });
    }

    private static ArgumentMatcher<GppContextWrapper> gppContextWrapperMatcher(Set<Integer> sectionsIds,
                                                                               TcfEuV2Privacy tcfEuV2Privacy,
                                                                               List<String> errors) {

        return wrapper -> Objects.equals(wrapper.getGppContext().scope().getSectionsIds(), sectionsIds)
                && Objects.equals(wrapper.getGppContext().regions().getTcfEuV2Privacy(), tcfEuV2Privacy)
                && Objects.equals(wrapper.getErrors(), errors);
    }

    private static GppContextWrapper givenGppContextWrapper(List<Integer> sectionsIds,
                                                            TcfEuV2Privacy tcfEuV2Privacy,
                                                            List<String> errors) {

        final GppContextWrapper gppContextWrapper = GppContextCreator.from(givenValidGppString(), sectionsIds)
                .with(tcfEuV2Privacy)
                .build();
        gppContextWrapper.getErrors().addAll(errors);

        return gppContextWrapper;
    }

    private static GppContext givenGppContext(List<Integer> sectionsIds, TcfEuV2Privacy tcfEuV2Privacy) {
        return givenGppContextWrapper(sectionsIds, tcfEuV2Privacy, Collections.emptyList())
                .getGppContext();
    }

    private static String givenValidGppString() {
        try {
            return new GppModel().encode();
        } catch (EncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private static Privacy givenPrivacy(String gpp,
                                        List<Integer> gppSid,
                                        Integer gdpr,
                                        String consent) {

        return Privacy.builder()
                .gpp(gpp)
                .gppSid(gppSid)
                .gdpr(gdpr != null ? gdpr.toString() : null)
                .consentString(consent)
                .build();
    }

    private static SetuidContext givenSetuidContext(Privacy privacy) {
        return SetuidContext.builder()
                .privacyContext(PrivacyContext.of(privacy, null))
                .build();
    }

    private static SetuidContext givenSetuidContext(Privacy privacy, GppContext gppContext) {
        return SetuidContext.builder()
                .privacyContext(PrivacyContext.of(privacy, null))
                .gppContext(gppContext)
                .build();
    }
}
