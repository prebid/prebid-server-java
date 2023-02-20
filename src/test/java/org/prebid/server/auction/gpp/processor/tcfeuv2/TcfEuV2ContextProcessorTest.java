package org.prebid.server.auction.gpp.processor.tcfeuv2;

import com.iab.gpp.encoder.GppModel;
import com.iab.gpp.encoder.error.EncodingException;
import com.iab.gpp.encoder.section.TcfEuV2;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.auction.gpp.model.privacy.TcfEuV2Privacy;

import java.util.ArrayList;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

public class TcfEuV2ContextProcessorTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private TcfEuV2ContextProcessor tcfEuV2ContextProcessor;

    @Mock
    private GppModel gppModel;

    @Before
    public void setUp() {
        given(gppModel.hasSection(TcfEuV2.ID)).willReturn(false);

        tcfEuV2ContextProcessor = new TcfEuV2ContextProcessor();
    }

    @Test
    public void processShouldReturnSameGppContextIfGppIsEmpty() {
        // given
        final GppContext gppContext = givenGppContext(null, TcfEuV2Privacy.of(null, null));

        // when
        final GppContext result = tcfEuV2ContextProcessor.process(gppContext);

        // then
        assertThat(result).isSameAs(gppContext);
        assertThat(result.errors()).isEmpty();
    }

    @Test
    public void processShouldReturnSameGppContextIfPrivacyAlreadyExists() throws EncodingException {
        // given
        given(gppModel.hasSection(TcfEuV2.ID)).willReturn(true);
        given(gppModel.encodeSection(TcfEuV2.ID)).willReturn("consent");

        final GppContext gppContext = givenGppContext(
                Set.of(TcfEuV2.ID),
                TcfEuV2Privacy.of(1, "consent"));

        // when
        final GppContext result = tcfEuV2ContextProcessor.process(gppContext);

        // then
        assertThat(result).isSameAs(gppContext);
        assertThat(result.errors()).isEmpty();
    }

    @Test
    public void processShouldReturnUpdatedGppContextIfOriginalGdprNotPresent() {
        // given
        final GppContext gppContext = givenGppContext(
                Set.of(TcfEuV2.ID),
                TcfEuV2Privacy.of(null, "consent"));

        // when
        final GppContext result = tcfEuV2ContextProcessor.process(gppContext);

        // then
        assertThat(result.regions().getTcfEuV2Privacy())
                .isEqualTo(TcfEuV2Privacy.of(1, "consent"));
        assertThat(result.errors()).isEmpty();
    }

    @Test
    public void processShouldReturnUpdatedGppContextIfOriginalConsentNotPresent() throws EncodingException {
        // given
        given(gppModel.hasSection(TcfEuV2.ID)).willReturn(true);
        given(gppModel.encodeSection(TcfEuV2.ID)).willReturn("consent");

        final GppContext gppContext = givenGppContext(
                Set.of(TcfEuV2.ID),
                TcfEuV2Privacy.of(1, null));

        // when
        final GppContext result = tcfEuV2ContextProcessor.process(gppContext);

        // then
        assertThat(result.regions().getTcfEuV2Privacy())
                .isEqualTo(TcfEuV2Privacy.of(1, "consent"));
        assertThat(result.errors()).isEmpty();
    }

    @Test
    public void processShouldAddErrors() throws EncodingException {
        // given
        given(gppModel.hasSection(TcfEuV2.ID)).willReturn(true);
        given(gppModel.encodeSection(TcfEuV2.ID)).willReturn("another");

        final GppContext gppContext = givenGppContext(
                Set.of(TcfEuV2.ID),
                TcfEuV2Privacy.of(1, "consent"));

        // when
        final GppContext result = tcfEuV2ContextProcessor.process(gppContext);

        // then
        assertThat(result).isSameAs(gppContext);
        assertThat(result.errors()).isNotEmpty();
    }

    private GppContext givenGppContext(Set<Integer> sectionsIds, TcfEuV2Privacy tcfEuV2Privacy) {
        return new GppContext(
                GppContext.Scope.of(gppModel, sectionsIds),
                GppContext.Regions.builder()
                        .tcfEuV2Privacy(tcfEuV2Privacy)
                        .build(),
                new ArrayList<>());
    }
}
