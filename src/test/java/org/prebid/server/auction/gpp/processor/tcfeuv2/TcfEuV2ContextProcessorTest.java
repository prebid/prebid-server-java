package org.prebid.server.auction.gpp.processor.tcfeuv2;

import com.iab.gpp.encoder.GppModel;
import com.iab.gpp.encoder.error.EncodingException;
import com.iab.gpp.encoder.section.TcfEuV2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.auction.gpp.model.GppContextWrapper;
import org.prebid.server.auction.gpp.model.privacy.TcfEuV2Privacy;

import java.util.Set;

import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;

@ExtendWith(MockitoExtension.class)
public class TcfEuV2ContextProcessorTest {

    private TcfEuV2ContextProcessor tcfEuV2ContextProcessor;

    @Mock(strictness = LENIENT)
    private GppModel gppModel;

    @BeforeEach
    public void setUp() {
        given(gppModel.hasSection(TcfEuV2.ID)).willReturn(false);

        tcfEuV2ContextProcessor = new TcfEuV2ContextProcessor();
    }

    @Test
    public void processShouldReturnSameGppContextIfGppIsEmpty() {
        // given
        final GppContext gppContext = givenGppContext(null, TcfEuV2Privacy.of(null, null));

        // when
        final GppContextWrapper result = tcfEuV2ContextProcessor.process(gppContext);

        // then
        assertThat(result.getGppContext()).isSameAs(gppContext);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void processShouldReturnSameGppContextIfPrivacyAlreadyExists() throws EncodingException {
        // given
        given(gppModel.hasSection(TcfEuV2.ID)).willReturn(true);
        given(gppModel.encodeSection(TcfEuV2.ID)).willReturn("consent");

        final GppContext gppContext = givenGppContext(Set.of(TcfEuV2.ID), TcfEuV2Privacy.of(1, "consent"));

        // when
        final GppContextWrapper result = tcfEuV2ContextProcessor.process(gppContext);

        // then
        assertThat(result.getGppContext()).isSameAs(gppContext);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void processShouldReturnUpdatedGppContextWithGdpr0IfOriginalGdprNotPresentAndTcfEuV2NotInScope() {
        // given
        final GppContext gppContext = givenGppContext(emptySet(), TcfEuV2Privacy.of(null, "consent"));

        // when
        final GppContextWrapper result = tcfEuV2ContextProcessor.process(gppContext);

        // then
        assertThat(result.getGppContext().regions().getTcfEuV2Privacy())
                .isEqualTo(TcfEuV2Privacy.of(0, "consent"));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void processShouldReturnUpdatedGppContextWithGdpr1IfOriginalGdprNotPresentAndTcfEuV2InScope() {
        // given
        final GppContext gppContext = givenGppContext(Set.of(TcfEuV2.ID), TcfEuV2Privacy.of(null, "consent"));

        // when
        final GppContextWrapper result = tcfEuV2ContextProcessor.process(gppContext);

        // then
        assertThat(result.getGppContext().regions().getTcfEuV2Privacy())
                .isEqualTo(TcfEuV2Privacy.of(1, "consent"));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void processShouldReturnErrorIfOriginalGdpr0AndTcfEuV2InScope() {
        // given
        final GppContext gppContext = givenGppContext(Set.of(TcfEuV2.ID), TcfEuV2Privacy.of(0, "consent"));

        // when
        final GppContextWrapper result = tcfEuV2ContextProcessor.process(gppContext);

        // then
        assertThat(result.getGppContext()).isSameAs(gppContext);
        assertThat(result.getErrors()).containsExactly("GPP scope does not match TCF2 scope");
    }

    @Test
    public void processShouldReturnErrorIfOriginalGdpr1AndTcfEuV2NotInScope() {
        // given
        final GppContext gppContext = givenGppContext(emptySet(), TcfEuV2Privacy.of(1, "consent"));

        // when
        final GppContextWrapper result = tcfEuV2ContextProcessor.process(gppContext);

        // then
        assertThat(result.getGppContext()).isSameAs(gppContext);
        assertThat(result.getErrors()).containsExactly("GPP scope does not match TCF2 scope");
    }

    @Test
    public void processShouldReturnGppContextWithSameConsentIfTcfEuV2NotInScope() throws EncodingException {
        // given
        given(gppModel.hasSection(TcfEuV2.ID)).willReturn(true);
        given(gppModel.encodeSection(TcfEuV2.ID)).willReturn("consent");

        final GppContext gppContext = givenGppContext(emptySet(), TcfEuV2Privacy.of(0, null));

        // when
        final GppContextWrapper result = tcfEuV2ContextProcessor.process(gppContext);

        // then
        assertThat(result.getGppContext()).isSameAs(gppContext);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void processShouldReturnGppContextWithSameConsentIfTcfEuV2NotPresentInGpp() throws EncodingException {
        // given
        given(gppModel.hasSection(TcfEuV2.ID)).willReturn(false);
        given(gppModel.encodeSection(TcfEuV2.ID)).willReturn("consent");

        final GppContext gppContext = givenGppContext(Set.of(TcfEuV2.ID), TcfEuV2Privacy.of(1, null));

        // when
        final GppContextWrapper result = tcfEuV2ContextProcessor.process(gppContext);

        // then
        assertThat(result.getGppContext()).isSameAs(gppContext);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void processShouldReturnUpdatedGppContextIfOriginalConsentNotPresent() throws EncodingException {
        // given
        given(gppModel.hasSection(TcfEuV2.ID)).willReturn(true);
        given(gppModel.encodeSection(TcfEuV2.ID)).willReturn("consent");

        final GppContext gppContext = givenGppContext(Set.of(TcfEuV2.ID), TcfEuV2Privacy.of(1, null));

        // when
        final GppContextWrapper result = tcfEuV2ContextProcessor.process(gppContext);

        // then
        assertThat(result.getGppContext().regions().getTcfEuV2Privacy())
                .isEqualTo(TcfEuV2Privacy.of(1, "consent"));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void processShouldReturnErrorIfOriginalConsentDoesNotMatchGppConsent() throws EncodingException {
        // given
        given(gppModel.hasSection(TcfEuV2.ID)).willReturn(true);
        given(gppModel.encodeSection(TcfEuV2.ID)).willReturn("another");

        final GppContext gppContext = givenGppContext(Set.of(TcfEuV2.ID), TcfEuV2Privacy.of(1, "consent"));

        // when
        final GppContextWrapper result = tcfEuV2ContextProcessor.process(gppContext);

        // then
        assertThat(result.getGppContext()).isSameAs(gppContext);
        assertThat(result.getErrors()).containsExactly("GPP TCF2 string does not match user.consent");
    }

    private GppContext givenGppContext(Set<Integer> sectionsIds, TcfEuV2Privacy tcfEuV2Privacy) {
        return new GppContext(
                GppContext.Scope.of(gppModel, sectionsIds),
                GppContext.Regions.builder()
                        .tcfEuV2Privacy(tcfEuV2Privacy)
                        .build());
    }
}
