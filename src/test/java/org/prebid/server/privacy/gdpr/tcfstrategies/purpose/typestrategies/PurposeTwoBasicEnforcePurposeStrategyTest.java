package org.prebid.server.privacy.gdpr.tcfstrategies.purpose.typestrategies;

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
import org.prebid.server.privacy.gdpr.model.VendorPermissionWithGvl;
import org.prebid.server.privacy.gdpr.vendorlist.proto.VendorV2;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;

public class PurposeTwoBasicEnforcePurposeStrategyTest {

    private static final int PURPOSE_ID = 1;

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private PurposeTwoBasicEnforcePurposeStrategy target;

    @Mock
    private TCString tcString;
    @Mock
    private IntIterable allowedVendors;
    @Mock
    private IntIterable allowedVendorsLI;
    @Mock
    private IntIterable purposesConsent;
    @Mock
    private IntIterable purposesLI;

    @Before
    public void setUp() {
        given(tcString.getVendorConsent()).willReturn(allowedVendors);
        given(tcString.getVendorLegitimateInterest()).willReturn(allowedVendorsLI);
        given(tcString.getPurposesConsent()).willReturn(purposesConsent);
        given(tcString.getPurposesLITransparency()).willReturn(purposesLI);

        given(allowedVendors.contains(anyInt())).willReturn(false);
        given(allowedVendorsLI.contains(anyInt())).willReturn(false);
        given(purposesConsent.contains(anyInt())).willReturn(false);
        given(purposesLI.contains(anyInt())).willReturn(false);

        target = new PurposeTwoBasicEnforcePurposeStrategy();
    }

    @Test
    public void allowedByTypeStrategyShouldReturnEmptyListWhenVendorIsNotAllowedAndVendorIsNotEnforced() {
        // given
        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = VendorPermissionWithGvl.of(vendorPermission,
                VendorV2.empty(1));
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_ID, tcString,
                vendorPermissionWithGvls, emptyList(), false);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void allowedByTypeStrategyShouldReturnEmptyListWhenVendorIsNotAllowedAndVendorEnforced() {
        // given
        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = VendorPermissionWithGvl.of(vendorPermission,
                VendorV2.empty(1));
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_ID, tcString,
                vendorPermissionWithGvls, emptyList(), true);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void allowedByTypeStrategyShouldReturnExpectedValueWhenVendorIsAllowedAndVendorEnforced() {
        // given
        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = VendorPermissionWithGvl.of(vendorPermission,
                VendorV2.empty(1));
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        given(allowedVendors.contains(anyInt())).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_ID, tcString,
                vendorPermissionWithGvls, emptyList(), true);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void allowedByTypeStrategyShouldReturnExpectedValueWhenVendorIsAllowedAndVendorIsNotEnforced() {
        // given
        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = VendorPermissionWithGvl.of(vendorPermission,
                VendorV2.empty(1));
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        given(allowedVendors.contains(anyInt())).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_ID, tcString,
                vendorPermissionWithGvls, emptyList(), false);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void allowedByTypeStrategyShouldReturnExpectedValueWhenVendorLIIsAllowedAndVendorEnforced() {
        // given
        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = VendorPermissionWithGvl.of(vendorPermission,
                VendorV2.empty(1));
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        given(allowedVendorsLI.contains(anyInt())).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_ID, tcString,
                vendorPermissionWithGvls, emptyList(), true);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void allowedByTypeStrategyShouldReturnExpectedValueWhenVendorLIIsAllowedAndVendorIsNotEnforced() {
        // given
        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = VendorPermissionWithGvl.of(vendorPermission,
                VendorV2.empty(1));
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        given(allowedVendorsLI.contains(anyInt())).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_ID, tcString,
                vendorPermissionWithGvls, emptyList(), false);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void allowedByTypeStrategyShouldReturnExpectedValueWhenPurposeLIAndPurposeIsAllowedAndVendorIsNotEnforced() {
        // given
        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = VendorPermissionWithGvl.of(vendorPermission,
                VendorV2.empty(1));
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        given(purposesConsent.contains(anyInt())).willReturn(true);
        given(purposesLI.contains(anyInt())).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_ID, tcString,
                vendorPermissionWithGvls, emptyList(), false);

        // then
        assertThat(result).usingFieldByFieldElementComparator().containsOnly(vendorPermission);
    }

    @Test
    public void allowedByTypeStrategyShouldReturnExpectedValueWhenPurposeLIAndPurposeIsAllowedAndVendorEnforced() {
        // given
        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = VendorPermissionWithGvl.of(vendorPermission,
                VendorV2.empty(1));
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        given(purposesConsent.contains(anyInt())).willReturn(true);
        given(purposesLI.contains(anyInt())).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_ID, tcString,
                vendorPermissionWithGvls, emptyList(), true);

        // then
        assertThat(result).usingFieldByFieldElementComparator().containsOnly(vendorPermission);
    }

    @Test
    public void allowedByTypeStrategyShouldReturnExpectedValueWhenPurposeLIAndVendorIsAllowedAndVendorIsNotEnforced() {
        // given
        final VendorPermission vendorPermission1 = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission2 = VendorPermission.of(2, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl1 = VendorPermissionWithGvl.of(vendorPermission1,
                VendorV2.empty(1));
        final VendorPermissionWithGvl vendorPermissionWitGvl2 = VendorPermissionWithGvl.of(vendorPermission2,
                VendorV2.empty(2));
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = Arrays.asList(vendorPermissionWitGvl1,
                vendorPermissionWitGvl2);

        given(allowedVendors.contains(anyInt())).willReturn(true);
        given(purposesLI.contains(PURPOSE_ID)).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_ID, tcString,
                vendorPermissionWithGvls, emptyList(), false);

        // then
        assertThat(result).usingFieldByFieldElementComparator().containsOnly(vendorPermission1, vendorPermission2);
    }

    @Test
    public void allowedByTypeStrategyShouldReturnExpectedValueWhenPurposeAndVendorLIIsAllowedAndVendorIsNotEnforced() {
        // given
        final VendorPermission vendorPermission1 = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission2 = VendorPermission.of(2, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl1 = VendorPermissionWithGvl.of(vendorPermission1,
                VendorV2.empty(1));
        final VendorPermissionWithGvl vendorPermissionWitGvl2 = VendorPermissionWithGvl.of(vendorPermission2,
                VendorV2.empty(2));
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = Arrays.asList(vendorPermissionWitGvl1,
                vendorPermissionWitGvl2);

        given(allowedVendorsLI.contains(anyInt())).willReturn(true);
        given(purposesConsent.contains(PURPOSE_ID)).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_ID, tcString,
                vendorPermissionWithGvls, emptyList(), false);

        // then
        assertThat(result).usingFieldByFieldElementComparator().containsOnly(vendorPermission1, vendorPermission2);
    }

    @Test
    public void allowedByTypeStrategyShouldReturnExcludedVendors() {
        // given
        final VendorPermission vendorPermission1 = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission2 = VendorPermission.of(2, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl1 = VendorPermissionWithGvl.of(vendorPermission1,
                VendorV2.empty(1));
        final VendorPermissionWithGvl vendorPermissionWitGvl2 = VendorPermissionWithGvl.of(vendorPermission2,
                VendorV2.empty(2));

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_ID, tcString,
                singleton(vendorPermissionWitGvl1), singleton(vendorPermissionWitGvl2), true);

        // then
        assertThat(result).usingFieldByFieldElementComparator().containsOnly(vendorPermission2);
    }
}
