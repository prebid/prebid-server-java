package org.prebid.server.auction.gpp.processor.tcfeuv2;

import com.iab.gpp.encoder.GppModel;
import com.iab.gpp.encoder.error.EncodingException;
import com.iab.gpp.encoder.section.TcfEuV2;
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

public class TcfEuV2ContextTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private GppModel gppModel;

    @Test
    public void resolveGdprShouldReturnUnalteredResultIfSectionsIdsIsNull() {
        // given
        final TcfEuV2Context tcfEuV2Context = new TcfEuV2Context(gppModel, null);

        // when
        final UpdateResult<Integer> result = tcfEuV2Context.resolveGdpr(1);

        // then
        assertThat(result).isEqualTo(UpdateResult.unaltered(1));
    }

    @Test
    public void resolveGdprShouldReturn0IfOriginalGdprIsNullAndNotInTcfEuV2Scope() {
        // given
        final TcfEuV2Context tcfEuV2Context = new TcfEuV2Context(gppModel, emptySet());

        // when
        final UpdateResult<Integer> result = tcfEuV2Context.resolveGdpr(null);

        // then
        assertThat(result).isEqualTo(UpdateResult.updated(0));
    }

    @Test
    public void resolveGdprShouldReturn1IfOriginalGdprIsNullAndInTcfEuV2Scope() {
        // given
        final TcfEuV2Context tcfEuV2Context = new TcfEuV2Context(gppModel, Set.of(TcfEuV2.ID));

        // when
        final UpdateResult<Integer> result = tcfEuV2Context.resolveGdpr(null);

        // then
        assertThat(result).isEqualTo(UpdateResult.updated(1));
    }

    @Test
    public void resolveGdprShouldLogErrorIfOriginalGdpr1DoesNotMatchGppSid() {
        // given
        final TcfEuV2Context tcfEuV2Context = new TcfEuV2Context(gppModel, emptySet());

        // when
        final UpdateResult<Integer> result = tcfEuV2Context.resolveGdpr(1);

        // then
        assertThat(result).isEqualTo(UpdateResult.unaltered(1));
        assertThat(tcfEuV2Context.getErrors()).containsExactly("GPP scope does not match TCF2 scope");
    }

    @Test
    public void resolveGdprShouldLogErrorIfOriginalGdpr0DoesNotMatchGppSid() {
        // given
        final TcfEuV2Context tcfEuV2Context = new TcfEuV2Context(gppModel, Set.of(TcfEuV2.ID));

        // when
        final UpdateResult<Integer> result = tcfEuV2Context.resolveGdpr(0);

        // then
        assertThat(result).isEqualTo(UpdateResult.unaltered(0));
        assertThat(tcfEuV2Context.getErrors()).containsExactly("GPP scope does not match TCF2 scope");
    }

    @Test
    public void resolveConsentShouldReturnUnalteredResultIfSectionsIdsIsNull() {
        // given
        final TcfEuV2Context tcfEuV2Context = new TcfEuV2Context(gppModel, null);

        // when
        final UpdateResult<String> result = tcfEuV2Context.resolveConsent(null);

        // then
        assertThat(result).isEqualTo(UpdateResult.unaltered(null));
    }

    @Test
    public void resolveConsentShouldReturnUnalteredResultIfSectionsIdsDoesNotContainsTcfEuV2Scope() {
        // given
        final TcfEuV2Context tcfEuV2Context = new TcfEuV2Context(gppModel, emptySet());

        // when
        final UpdateResult<String> result = tcfEuV2Context.resolveConsent("consent");

        // then
        assertThat(result).isEqualTo(UpdateResult.unaltered("consent"));
    }

    @Test
    public void resolveConsentShouldReturnUnalteredResultIfGppModelIsNull() {
        // given
        final TcfEuV2Context tcfEuV2Context = new TcfEuV2Context(null, Set.of(TcfEuV2.ID));

        // when
        final UpdateResult<String> result = tcfEuV2Context.resolveConsent("consent");

        // then
        assertThat(result).isEqualTo(UpdateResult.unaltered("consent"));
    }

    @Test
    public void resolveConsentShouldReturnUnalteredResultIfGppModelDoesNotContainsTcfEuV2Scope() {
        // given
        given(gppModel.hasSection(eq(TcfEuV2.ID))).willReturn(false);
        final TcfEuV2Context tcfEuV2Context = new TcfEuV2Context(gppModel, Set.of(TcfEuV2.ID));

        // when
        final UpdateResult<String> result = tcfEuV2Context.resolveConsent("consent");

        // then
        assertThat(result).isEqualTo(UpdateResult.unaltered("consent"));
    }

    @Test
    public void resolveConsentShouldReturnConsentFromGppIfOriginalIsAbsent() throws EncodingException {
        // given
        given(gppModel.hasSection(eq(TcfEuV2.ID))).willReturn(true);
        given(gppModel.encodeSection(eq(TcfEuV2.ID))).willReturn("consent");
        final TcfEuV2Context tcfEuV2Context = new TcfEuV2Context(gppModel, Set.of(TcfEuV2.ID));

        // when
        final UpdateResult<String> result = tcfEuV2Context.resolveConsent(null);

        // then
        assertThat(result).isEqualTo(UpdateResult.updated("consent"));
    }

    @Test
    public void resolveConsentShouldLogErrorIfOriginalConsentDoesNotMatchGppConsent() throws EncodingException {
        // given
        given(gppModel.hasSection(eq(TcfEuV2.ID))).willReturn(true);
        given(gppModel.encodeSection(eq(TcfEuV2.ID))).willReturn("another");
        final TcfEuV2Context tcfEuV2Context = new TcfEuV2Context(gppModel, Set.of(TcfEuV2.ID));

        // when
        final UpdateResult<String> result = tcfEuV2Context.resolveConsent("consent");

        // then
        assertThat(result).isEqualTo(UpdateResult.unaltered("consent"));
        assertThat(tcfEuV2Context.getErrors()).containsExactly("GPP TCF2 string does not match user.consent");
    }
}
