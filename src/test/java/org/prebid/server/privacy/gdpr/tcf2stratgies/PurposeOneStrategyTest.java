package org.prebid.server.privacy.gdpr.tcf2stratgies;

import com.iabtcf.decoder.TCString;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.VendorPermission;
import org.prebid.server.privacy.gdpr.tcf2stratgies.typeStrategies.BasicTypeStrategy;
import org.prebid.server.settings.model.EnforcePurpose;
import org.prebid.server.settings.model.Purpose;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

public class PurposeOneStrategyTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private BasicTypeStrategy basicTypeStrategy;

    private PurposeOneStrategy target;

    @Mock
    private TCString tcString;

    @Before
    public void setUp() {
        target = new PurposeOneStrategy(basicTypeStrategy);
    }

    @Test
    public void allowShouldReturnExpectedValue() {
        // given
        final PrivacyEnforcementAction privacyEnforcementAction = PrivacyEnforcementAction.restrictAll();

        // when
        target.allow(privacyEnforcementAction);

        // then
        final PrivacyEnforcementAction expectedAction = PrivacyEnforcementAction.restrictAll();
        expectedAction.setBlockPixelSync(false);
        assertThat(privacyEnforcementAction).isEqualTo(expectedAction);
    }

    @Test
    public void getPurposeIdShouldReturnExpectedValue() {
        // when and then
        assertThat(target.getPurposeId()).isEqualTo(1);
    }

    @Test
    public void processTypePurposeStrategyShouldReturnAllAllowedActionWithoutAnyCheck() {
        // given
        final Purpose purpose = new Purpose(EnforcePurpose.no, true, Collections.emptyList());

        // when
        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final Collection<VendorPermission> result = target.processTypePurposeStrategy(tcString, purpose, singleton(vendorPermission));

        // then
        final PrivacyEnforcementAction expectedAction = PrivacyEnforcementAction.restrictAll();
        expectedAction.setBlockPixelSync(false);
        assertThat(result).containsOnly(VendorPermission.of(1, null, expectedAction));

        verifyZeroInteractions(basicTypeStrategy);
    }

    @Test
    public void processTypePurposeStrategyShouldPassListWithEnforcementsWhenEnforceVendorIsTrue() {
        // given
        final Purpose purpose = new Purpose(EnforcePurpose.base, true, singletonList("b1"));
        final VendorPermission vendorPermission1 = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission2 = VendorPermission.of(2, "b1", PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission3 = VendorPermission.of(3, null , PrivacyEnforcementAction.restrictAll());
        final List<VendorPermission> vendorPermissions = Arrays.asList(vendorPermission1, vendorPermission2, vendorPermission3);

        given(basicTypeStrategy.allowedByTypeStrategy(anyInt(), any(), any(), any())).willReturn(vendorPermissions);

        // when
        final Collection<VendorPermission> result = target.processTypePurposeStrategy(tcString, purpose, vendorPermissions);

        // then
        assertThat(result).isEqualTo(vendorPermissions);

        final List<VendorPermission> vendorGdprEnforced = Arrays.asList(vendorPermission1, vendorPermission3);
        final List<VendorPermission> vendorAndPurposeEnforced = singletonList(vendorPermission2);
        verify(basicTypeStrategy).allowedByTypeStrategy(1, tcString, vendorGdprEnforced, vendorAndPurposeEnforced);
    }

    @Test
    public void processTypePurposeStrategyShouldPassListWithEnforcementsWhenEnforceVendorIsFalse() {
        // given
        final Purpose purpose = new Purpose(EnforcePurpose.base, false, singletonList("b1"));
        final VendorPermission vendorPermission1 = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission2 = VendorPermission.of(2, "b1", PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission3 = VendorPermission.of(3, null , PrivacyEnforcementAction.restrictAll());
        final List<VendorPermission> vendorPermissions = Arrays.asList(vendorPermission1, vendorPermission2, vendorPermission3);

        given(basicTypeStrategy.allowedByTypeStrategy(anyInt(), any(), any(), any())).willReturn(vendorPermissions);

        // when
        final Collection<VendorPermission> result = target.processTypePurposeStrategy(tcString, purpose, vendorPermissions);

        // then
        assertThat(result).isEqualTo(vendorPermissions);

        final List<VendorPermission> vendorGdprEnforced = singletonList(vendorPermission2);
        final List<VendorPermission> vendorAndPurposeEnforced = Arrays.asList(vendorPermission1, vendorPermission3);
        verify(basicTypeStrategy).allowedByTypeStrategy(1, tcString, vendorGdprEnforced, vendorAndPurposeEnforced);
    }
}
