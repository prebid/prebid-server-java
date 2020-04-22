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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

public class PurposeFourStrategyTest {

    private static final int PURPOSE_ID = 4;

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private FullEnforcePurposeStrategy fullEnforcePurposeStrategy;

    @Mock
    private BasicEnforcePurposeStrategy basicEnforcePurposeStrategy;

    @Mock
    private NoEnforcePurposeStrategy noEnforcePurposeStrategy;

    private PurposeFourStrategy target;

    @Mock
    private TCString tcString;

    @Before
    public void setUp() {
        target = new PurposeFourStrategy(fullEnforcePurposeStrategy, basicEnforcePurposeStrategy,
                noEnforcePurposeStrategy);
    }

    @Test
    public void allowShouldReturnExpectedValue() {
        // given
        final PrivacyEnforcementAction privacyEnforcementAction = PrivacyEnforcementAction.restrictAll();

        // when
        target.allow(privacyEnforcementAction);

        // then
        final PrivacyEnforcementAction expectedAction = PrivacyEnforcementAction.restrictAll();
        expectedAction.setRemoveUserBuyerUid(false);
        assertThat(privacyEnforcementAction).isEqualTo(expectedAction);
    }

    @Test
    public void getPurposeIdShouldReturnExpectedValue() {
        // when and then
        assertThat(target.getPurposeId()).isEqualTo(PURPOSE_ID);
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

        given(noEnforcePurposeStrategy.allowedByTypeStrategy(anyInt(), any(), any(), any(), anyBoolean()))
                .willReturn(Arrays.asList(vendorPermission1, vendorPermission2));

        // when
        final Collection<VendorPermission> result = target.processTypePurposeStrategy(tcString, purpose,
                vendorPermissionsWithGvl);

        // then
        final VendorPermission vendorPermission1Changed = VendorPermission.of(1, null, allowPurpose());
        final VendorPermission vendorPermission2Changed = VendorPermission.of(2, "b1", allowPurpose());
        final VendorPermission vendorPermission3Changed = VendorPermission.of(3, null,
                PrivacyEnforcementAction.restrictAll());
        assertThat(result).usingFieldByFieldElementComparator().isEqualTo(
                Arrays.asList(vendorPermission1Changed, vendorPermission2Changed, vendorPermission3Changed));

        verify(noEnforcePurposeStrategy).allowedByTypeStrategy(PURPOSE_ID, tcString,
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

        given(basicEnforcePurposeStrategy.allowedByTypeStrategy(anyInt(), any(), any(), any(), anyBoolean()))
                .willReturn(Arrays.asList(vendorPermission1, vendorPermission2));

        // when
        final Collection<VendorPermission> result = target.processTypePurposeStrategy(tcString, purpose,
                vendorPermissionsWithGvl);

        // then
        final VendorPermission vendorPermission1Changed = VendorPermission.of(1, null, allowPurpose());
        final VendorPermission vendorPermission2Changed = VendorPermission.of(2, "b1", allowPurpose());
        final VendorPermission vendorPermission3Changed = VendorPermission.of(3, null,
                PrivacyEnforcementAction.restrictAll());
        assertThat(result).usingFieldByFieldElementComparator().isEqualTo(
                Arrays.asList(vendorPermission1Changed, vendorPermission2Changed, vendorPermission3Changed));

        verify(basicEnforcePurposeStrategy).allowedByTypeStrategy(PURPOSE_ID, tcString,
                Arrays.asList(vendorPermissionWitGvl1, vendorPermissionWitGvl3),
                singletonList(vendorPermissionWitGvl2), false);
    }

    @Test
    public void processTypePurposeStrategyShouldPassEmptyListWithEnforcementsWhenAllBiddersAreExcluded() {
        // given
        final List<String> vendorExceptions = Arrays.asList("b1", "b2", "b3", "b5", "b7");
        final Purpose purpose = Purpose.of(EnforcePurpose.basic, null, vendorExceptions);
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

        given(basicEnforcePurposeStrategy.allowedByTypeStrategy(anyInt(), any(), any(), any(), anyBoolean()))
                .willReturn(vendorPermissions);

        // when
        final Collection<VendorPermission> result = target.processTypePurposeStrategy(tcString, purpose,
                vendorPermissionsWithGvl);

        // then
        final VendorPermission vendorPermission1Changed = VendorPermission.of(1, "b1", allowPurpose());
        final VendorPermission vendorPermission2Changed = VendorPermission.of(2, "b2", allowPurpose());
        final VendorPermission vendorPermission3Changed = VendorPermission.of(3, "b3", allowPurpose());
        assertThat(result).usingFieldByFieldElementComparator().isEqualTo(
                Arrays.asList(vendorPermission1Changed, vendorPermission2Changed, vendorPermission3Changed));

        verify(basicEnforcePurposeStrategy).allowedByTypeStrategy(PURPOSE_ID, tcString, emptyList(),
                vendorPermissionsWithGvl, true);
    }

    private static PrivacyEnforcementAction allowPurpose() {
        final PrivacyEnforcementAction privacyEnforcementAction = PrivacyEnforcementAction.restrictAll();
        privacyEnforcementAction.setRemoveUserBuyerUid(false);
        return privacyEnforcementAction;
    }
}
