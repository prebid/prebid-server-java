package org.prebid.server.auction.gpp.processor.uspv1;

import com.iab.gpp.encoder.GppModel;
import com.iab.gpp.encoder.error.EncodingException;
import com.iab.gpp.encoder.section.UspV1;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.model.UpdateResult;

import java.util.Set;

import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

public class UspV1ContextTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private GppModel gppModel;

    @Test
    public void resolveUsPrivacyShouldReturnUnalteredResultIfSectionsIdsIsNull() {
        // given
        final UspV1Context uspV1Context = UspV1Context.of(gppModel, null);

        // when
        final UpdateResult<String> result = uspV1Context.resolveUsPrivacy(null);

        // then
        assertThat(result).isEqualTo(UpdateResult.unaltered(null));
    }

    @Test
    public void resolveUsPrivacyShouldReturnUnalteredResultIfSectionsIdsDoesNotContainsUspV1Scope() {
        // given
        final UspV1Context uspV1Context = UspV1Context.of(gppModel, emptySet());

        // when
        final UpdateResult<String> result = uspV1Context.resolveUsPrivacy("usPrivacy");

        // then
        assertThat(result).isEqualTo(UpdateResult.unaltered("usPrivacy"));
    }

    @Test
    public void resolveUsPrivacyShouldReturnUnalteredResultIfGppModelIsNull() {
        // given
        final UspV1Context uspV1Context = UspV1Context.of(null, Set.of(UspV1.ID));

        // when
        final UpdateResult<String> result = uspV1Context.resolveUsPrivacy("usPrivacy");

        // then
        assertThat(result).isEqualTo(UpdateResult.unaltered("usPrivacy"));
    }

    @Test
    public void resolveUsPrivacyShouldReturnUnalteredResultIfGppModelDoesNotContainsUspV1Scope() {
        // given
        given(gppModel.hasSection(eq(UspV1.ID))).willReturn(false);
        final UspV1Context uspV1Context = UspV1Context.of(gppModel, Set.of(UspV1.ID));

        // when
        final UpdateResult<String> result = uspV1Context.resolveUsPrivacy("usPrivacy");

        // then
        assertThat(result).isEqualTo(UpdateResult.unaltered("usPrivacy"));
    }

    @Test
    public void resolveUsPrivacyShouldReturnUsPrivacyFromGppIfOriginalIsAbsent() throws EncodingException {
        // given
        given(gppModel.hasSection(eq(UspV1.ID))).willReturn(true);
        given(gppModel.encodeSection(eq(UspV1.ID))).willReturn("usPrivacy");
        final UspV1Context uspV1Context = UspV1Context.of(gppModel, Set.of(UspV1.ID));

        // when
        final UpdateResult<String> result = uspV1Context.resolveUsPrivacy(null);

        // then
        assertThat(result).isEqualTo(UpdateResult.updated("usPrivacy"));
    }

    @Test
    public void resolveUsPrivacyShouldLogErrorIfOriginalUsPrivacyDoesNotMatchGppUsPrivacy() throws EncodingException {
        // given
        given(gppModel.hasSection(eq(UspV1.ID))).willReturn(true);
        given(gppModel.encodeSection(eq(UspV1.ID))).willReturn("another");
        final UspV1Context uspV1Context = UspV1Context.of(gppModel, Set.of(UspV1.ID));

        // when
        final UpdateResult<String> result = uspV1Context.resolveUsPrivacy("usPrivacy");

        // then
        assertThat(result).isEqualTo(UpdateResult.unaltered("usPrivacy"));
        assertThat(uspV1Context.getErrors()).containsExactly("USP string does not match regs.us_privacy");
    }
}
