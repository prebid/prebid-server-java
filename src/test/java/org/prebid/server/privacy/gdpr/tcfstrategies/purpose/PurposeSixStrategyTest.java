package org.prebid.server.privacy.gdpr.tcfstrategies.purpose;

import com.iabtcf.decoder.TCString;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.VendorPermission;
import org.prebid.server.privacy.gdpr.model.VendorPermissionWithGvl;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.typestrategies.BasicEnforcePurposeStrategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.typestrategies.FullEnforcePurposeStrategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.typestrategies.NoEnforcePurposeStrategy;
import org.prebid.server.privacy.gdpr.vendorlist.proto.VendorV2;
import org.prebid.server.settings.model.EnforcePurpose;
import org.prebid.server.settings.model.Purpose;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class PurposeSixStrategyTest {

    private static final org.prebid.server.privacy.gdpr.vendorlist.proto.Purpose PURPOSE =
            org.prebid.server.privacy.gdpr.vendorlist.proto.Purpose.SIX;

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private FullEnforcePurposeStrategy fullEnforcePurposeStrategy;

    @Mock
    private BasicEnforcePurposeStrategy basicEnforcePurposeStrategy;

    @Mock
    private NoEnforcePurposeStrategy noEnforcePurposeStrategy;

    private PurposeSixStrategy target;

    @Mock
    private TCString tcString;

    @Before
    public void setUp() {
        target = new PurposeSixStrategy(fullEnforcePurposeStrategy, basicEnforcePurposeStrategy,
                noEnforcePurposeStrategy);
    }

    @Test
    public void allowShouldReturnExpectedValue() {
        // given
        final PrivacyEnforcementAction privacyEnforcementAction = PrivacyEnforcementAction.restrictAll();

        // when
        target.allow(privacyEnforcementAction);

        // then
        assertThat(privacyEnforcementAction).isEqualToComparingFieldByField(allowPurpose());
    }

    @Test
    public void allowNaturallyShouldReturnExpectedValue() {
        // given
        final PrivacyEnforcementAction privacyEnforcementAction = PrivacyEnforcementAction.restrictAll();

        // when
        target.allowNaturally(privacyEnforcementAction);

        // then
        assertThat(privacyEnforcementAction).isEqualToComparingFieldByField(allowNatural());
    }

    @Test
    public void getPurposeIdShouldReturnExpectedValue() {
        // when and then
        assertThat(target.getPurpose()).isEqualTo(PURPOSE);
    }

    @Test
    public void processTypePurposeStrategyShouldPassListWithEnforcementsAndExcludeBiddersToNoType() {
        // given
        final List<String> vendorExceptions = Arrays.asList("b1", "b3");
        final Purpose purpose = Purpose.of(EnforcePurpose.no, false, vendorExceptions);
        final VendorPermission vendorPermission1 = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission2 = VendorPermission.of(2, "b1", PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission3 = VendorPermission.of(3, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl1 = VendorPermissionWithGvl.of(vendorPermission1,
                VendorV2.empty(1));
        final VendorPermissionWithGvl vendorPermissionWitGvl2 = VendorPermissionWithGvl.of(vendorPermission2,
                VendorV2.empty(2));
        final VendorPermissionWithGvl vendorPermissionWitGvl3 = VendorPermissionWithGvl.of(vendorPermission3,
                VendorV2.empty(3));
        final List<VendorPermissionWithGvl> vendorPermissionsWithGvl = Arrays.asList(vendorPermissionWitGvl1,
                vendorPermissionWitGvl2, vendorPermissionWitGvl3);

        given(noEnforcePurposeStrategy.allowedByTypeStrategy(any(), any(), any(), any(), anyBoolean()))
                .willReturn(Arrays.asList(vendorPermission1, vendorPermission2));

        // when
        final Collection<VendorPermission> result = target.processTypePurposeStrategy(tcString, purpose,
                vendorPermissionsWithGvl, false);

        // then
        final VendorPermission vendorPermission1Changed = VendorPermission.of(1, null, allowPurpose());
        final VendorPermission vendorPermission2Changed = VendorPermission.of(2, "b1", allowPurpose());
        final VendorPermission vendorPermission3Changed = VendorPermission.of(3, null,
                PrivacyEnforcementAction.restrictAll());
        assertThat(result).usingFieldByFieldElementComparator().isEqualTo(
                Arrays.asList(vendorPermission1Changed, vendorPermission2Changed, vendorPermission3Changed));

        verify(noEnforcePurposeStrategy).allowedByTypeStrategy(PURPOSE, tcString,
                Arrays.asList(vendorPermissionWitGvl1, vendorPermissionWitGvl3), singletonList(vendorPermissionWitGvl2),
                false);
    }

    @Test
    public void processTypePurposeStrategyShouldPassListWithEnforcementsAndExcludeBiddersToBaseType() {
        // given
        final List<String> vendorExceptions = Arrays.asList("b1", "b3");
        final Purpose purpose = Purpose.of(EnforcePurpose.basic, false, vendorExceptions);
        final VendorPermission vendorPermission1 = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission2 = VendorPermission.of(2, "b1", PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission3 = VendorPermission.of(3, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl1 = VendorPermissionWithGvl.of(vendorPermission1,
                VendorV2.empty(1));
        final VendorPermissionWithGvl vendorPermissionWitGvl2 = VendorPermissionWithGvl.of(vendorPermission2,
                VendorV2.empty(2));
        final VendorPermissionWithGvl vendorPermissionWitGvl3 = VendorPermissionWithGvl.of(vendorPermission3,
                VendorV2.empty(3));
        final List<VendorPermissionWithGvl> vendorPermissionsWithGvl = Arrays.asList(vendorPermissionWitGvl1,
                vendorPermissionWitGvl2, vendorPermissionWitGvl3);
        given(basicEnforcePurposeStrategy.allowedByTypeStrategy(any(), any(), any(), any(), anyBoolean()))
                .willReturn(Arrays.asList(vendorPermission1, vendorPermission2));

        // when
        final Collection<VendorPermission> result = target.processTypePurposeStrategy(tcString, purpose,
                vendorPermissionsWithGvl, false);

        // then
        final VendorPermission vendorPermission1Changed = VendorPermission.of(1, null, allowPurpose());
        final VendorPermission vendorPermission2Changed = VendorPermission.of(2, "b1", allowPurpose());
        final VendorPermission vendorPermission3Changed = VendorPermission.of(3, null,
                PrivacyEnforcementAction.restrictAll());
        assertThat(result).usingFieldByFieldElementComparator().isEqualTo(
                Arrays.asList(vendorPermission1Changed, vendorPermission2Changed, vendorPermission3Changed));

        verify(basicEnforcePurposeStrategy).allowedByTypeStrategy(PURPOSE, tcString,
                Arrays.asList(vendorPermissionWitGvl1, vendorPermissionWitGvl3), singletonList(vendorPermissionWitGvl2),
                false);
    }

    @Test
    public void processTypePurposeStrategyShouldPassListWithEnforcementsAndExcludeBiddersToFullType() {
        // given
        final Purpose purpose = Purpose.of(EnforcePurpose.basic, null, Arrays.asList("b1", "b2", "b3", "b5", "b7"));
        final VendorPermission vendorPermission1 = VendorPermission.of(1, "b1", PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission2 = VendorPermission.of(2, "b2", PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission3 = VendorPermission.of(3, "b3", PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl1 = VendorPermissionWithGvl.of(vendorPermission1,
                VendorV2.empty(1));
        final VendorPermissionWithGvl vendorPermissionWitGvl2 = VendorPermissionWithGvl.of(vendorPermission2,
                VendorV2.empty(2));
        final VendorPermissionWithGvl vendorPermissionWitGvl3 = VendorPermissionWithGvl.of(vendorPermission3,
                VendorV2.empty(3));
        final List<VendorPermission> vendorPermissions = Arrays.asList(vendorPermission1, vendorPermission2,
                vendorPermission3);
        final List<VendorPermissionWithGvl> vendorPermissionsWithGvl = Arrays.asList(vendorPermissionWitGvl1,
                vendorPermissionWitGvl2, vendorPermissionWitGvl3);

        given(basicEnforcePurposeStrategy.allowedByTypeStrategy(any(), any(), any(), any(), anyBoolean()))
                .willReturn(vendorPermissions);

        // when
        final Collection<VendorPermission> result = target.processTypePurposeStrategy(tcString, purpose,
                vendorPermissionsWithGvl, false);

        // then
        final VendorPermission vendorPermission1Changed = VendorPermission.of(1, "b1", allowPurpose());
        final VendorPermission vendorPermission2Changed = VendorPermission.of(2, "b2", allowPurpose());
        final VendorPermission vendorPermission3Changed = VendorPermission.of(3, "b3", allowPurpose());
        assertThat(result).usingFieldByFieldElementComparator().isEqualTo(
                Arrays.asList(vendorPermission1Changed, vendorPermission2Changed, vendorPermission3Changed));

        verify(basicEnforcePurposeStrategy).allowedByTypeStrategy(PURPOSE, tcString, emptyList(),
                vendorPermissionsWithGvl, true);
    }

    @Test
    public void processTypePurposeStrategyShouldPassEmptyListWithFullEnforcementsWhenAllBiddersAreExcluded() {
        // given
        final Purpose purpose = Purpose.of(EnforcePurpose.full, null, Arrays.asList("b1", "b2", "b3", "b5", "b7"));
        final VendorPermission vendorPermission1 = VendorPermission.of(1, "b1", PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission2 = VendorPermission.of(2, "b2", PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission3 = VendorPermission.of(3, "b3", PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl1 = VendorPermissionWithGvl.of(vendorPermission1,
                VendorV2.empty(1));
        final VendorPermissionWithGvl vendorPermissionWitGvl2 = VendorPermissionWithGvl.of(vendorPermission2,
                VendorV2.empty(2));
        final VendorPermissionWithGvl vendorPermissionWitGvl3 = VendorPermissionWithGvl.of(vendorPermission3,
                VendorV2.empty(3));
        final List<VendorPermission> vendorPermissions = Arrays.asList(vendorPermission1, vendorPermission2,
                vendorPermission3);
        final List<VendorPermissionWithGvl> vendorPermissionsWithGvl = Arrays.asList(vendorPermissionWitGvl1,
                vendorPermissionWitGvl2, vendorPermissionWitGvl3);

        given(fullEnforcePurposeStrategy.allowedByTypeStrategy(any(), any(), any(), any(), anyBoolean()))
                .willReturn(vendorPermissions);

        // when
        final Collection<VendorPermission> result = target.processTypePurposeStrategy(tcString, purpose,
                vendorPermissionsWithGvl, false);

        // then
        final VendorPermission vendorPermission1Changed = VendorPermission.of(1, "b1", allowPurposeAndNaturally());
        final VendorPermission vendorPermission2Changed = VendorPermission.of(2, "b2", allowPurposeAndNaturally());
        final VendorPermission vendorPermission3Changed = VendorPermission.of(3, "b3", allowPurposeAndNaturally());
        assertThat(result).usingFieldByFieldElementComparator().isEqualTo(
                Arrays.asList(vendorPermission1Changed, vendorPermission2Changed, vendorPermission3Changed));

        verify(fullEnforcePurposeStrategy, times(2)).allowedByTypeStrategy(PURPOSE, tcString, emptyList(),
                vendorPermissionsWithGvl, true);
    }

    @Test
    public void processTypePurposeStrategyShouldAllowPurposeAndNaturallyVendorExceptions() {
        // given
        final Purpose purpose = Purpose.of(EnforcePurpose.full, null, Arrays.asList("b1", "b2"));
        final VendorPermission vendorPermission1 = VendorPermission.of(1, "b1", PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission2 = VendorPermission.of(2, "b2", PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission3 = VendorPermission.of(3, "b3", PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl1 = VendorPermissionWithGvl.of(vendorPermission1,
                VendorV2.empty(1));
        final VendorPermissionWithGvl vendorPermissionWitGvl2 = VendorPermissionWithGvl.of(vendorPermission2,
                VendorV2.empty(2));
        final VendorPermissionWithGvl vendorPermissionWitGvl3 = VendorPermissionWithGvl.of(vendorPermission3,
                VendorV2.empty(3));
        final List<VendorPermission> vendorPermissions = Arrays.asList(vendorPermission1, vendorPermission2,
                vendorPermission3);
        final List<VendorPermission> excludedVendorPermissions = Arrays.asList(vendorPermission1, vendorPermission2);

        given(fullEnforcePurposeStrategy.allowedByTypeStrategy(any(), any(), any(), any(), anyBoolean()))
                .willReturn(vendorPermissions)
                .willReturn(excludedVendorPermissions);

        final List<VendorPermissionWithGvl> vendorPermissionsWithGvl = Arrays.asList(vendorPermissionWitGvl1,
                vendorPermissionWitGvl2, vendorPermissionWitGvl3);

        // when
        final Collection<VendorPermission> result = target.processTypePurposeStrategy(tcString, purpose,
                vendorPermissionsWithGvl, false);

        // then
        final List<VendorPermissionWithGvl> excludedVendorPermissionsWithGvl = Arrays.asList(vendorPermissionWitGvl1,
                vendorPermissionWitGvl2);

        final VendorPermission vendorPermission1Changed = VendorPermission.of(1, "b1", allowPurposeAndNaturally());
        final VendorPermission vendorPermission2Changed = VendorPermission.of(2, "b2", allowPurposeAndNaturally());
        final VendorPermission vendorPermission3Changed = VendorPermission.of(3, "b3", allowPurpose());
        assertThat(result).usingFieldByFieldElementComparator().isEqualTo(
                Arrays.asList(vendorPermission1Changed, vendorPermission2Changed, vendorPermission3Changed));

        verify(fullEnforcePurposeStrategy, times(2)).allowedByTypeStrategy(PURPOSE, tcString,
                singletonList(vendorPermissionWitGvl3), excludedVendorPermissionsWithGvl, true);
    }

    @Test
    public void processTypePurposeStrategyShouldAllowPurposeAndNaturallyWhenVendorPermissionsReturnedForDowngraded() {
        // given
        final Purpose purpose = Purpose.of(EnforcePurpose.no, null, Arrays.asList("b1", "b2"));
        final VendorPermission vendorPermission1 = VendorPermission.of(1, "b1", PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission2 = VendorPermission.of(2, "b2", PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission3 = VendorPermission.of(3, "b3", PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl1 = VendorPermissionWithGvl.of(vendorPermission1,
                VendorV2.empty(1));
        final VendorPermissionWithGvl vendorPermissionWitGvl2 = VendorPermissionWithGvl.of(vendorPermission2,
                VendorV2.empty(2));
        final VendorPermissionWithGvl vendorPermissionWitGvl3 = VendorPermissionWithGvl.of(vendorPermission3,
                VendorV2.empty(3));
        final List<VendorPermission> vendorPermissions = Arrays.asList(vendorPermission1, vendorPermission2,
                vendorPermission3);

        final List<VendorPermission> excludedVendorPermissions = Arrays.asList(vendorPermission1, vendorPermission2);

        given(noEnforcePurposeStrategy.allowedByTypeStrategy(any(), any(), any(), any(), anyBoolean()))
                .willReturn(vendorPermissions);
        given(basicEnforcePurposeStrategy.allowedByTypeStrategy(any(), any(), any(), any(), anyBoolean()))
                .willReturn(excludedVendorPermissions);

        final List<VendorPermissionWithGvl> vendorPermissionsWithGvl = Arrays.asList(vendorPermissionWitGvl1,
                vendorPermissionWitGvl2, vendorPermissionWitGvl3);

        // when
        final Collection<VendorPermission> result = target.processTypePurposeStrategy(tcString, purpose,
                vendorPermissionsWithGvl, true);

        // then
        final List<VendorPermissionWithGvl> excludedVendorPermissionsWithGvl = Arrays.asList(vendorPermissionWitGvl1,
                vendorPermissionWitGvl2);

        final VendorPermission vendorPermission1Changed = VendorPermission.of(1, "b1", allowPurposeAndNaturally());
        final VendorPermission vendorPermission2Changed = VendorPermission.of(2, "b2", allowPurposeAndNaturally());
        final VendorPermission vendorPermission3Changed = VendorPermission.of(3, "b3", allowPurpose());
        assertThat(result).usingFieldByFieldElementComparator().isEqualTo(
                Arrays.asList(vendorPermission1Changed, vendorPermission2Changed, vendorPermission3Changed));

        verify(noEnforcePurposeStrategy).allowedByTypeStrategy(PURPOSE, tcString,
                singletonList(vendorPermissionWitGvl3), excludedVendorPermissionsWithGvl, true);
        verify(basicEnforcePurposeStrategy).allowedByTypeStrategy(PURPOSE, tcString,
                singletonList(vendorPermissionWitGvl3), excludedVendorPermissionsWithGvl, true);
    }

    private static PrivacyEnforcementAction allowPurposeAndNaturally() {
        return allowNatural(allowPurpose());
    }

    private static PrivacyEnforcementAction allowPurpose() {
        return PrivacyEnforcementAction.restrictAll();
    }

    private static PrivacyEnforcementAction allowNatural() {
        return allowNatural(PrivacyEnforcementAction.restrictAll());
    }

    private static PrivacyEnforcementAction allowNatural(PrivacyEnforcementAction privacyEnforcementAction) {
        privacyEnforcementAction.setRemoveUserIds(false);
        privacyEnforcementAction.setMaskDeviceInfo(false);
        return privacyEnforcementAction;
    }
}
