package org.prebid.server.activity.infrastructure.creator.privacy.uscustomlogic;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.jamsesso.jsonlogic.ast.JsonLogicBoolean;
import io.github.jamsesso.jsonlogic.evaluator.JsonLogicEvaluationException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.activity.Activity;
import org.prebid.server.activity.infrastructure.creator.PrivacyModuleCreationContext;
import org.prebid.server.activity.infrastructure.privacy.PrivacyModule;
import org.prebid.server.activity.infrastructure.privacy.PrivacyModuleQualifier;
import org.prebid.server.activity.infrastructure.privacy.usnat.reader.USNationalGppReader;
import org.prebid.server.activity.infrastructure.rule.Rule;
import org.prebid.server.auction.gpp.model.GppContextCreator;
import org.prebid.server.exception.InvalidAccountConfigException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JsonLogic;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.settings.model.activity.privacy.AccountUSCustomLogicModuleConfig;

import java.util.List;
import java.util.Set;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class USCustomLogicModuleCreatorTest extends VertxTest {

    @org.junit.Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private USCustomLogicGppReaderFactory gppReaderFactory;

    @Mock
    private JsonLogic jsonLogic;

    @Mock
    private Metrics metrics;

    private USCustomLogicModuleCreator target;

    @Before
    public void setUp() {
        given(gppReaderFactory.forSection(anyInt(), anyBoolean(), any()))
                .willReturn(new USNationalGppReader(null));
        given(jsonLogic.parse(any())).willReturn(JsonLogicBoolean.TRUE);

        target = new USCustomLogicModuleCreator(
                gppReaderFactory, jsonLogic, null, null, false, metrics);
    }

    @Test
    public void qualifierShouldReturnExpectedResult() {
        // when and then
        assertThat(target.qualifier()).isEqualTo(PrivacyModuleQualifier.US_CUSTOM_LOGIC);
        verifyNoInteractions(metrics);
    }

    @Test
    public void fromShouldCreateProperPrivacyModuleIfSectionsIdsIsNull() {
        // given
        final PrivacyModuleCreationContext creationContext = givenCreationContext(
                null,
                givenConfig(singleton(7), null, Activity.CALL_BIDDER, mapper.createObjectNode()));

        // when
        final PrivacyModule privacyModule = target.from(creationContext);

        // then
        assertThat(privacyModule.proceed(null)).isEqualTo(Rule.Result.ABSTAIN);
        verifyNoInteractions(gppReaderFactory);
        verifyNoInteractions(jsonLogic);
        verifyNoInteractions(metrics);
    }

    @Test
    public void fromShouldCreateProperPrivacyModuleIfSectionsIdsIsEmpty() {
        // given
        final PrivacyModuleCreationContext creationContext = givenCreationContext(
                emptyList(),
                givenConfig(singleton(7), null, Activity.CALL_BIDDER, mapper.createObjectNode()));

        // when
        final PrivacyModule privacyModule = target.from(creationContext);

        // then
        assertThat(privacyModule.proceed(null)).isEqualTo(Rule.Result.ABSTAIN);
        verifyNoInteractions(gppReaderFactory);
        verifyNoInteractions(jsonLogic);
        verifyNoInteractions(metrics);
    }

    @Test
    public void fromShouldCreateProperPrivacyModuleIfAllSectionsIdsSkipped() {
        // given
        final PrivacyModuleCreationContext creationContext = givenCreationContext(
                singletonList(1),
                givenConfig(singleton(7), null, Activity.CALL_BIDDER, mapper.createObjectNode()));

        // when
        final PrivacyModule privacyModule = target.from(creationContext);

        // then
        assertThat(privacyModule.proceed(null)).isEqualTo(Rule.Result.ABSTAIN);
        verifyNoInteractions(gppReaderFactory);
        verifyNoInteractions(jsonLogic);
        verifyNoInteractions(metrics);
    }

    @Test
    public void fromShouldShouldSkipNotSupportedSectionsIds() throws JsonLogicEvaluationException {
        // given
        final PrivacyModuleCreationContext creationContext = givenCreationContext(
                asList(6, 7, 8, 9, 10, 11, 12, 13),
                givenConfig(Set.of(7, 8, 9, 10, 11, 12), true, Activity.CALL_BIDDER, mapper.createObjectNode()));

        // when
        target.from(creationContext);

        // then
        verify(gppReaderFactory).forSection(eq(7), eq(true), any());
        verify(gppReaderFactory).forSection(eq(8), eq(true), any());
        verify(gppReaderFactory).forSection(eq(9), eq(true), any());
        verify(gppReaderFactory).forSection(eq(10), eq(true), any());
        verify(gppReaderFactory).forSection(eq(11), eq(true), any());
        verify(gppReaderFactory).forSection(eq(12), eq(true), any());
        verifyNoMoreInteractions(gppReaderFactory);

        verify(jsonLogic, times(6)).parse(eq("{}"));
        verify(jsonLogic, times(6)).evaluate(any(), any());
        verifyNoMoreInteractions(jsonLogic);
        verifyNoInteractions(metrics);
    }

    @Test
    public void fromShouldShouldSkipNotConfiguredSectionsIds() throws JsonLogicEvaluationException {
        // given
        final PrivacyModuleCreationContext creationContext = givenCreationContext(
                asList(7, 8, 9),
                givenConfig(singleton(7), false, Activity.CALL_BIDDER, mapper.createObjectNode()));

        // when
        target.from(creationContext);

        // then
        verify(gppReaderFactory).forSection(eq(7), eq(false), any());
        verifyNoMoreInteractions(gppReaderFactory);

        verify(jsonLogic).parse(eq("{}"));
        verify(jsonLogic).evaluate(any(), any());
        verifyNoMoreInteractions(jsonLogic);
        verifyNoInteractions(metrics);
    }

    @Test
    public void fromShouldCreateProperPrivacyModuleIfCurrentActivityNotConfigured() {
        // given
        final PrivacyModuleCreationContext creationContext = givenCreationContext(
                singletonList(1),
                givenConfig(singleton(7), null, Activity.SYNC_USER, mapper.createObjectNode()));

        // when
        final PrivacyModule privacyModule = target.from(creationContext);

        // then
        assertThat(privacyModule.proceed(null)).isEqualTo(Rule.Result.ABSTAIN);
        verifyNoInteractions(gppReaderFactory);
        verifyNoInteractions(jsonLogic);
        verifyNoInteractions(metrics);
    }

    @Test
    public void fromShouldCreateProperPrivacyModuleIfJsonLogicConfigIsNull() {
        // given
        final PrivacyModuleCreationContext creationContext = givenCreationContext(
                singletonList(1),
                givenConfig(singleton(7), null, Activity.CALL_BIDDER, null));

        // when
        final PrivacyModule privacyModule = target.from(creationContext);

        // then
        assertThat(privacyModule.proceed(null)).isEqualTo(Rule.Result.ABSTAIN);
        verifyNoInteractions(gppReaderFactory);
        verifyNoInteractions(jsonLogic);
        verifyNoInteractions(metrics);
    }

    @Test
    public void fromShouldUseDefaultValueForNormalizeSectionsIfItWasNull() throws JsonLogicEvaluationException {
        // given
        final PrivacyModuleCreationContext creationContext = givenCreationContext(
                singletonList(7),
                givenConfig(singleton(7), null, Activity.CALL_BIDDER, mapper.createObjectNode()));

        // when
        target.from(creationContext);

        // then
        verify(gppReaderFactory).forSection(eq(7), eq(true), any());
        verifyNoMoreInteractions(gppReaderFactory);

        verify(jsonLogic).parse(eq("{}"));
        verify(jsonLogic).evaluate(any(), any());
        verifyNoMoreInteractions(jsonLogic);
        verifyNoInteractions(metrics);
    }

    @Test
    public void fromShouldThrowExceptionAndEmitMetricsOnInvalidJsonLogicConfig() {
        // given
        given(jsonLogic.parse(any())).willThrow(new DecodeException("Test exception"));

        final PrivacyModuleCreationContext creationContext = givenCreationContext(
                singletonList(7),
                givenConfig(singleton(7), null, Activity.CALL_BIDDER, mapper.createObjectNode()));

        // when and then
        assertThatExceptionOfType(InvalidAccountConfigException.class).isThrownBy(() -> target.from(creationContext));

        verify(jsonLogic).parse(any());
        verify(metrics).updateAlertsMetrics(eq(MetricName.general));

        verifyNoInteractions(gppReaderFactory);
        verifyNoMoreInteractions(jsonLogic);
        verifyNoMoreInteractions(metrics);
    }

    private static PrivacyModuleCreationContext givenCreationContext(List<Integer> sectionsIds,
                                                                     AccountUSCustomLogicModuleConfig.Config config) {

        return PrivacyModuleCreationContext.of(
                Activity.CALL_BIDDER,
                AccountUSCustomLogicModuleConfig.of(true, config),
                GppContextCreator.from(null, sectionsIds).build().getGppContext());
    }

    private static AccountUSCustomLogicModuleConfig.Config givenConfig(Set<Integer> supportedSectionsIds,
                                                                       Boolean normalizeSections,
                                                                       Activity activity,
                                                                       ObjectNode jsonLogicConfig) {

        return AccountUSCustomLogicModuleConfig.Config.of(
                supportedSectionsIds,
                normalizeSections,
                singletonList(AccountUSCustomLogicModuleConfig.ActivityConfig.of(
                        singleton(activity),
                        jsonLogicConfig)));
    }
}
