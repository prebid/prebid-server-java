package org.prebid.server.privacy.gdpr.tcfstrategies.purpose.typestrategies;

import com.iabtcf.decoder.TCString;
import com.iabtcf.utils.IntIterable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.VendorPermission;
import org.prebid.server.privacy.gdpr.model.VendorPermissionWithGvl;
import org.prebid.server.privacy.gdpr.vendorlist.proto.PurposeCode;
import org.prebid.server.privacy.gdpr.vendorlist.proto.Vendor;

import java.util.List;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
public class BasicEnforcePurposeStrategyTest {

    private static final PurposeCode PURPOSE_CODE = PurposeCode.ONE;

    private BasicEnforcePurposeStrategy target;

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

    @BeforeEach
    public void setUp() {
        given(tcString.getVendorConsent()).willReturn(allowedVendors);
        given(tcString.getVendorLegitimateInterest()).willReturn(allowedVendorsLI);
        given(tcString.getPurposesConsent()).willReturn(purposesConsent);
        given(tcString.getPurposesLITransparency()).willReturn(purposesLI);

        given(allowedVendors.contains(anyInt())).willReturn(false);
        given(allowedVendorsLI.contains(anyInt())).willReturn(false);
        given(purposesConsent.contains(anyInt())).willReturn(false);
        given(purposesLI.contains(anyInt())).willReturn(false);

        target = new BasicEnforcePurposeStrategy();
    }

