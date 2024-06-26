package org.prebid.server.auction.gpp.processor.uspv1;

import com.iab.gpp.encoder.GppModel;
import com.iab.gpp.encoder.error.EncodingException;
import com.iab.gpp.encoder.section.UspV1;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.auction.gpp.model.GppContextWrapper;
import org.prebid.server.auction.gpp.model.privacy.UspV1Privacy;

import java.util.Set;

import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;

@ExtendWith(MockitoExtension.class)
public class UspV1ContextProcessorTest {

    private UspV1ContextProcessor uspV1ContextProcessor;

    @Mock(strictness = LENIENT)
    private GppModel gppModel;

    @BeforeEach
    public void setUp() {
        given(gppModel.hasSection(UspV1.ID)).willReturn(false);

        uspV1ContextProcessor = new UspV1ContextProcessor();
    }

    @Test
    public void processShouldReturnSameGppContextIfGppIsEmpty() {
        // given
        final GppContext gppContext = givenGppContext(null, UspV1Privacy.of(null));

        // when
        final GppContextWrapper result = uspV1ContextProcessor.process(gppContext);

        // then
        assertThat(result.getGppContext()).isSameAs(gppContext);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void processShouldReturnGppContextWithSameUsPrivacyIfUspV1NotInScope() throws EncodingException {
        // given
        given(gppModel.hasSection(eq(UspV1.ID))).willReturn(true);
        given(gppModel.encodeSection(eq(UspV1.ID))).willReturn("usPrivacy");

        final GppContext gppContext = givenGppContext(emptySet(), UspV1Privacy.of(null));

        // when
        final GppContextWrapper result = uspV1ContextProcessor.process(gppContext);

        // then
        assertThat(result.getGppContext()).isSameAs(gppContext);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void processShouldReturnGppContextWithSameUsPrivacyIfGppDoesNotContainsUspV1() throws EncodingException {
        // given
        given(gppModel.hasSection(eq(UspV1.ID))).willReturn(false);
        given(gppModel.encodeSection(eq(UspV1.ID))).willReturn("usPrivacy");

        final GppContext gppContext = givenGppContext(Set.of(UspV1.ID), UspV1Privacy.of(null));

        // when
        final GppContextWrapper result = uspV1ContextProcessor.process(gppContext);

        // then
        assertThat(result.getGppContext()).isSameAs(gppContext);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void processShouldReturnUpdatedGppContextIfOriginalUsPrivacyNotPresent() throws EncodingException {
        // given
        given(gppModel.hasSection(UspV1.ID)).willReturn(true);
        given(gppModel.encodeSection(UspV1.ID)).willReturn("usPrivacy");

        final GppContext gppContext = givenGppContext(
                Set.of(UspV1.ID),
                UspV1Privacy.of(null));

        // when
        final GppContextWrapper result = uspV1ContextProcessor.process(gppContext);

        // then
        assertThat(result.getGppContext().regions().getUspV1Privacy())
                .isEqualTo(UspV1Privacy.of("usPrivacy"));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void processShouldReturnSameGppContextIfPrivacyAlreadyExists() throws EncodingException {
        // given
        given(gppModel.hasSection(UspV1.ID)).willReturn(true);
        given(gppModel.encodeSection(UspV1.ID)).willReturn("usPrivacy");

        final GppContext gppContext = givenGppContext(
                Set.of(UspV1.ID),
                UspV1Privacy.of("usPrivacy"));

        // when
        final GppContextWrapper result = uspV1ContextProcessor.process(gppContext);

        // then
        assertThat(result.getGppContext()).isSameAs(gppContext);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void processShouldReturnErrorIfUsPrivacyDoesNotMatchGppUsPrivacy() throws EncodingException {
        // given
        given(gppModel.hasSection(UspV1.ID)).willReturn(true);
        given(gppModel.encodeSection(UspV1.ID)).willReturn("another");

        final GppContext gppContext = givenGppContext(
                Set.of(UspV1.ID),
                UspV1Privacy.of("usPrivacy"));

        // when
        final GppContextWrapper result = uspV1ContextProcessor.process(gppContext);

        // then
        assertThat(result.getGppContext()).isSameAs(gppContext);
        assertThat(result.getErrors()).containsExactly("USP string does not match regs.us_privacy");
    }

    private GppContext givenGppContext(Set<Integer> sectionsIds, UspV1Privacy uspV1Privacy) {
        return new GppContext(
                GppContext.Scope.of(gppModel, sectionsIds),
                GppContext.Regions.builder()
                        .uspV1Privacy(uspV1Privacy)
                        .build());
    }
}
