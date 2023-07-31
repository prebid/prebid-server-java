package org.prebid.server.activity.infrastructure.creator.privacy.usnat;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.activity.Activity;
import org.prebid.server.activity.infrastructure.creator.PrivacyModuleCreationContext;
import org.prebid.server.activity.infrastructure.privacy.PrivacyModule;
import org.prebid.server.activity.infrastructure.privacy.PrivacyModuleQualifier;
import org.prebid.server.activity.infrastructure.privacy.usnat.reader.USNationalGppReader;
import org.prebid.server.activity.infrastructure.rule.Rule;
import org.prebid.server.auction.gpp.model.GppContextCreator;
import org.prebid.server.settings.model.activity.privacy.AccountUSNatModuleConfig;

import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class USNatModuleCreatorTest {

    @org.junit.Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private USNatGppReaderFactory gppReaderFactory;

    private USNatModuleCreator target;

    @Before
    public void setUp() {
        given(gppReaderFactory.forSection(any(), any())).willReturn(new USNationalGppReader(null));

        target = new USNatModuleCreator(gppReaderFactory);
    }

    @Test
    public void qualifierShouldReturnExpectedResult() {
        // when and then
        assertThat(target.qualifier()).isEqualTo(PrivacyModuleQualifier.US_NAT);
    }

    @Test
    public void fromShouldCreateProperPrivacyModuleIfSectionsIdsIsNull() {
        // given
        final PrivacyModuleCreationContext creationContext = givenCreationContext(null, null);

        // when
        final PrivacyModule privacyModule = target.from(creationContext);

        // then
        assertThat(privacyModule.proceed(null)).isEqualTo(Rule.Result.ABSTAIN);
        verifyNoInteractions(gppReaderFactory);
    }

    @Test
    public void fromShouldCreateProperPrivacyModuleIfSectionsIdsIsEmpty() {
        // given
        final PrivacyModuleCreationContext creationContext = givenCreationContext(emptyList(), null);

        // when
        final PrivacyModule privacyModule = target.from(creationContext);

        // then
        assertThat(privacyModule.proceed(null)).isEqualTo(Rule.Result.ABSTAIN);
        verifyNoInteractions(gppReaderFactory);
    }

    @Test
    public void fromShouldCreateProperPrivacyModuleIfAllSectionsIdsSkipped() {
        // given
        final PrivacyModuleCreationContext creationContext = givenCreationContext(singletonList(1), null);

        // when
        final PrivacyModule privacyModule = target.from(creationContext);

        // then
        assertThat(privacyModule.proceed(null)).isEqualTo(Rule.Result.ABSTAIN);
        verifyNoInteractions(gppReaderFactory);
    }

    @Test
    public void fromShouldShouldSkipNotSupportedSectionsIds() {
        // given
        final PrivacyModuleCreationContext creationContext = givenCreationContext(
                asList(6, 7, 8, 9, 10, 11, 12, 13), null);

        // when
        target.from(creationContext);

        // then
        verify(gppReaderFactory).forSection(eq(7), any());
        verify(gppReaderFactory).forSection(eq(8), any());
        verify(gppReaderFactory).forSection(eq(9), any());
        verify(gppReaderFactory).forSection(eq(10), any());
        verify(gppReaderFactory).forSection(eq(11), any());
        verify(gppReaderFactory).forSection(eq(12), any());
        verifyNoMoreInteractions(gppReaderFactory);
    }

    @Test
    public void fromShouldShouldSkipConfiguredSectionsIds() {
        // given
        final PrivacyModuleCreationContext creationContext = givenCreationContext(asList(7, 8, 9), asList(8, 9));

        // when
        target.from(creationContext);

        // then
        verify(gppReaderFactory).forSection(eq(7), any());
        verifyNoMoreInteractions(gppReaderFactory);
    }

    private static PrivacyModuleCreationContext givenCreationContext(List<Integer> sectionsIds,
                                                                     List<Integer> skipSectionsIds) {

        return PrivacyModuleCreationContext.of(
                Activity.CALL_BIDDER,
                AccountUSNatModuleConfig.of(true, AccountUSNatModuleConfig.Config.of(skipSectionsIds)),
                GppContextCreator.from(null, sectionsIds).build().getGppContext());
    }
}