    @Test
    public void allowedByTypeStrategyShouldReturnEmptyListWhenVendorIsNotAllowedAndVendorIsNotEnforced() {
        // given
        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = withGvl(vendorPermission, Vendor.empty(1));
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        // when
        final Stream<VendorPermission> result = target.allowedByTypeStrategy(
                PURPOSE_CODE, tcString, vendorPermissionWithGvls, emptyList(), false);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void allowedByTypeStrategyShouldReturnEmptyListWhenVendorIsNotAllowedAndVendorEnforced() {
        // given
        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = withGvl(vendorPermission, Vendor.empty(1));
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        // when
        final Stream<VendorPermission> result = target.allowedByTypeStrategy(
                PURPOSE_CODE, tcString, vendorPermissionWithGvls, emptyList(), true);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void allowedByTypeStrategyShouldReturnExpectedValueWhenVendorIsAllowedAndVendorEnforced() {
        // given
        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = withGvl(vendorPermission, Vendor.empty(1));
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        given(allowedVendors.contains(anyInt())).willReturn(true);

        // when
        final Stream<VendorPermission> result = target.allowedByTypeStrategy(
                PURPOSE_CODE, tcString, vendorPermissionWithGvls, emptyList(), true);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void allowedByTypeStrategyShouldReturnExpectedValueWhenVendorIsAllowedAndVendorIsNotEnforced() {
        // given
        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = withGvl(vendorPermission, Vendor.empty(1));
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        given(allowedVendors.contains(anyInt())).willReturn(true);

        // when
        final Stream<VendorPermission> result = target.allowedByTypeStrategy(
                PURPOSE_CODE, tcString, vendorPermissionWithGvls, emptyList(), false);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void allowedByTypeStrategyShouldReturnExpectedValueWhenVendorLIIsAllowedAndVendorEnforced() {
        // given
        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = withGvl(vendorPermission, Vendor.empty(1));
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        given(allowedVendorsLI.contains(anyInt())).willReturn(true);

        // when
        final Stream<VendorPermission> result = target.allowedByTypeStrategy(
                PURPOSE_CODE, tcString, vendorPermissionWithGvls, emptyList(), true);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void allowedByTypeStrategyShouldReturnExpectedValueWhenVendorLIIsAllowedAndVendorIsNotEnforced() {
        // given
        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = withGvl(vendorPermission, Vendor.empty(1));
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        given(allowedVendorsLI.contains(anyInt())).willReturn(true);

        // when
        final Stream<VendorPermission> result = target.allowedByTypeStrategy(
                PURPOSE_CODE, tcString, vendorPermissionWithGvls, emptyList(), false);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void allowedByTypeStrategyShouldReturnExpectedValueWhenPurposeLIAndPurposeIsAllowedAndVendorIsNotEnforced() {
        // given
        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = withGvl(vendorPermission, Vendor.empty(1));
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        given(purposesConsent.contains(anyInt())).willReturn(true);
        given(purposesLI.contains(anyInt())).willReturn(true);

        // when
        final Stream<VendorPermission> result = target.allowedByTypeStrategy(
                PURPOSE_CODE, tcString, vendorPermissionWithGvls, emptyList(), false);

        // then
        assertThat(result).usingRecursiveFieldByFieldElementComparator().containsOnly(vendorPermission);
    }

    @Test
    public void allowedByTypeStrategyShouldReturnExpectedValueWhenPurposeLIAndPurposeIsAllowedAndVendorEnforced() {
        // given
        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = withGvl(vendorPermission, Vendor.empty(1));
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        given(purposesConsent.contains(anyInt())).willReturn(true);
        given(purposesLI.contains(anyInt())).willReturn(true);

        // when
        final Stream<VendorPermission> result = target.allowedByTypeStrategy(
                PURPOSE_CODE, tcString, vendorPermissionWithGvls, emptyList(), true);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void allowedByTypeStrategyShouldReturnExpectedValueWhenPurposeLIAndVendorIsAllowedAndVendorIsNotEnforced() {
        // given
        final VendorPermission vendorPermission1 = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission2 = VendorPermission.of(2, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl1 = withGvl(vendorPermission1, Vendor.empty(1));
        final VendorPermissionWithGvl vendorPermissionWitGvl2 = withGvl(vendorPermission2, Vendor.empty(2));
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = asList(
                vendorPermissionWitGvl1,
                vendorPermissionWitGvl2);

        given(allowedVendors.contains(anyInt())).willReturn(true);
        given(purposesLI.contains(PURPOSE_CODE.code())).willReturn(true);

        // when
        final Stream<VendorPermission> result = target.allowedByTypeStrategy(
                PURPOSE_CODE, tcString, vendorPermissionWithGvls, emptyList(), false);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void allowedByTypeStrategyShouldReturnExpectedValueWhenPurposeAndVendorLIIsAllowedAndVendorIsNotEnforced() {
        // given
        final VendorPermission vendorPermission1 = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission2 = VendorPermission.of(2, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl1 = withGvl(vendorPermission1, Vendor.empty(1));
        final VendorPermissionWithGvl vendorPermissionWitGvl2 = withGvl(vendorPermission2, Vendor.empty(2));
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = asList(
                vendorPermissionWitGvl1,
                vendorPermissionWitGvl2);

        given(allowedVendorsLI.contains(anyInt())).willReturn(true);
        given(purposesConsent.contains(PURPOSE_CODE.code())).willReturn(true);

        // when
        final Stream<VendorPermission> result = target.allowedByTypeStrategy(
                PURPOSE_CODE, tcString, vendorPermissionWithGvls, emptyList(), false);

        // then
        assertThat(result)
                .usingRecursiveFieldByFieldElementComparator()
                .containsOnly(vendorPermission1, vendorPermission2);
    }

    @Test
    public void allowedByTypeStrategyShouldReturnExcludedVendors() {
        // given
        final VendorPermission vendorPermission1 = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission2 = VendorPermission.of(2, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl1 = withGvl(vendorPermission1, Vendor.empty(1));
        final VendorPermissionWithGvl vendorPermissionWitGvl2 = withGvl(vendorPermission2, Vendor.empty(2));

        // when
        final Stream<VendorPermission> result = target.allowedByTypeStrategy(
                PURPOSE_CODE,
                tcString,
                singleton(vendorPermissionWitGvl1),
                singleton(vendorPermissionWitGvl2),
                true);

        // then
        assertThat(result).usingRecursiveFieldByFieldElementComparator().containsOnly(vendorPermission2);
    }

    private static VendorPermissionWithGvl withGvl(VendorPermission vendorPermission, Vendor vendor) {
        return VendorPermissionWithGvl.of(vendorPermission, vendor);
    }
}
