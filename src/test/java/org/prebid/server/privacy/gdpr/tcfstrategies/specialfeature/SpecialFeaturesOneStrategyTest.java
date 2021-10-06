package org.prebid.server.privacy.gdpr.tcfstrategies.specialfeature;

import com.iabtcf.decoder.TCString;
import com.iabtcf.utils.IntIterable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.VendorPermission;
import org.prebid.server.settings.model.SpecialFeature;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

public class SpecialFeaturesOneStrategyTest {

    private static final int SPECIAL_FEATURE_ID = 1;

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private SpecialFeaturesOneStrategy target;

    @Mock
    private TCString tcString;
    @Mock
    private IntIterable specialFeatureOptIns;

    @Before
    public void setUp() {
        given(tcString.getSpecialFeatureOptIns()).willReturn(specialFeatureOptIns);
        given(specialFeatureOptIns.contains(anyInt())).willReturn(false);

        target = new SpecialFeaturesOneStrategy();
    }

    @Test
    public void allowShouldReturnExpectedValue() {
        // given
        final PrivacyEnforcementAction privacyEnforcementAction = PrivacyEnforcementAction.restrictAll();

        // when
        target.allow(privacyEnforcementAction);

        // then
        assertThat(privacyEnforcementAction).isEqualTo(allowSpecialFeature());
    }

    @Test
    public void getSpecialFeatureIdShouldReturnExpectedValue() {
        // when and then
        assertThat(target.getSpecialFeatureId()).isEqualTo(SPECIAL_FEATURE_ID);
    }

    @Test
    public void processSpecialFeaturesStrategyShouldAllowForAllWhenIsNotEnforced() {
        // given
        final VendorPermission vendorPermission1 = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission2 = VendorPermission.of(2, "b1", PrivacyEnforcementAction.restrictAll());
        final List<VendorPermission> vendorPermissions = Arrays.asList(vendorPermission1, vendorPermission2);

        final SpecialFeature specialFeature = SpecialFeature.of(false, emptyList());

        // when
        final Collection<VendorPermission> result = target.processSpecialFeaturesStrategy(tcString, specialFeature,
                vendorPermissions);

        // then
        final VendorPermission vendorPermission1Changed = VendorPermission.of(1, null, allowSpecialFeature());
        final VendorPermission vendorPermission2Changed = VendorPermission.of(2, "b1", allowSpecialFeature());
        assertThat(result).usingFieldByFieldElementComparator().containsOnly(vendorPermission1Changed,
                vendorPermission2Changed);

        verifyNoInteractions(specialFeatureOptIns);
    }

    @Test
    public void processSpecialFeaturesStrategyShouldAllowEmptyListWhenAllOptOutAndNoExcluded() {
        // given
        final VendorPermission vendorPermission1 = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission2 = VendorPermission.of(2, "b1", PrivacyEnforcementAction.restrictAll());
        final List<VendorPermission> vendorPermissions = Arrays.asList(vendorPermission1, vendorPermission2);

        final SpecialFeature specialFeature = SpecialFeature.of(true, emptyList());

        // when
        final Collection<VendorPermission> result = target.processSpecialFeaturesStrategy(tcString, specialFeature,
                vendorPermissions);

        // then
        assertThat(result).usingFieldByFieldElementComparator().containsOnly(vendorPermission1, vendorPermission2);

        verify(specialFeatureOptIns).contains(SPECIAL_FEATURE_ID);
    }

    @Test
    public void processSpecialFeaturesStrategyShouldAllowOnlyExcludedWhenAllOptOutAndExcluded() {
        // given
        final VendorPermission vendorPermission1 = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission2 = VendorPermission.of(2, "b1", PrivacyEnforcementAction.restrictAll());
        final List<VendorPermission> vendorPermissions = Arrays.asList(vendorPermission1, vendorPermission2);

        final SpecialFeature specialFeature = SpecialFeature.of(true, singletonList("b1"));

        // when
        final Collection<VendorPermission> result = target.processSpecialFeaturesStrategy(tcString, specialFeature,
                vendorPermissions);

        // then
        final VendorPermission vendorPermission2Changed = VendorPermission.of(2, "b1", allowSpecialFeature());
        assertThat(result).usingFieldByFieldElementComparator().containsOnly(vendorPermission1,
                vendorPermission2Changed);

        verify(specialFeatureOptIns).contains(SPECIAL_FEATURE_ID);
    }

    @Test
    public void processSpecialFeaturesStrategyShouldAllowExcludedAndOptIn() {
        // given
        final VendorPermission vendorPermission1 = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission2 = VendorPermission.of(2, "b1", PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission3 = VendorPermission.of(3, "b3", PrivacyEnforcementAction.restrictAll());
        final List<VendorPermission> vendorPermissions = Arrays.asList(vendorPermission1, vendorPermission2,
                vendorPermission3);

        final SpecialFeature specialFeature = SpecialFeature.of(true, singletonList("b1"));

        given(specialFeatureOptIns.contains(SPECIAL_FEATURE_ID)).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.processSpecialFeaturesStrategy(tcString, specialFeature,
                vendorPermissions);

        // then
        final VendorPermission vendorPermission1Changed = VendorPermission.of(1, null, allowSpecialFeature());
        final VendorPermission vendorPermission2Changed = VendorPermission.of(2, "b1", allowSpecialFeature());
        final VendorPermission vendorPermission3Changed = VendorPermission.of(3, "b3", allowSpecialFeature());
        assertThat(result).usingFieldByFieldElementComparator().containsOnly(vendorPermission1Changed,
                vendorPermission2Changed, vendorPermission3Changed);

        verify(specialFeatureOptIns).contains(SPECIAL_FEATURE_ID);
    }

    private static PrivacyEnforcementAction allowSpecialFeature() {
        final PrivacyEnforcementAction privacyEnforcementAction = PrivacyEnforcementAction.restrictAll();
        privacyEnforcementAction.setMaskDeviceIp(false);
        privacyEnforcementAction.setMaskGeo(false);
        return privacyEnforcementAction;
    }
}
