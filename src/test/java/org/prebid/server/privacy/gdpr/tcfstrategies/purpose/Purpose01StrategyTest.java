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
import org.prebid.server.privacy.gdpr.vendorlist.proto.PurposeCode;
import org.prebid.server.privacy.gdpr.vendorlist.proto.Vendor;
import org.prebid.server.settings.model.EnforcePurpose;
import org.prebid.server.settings.model.Purpose;

import java.util.List;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class Purpose01StrategyTest {

    private static final PurposeCode PURPOSE_CODE = PurposeCode.ONE;

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private FullEnforcePurposeStrategy fullEnforcePurposeStrategy;

    @Mock
    private BasicEnforcePurposeStrategy basicEnforcePurposeStrategy;

    @Mock
    private NoEnforcePurposeStrategy noEnforcePurposeStrategy;

    private Purpose01Strategy target;

    @Mock
    private TCString tcString;

    @Before
    public void setUp() {
        target = new Purpose01Strategy(
                fullEnforcePurposeStrategy,
                basicEnforcePurposeStrategy,
                noEnforcePurposeStrategy);
    }

    @Test
    public void allowShouldReturnExpectedValue() {
        // given
        final PrivacyEnforcementAction privacyEnforcementAction = PrivacyEnforcementAction.restrictAll();

        // when
        target.allow(privacyEnforcementAction);

        // then
        assertThat(privacyEnforcementAction).usingRecursiveComparison().isEqualTo(allowPurpose());
    }

    @Test
    public void allowNaturallyShouldReturnExpectedValue() {
        // given
        final PrivacyEnforcementAction privacyEnforcementAction = PrivacyEnforcementAction.restrictAll();

        // when
        target.allowNaturally(privacyEnforcementAction);

        // then
        assertThat(privacyEnforcementAction)
                .usingRecursiveComparison()
                .isEqualTo(PrivacyEnforcementAction.restrictAll());
    }

    @Test
    public void getPurposeIdShouldReturnExpectedValue() {
        // when and then
        assertThat(target.getPurpose()).isEqualTo(PURPOSE_CODE);
    }

    @Test
    public void processTypePurposeStrategyShouldPassListWithEnforcementsAndExcludeBiddersToNoType() {
        // given
        final List<String> vendorExceptions = asList("b1", "b3");
        final Purpose purpose = Purpose.of(EnforcePurpose.no, false, vendorExceptions, null);
        final VendorPermission vendorPermission1 = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission2 = VendorPermission.of(2, "b1", PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission3 = VendorPermission.of(3, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl1 = withGvl(vendorPermission1, Vendor.empty(1));
        final VendorPermissionWithGvl vendorPermissionWitGvl2 = withGvl(vendorPermission2, Vendor.empty(2));
        final VendorPermissionWithGvl vendorPermissionWitGvl3 = withGvl(vendorPermission3, Vendor.empty(3));
        final List<VendorPermissionWithGvl> vendorPermissionsWithGvl = asList(
                vendorPermissionWitGvl1,
                vendorPermissionWitGvl2,
                vendorPermissionWitGvl3);

        given(noEnforcePurposeStrategy.allowedByTypeStrategy(any(), any(), any(), any(), anyBoolean()))
                .willReturn(Stream.of(vendorPermission1, vendorPermission2));

        // when
        target.processTypePurposeStrategy(tcString, purpose, vendorPermissionsWithGvl, false);

        // then
        assertThat(vendorPermission1).isEqualTo(vendorPermissionResult(1, null, allowPurpose()));
        assertThat(vendorPermission2).isEqualTo(vendorPermissionResult(2, "b1", allowPurpose()));
        assertThat(vendorPermission3).isEqualTo(VendorPermission.of(3, null, PrivacyEnforcementAction.restrictAll()));

        verify(noEnforcePurposeStrategy)
                .allowedByTypeStrategy(
                        PURPOSE_CODE,
                        tcString,
                        asList(vendorPermissionWitGvl1, vendorPermissionWitGvl3),
                        singletonList(vendorPermissionWitGvl2),
                        false);
    }

    @Test
    public void processTypePurposeStrategyShouldPassListWithEnforcementsAndExcludeBiddersToBaseType() {
        // given
        final List<String> vendorExceptions = asList("b1", "b3");
        final Purpose purpose = Purpose.of(EnforcePurpose.basic, false, vendorExceptions, null);
        final VendorPermission vendorPermission1 = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission2 = VendorPermission.of(2, "b1", PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission3 = VendorPermission.of(3, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl1 = withGvl(vendorPermission1, Vendor.empty(1));
        final VendorPermissionWithGvl vendorPermissionWitGvl2 = withGvl(vendorPermission2, Vendor.empty(2));
        final VendorPermissionWithGvl vendorPermissionWitGvl3 = withGvl(vendorPermission3, Vendor.empty(3));
        final List<VendorPermissionWithGvl> vendorPermissionsWithGvl = asList(
                vendorPermissionWitGvl1,
                vendorPermissionWitGvl2,
                vendorPermissionWitGvl3);

        given(basicEnforcePurposeStrategy.allowedByTypeStrategy(any(), any(), any(), any(), anyBoolean()))
                .willReturn(Stream.of(vendorPermission1, vendorPermission2));

        // when
        target.processTypePurposeStrategy(tcString, purpose, vendorPermissionsWithGvl, false);

        // then
        assertThat(vendorPermission1).isEqualTo(vendorPermissionResult(1, null, allowPurpose()));
        assertThat(vendorPermission2).isEqualTo(vendorPermissionResult(2, "b1", allowPurpose()));
        assertThat(vendorPermission3).isEqualTo(VendorPermission.of(3, null, PrivacyEnforcementAction.restrictAll()));

        verify(basicEnforcePurposeStrategy)
                .allowedByTypeStrategy(
                        PURPOSE_CODE,
                        tcString,
                        asList(vendorPermissionWitGvl1, vendorPermissionWitGvl3),
                        singletonList(vendorPermissionWitGvl2),
                        false);
    }

    @Test
    public void processTypePurposeStrategyShouldPassEmptyListWithEnforcementsWhenAllBiddersAreExcluded() {
        // given
        final List<String> vendorExceptions = asList("b1", "b2", "b3", "b5", "b7");
        final Purpose purpose = Purpose.of(EnforcePurpose.basic, null, vendorExceptions, null);
        final VendorPermission vendorPermission1 = VendorPermission.of(1, "b1", PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission2 = VendorPermission.of(2, "b2", PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission3 = VendorPermission.of(3, "b3", PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl1 = withGvl(vendorPermission1, Vendor.empty(1));
        final VendorPermissionWithGvl vendorPermissionWitGvl2 = withGvl(vendorPermission2, Vendor.empty(2));
        final VendorPermissionWithGvl vendorPermissionWitGvl3 = withGvl(vendorPermission3, Vendor.empty(3));
        final List<VendorPermissionWithGvl> vendorPermissionsWithGvl = asList(
                vendorPermissionWitGvl1,
                vendorPermissionWitGvl2,
                vendorPermissionWitGvl3);

        given(basicEnforcePurposeStrategy.allowedByTypeStrategy(any(), any(), any(), any(), anyBoolean()))
                .willReturn(Stream.of(vendorPermission1, vendorPermission2, vendorPermission3));

        // when
        target.processTypePurposeStrategy(tcString, purpose, vendorPermissionsWithGvl, false);

        // then
        assertThat(vendorPermission1).isEqualTo(vendorPermissionResult(1, "b1", allowPurpose()));
        assertThat(vendorPermission2).isEqualTo(vendorPermissionResult(2, "b2", allowPurpose()));
        assertThat(vendorPermission3).isEqualTo(vendorPermissionResult(3, "b3", allowPurpose()));

        verify(basicEnforcePurposeStrategy)
                .allowedByTypeStrategy(PURPOSE_CODE, tcString, emptyList(), vendorPermissionsWithGvl, true);
    }

    @Test
    public void processTypePurposeStrategyShouldPassEmptyListWithFullEnforcementsWhenAllBiddersAreExcluded() {
        // given
        final Purpose purpose = Purpose.of(EnforcePurpose.full, null, asList("b1", "b2", "b3", "b5", "b7"), null);
        final VendorPermission vendorPermission1 = VendorPermission.of(1, "b1", PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission2 = VendorPermission.of(2, "b2", PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission3 = VendorPermission.of(3, "b3", PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl1 = withGvl(vendorPermission1, Vendor.empty(1));
        final VendorPermissionWithGvl vendorPermissionWitGvl2 = withGvl(vendorPermission2, Vendor.empty(2));
        final VendorPermissionWithGvl vendorPermissionWitGvl3 = withGvl(vendorPermission3, Vendor.empty(3));
        final List<VendorPermissionWithGvl> vendorPermissionsWithGvl = asList(
                vendorPermissionWitGvl1,
                vendorPermissionWitGvl2,
                vendorPermissionWitGvl3);

        given(fullEnforcePurposeStrategy.allowedByTypeStrategy(any(), any(), any(), any(), anyBoolean()))
                .willAnswer(invocation -> Stream.of(vendorPermission1, vendorPermission2, vendorPermission3));

        // when
        target.processTypePurposeStrategy(tcString, purpose, vendorPermissionsWithGvl, false);

        // then
        assertThat(vendorPermission1).isEqualTo(vendorPermissionResult(1, "b1", allowPurposeAndNaturally()));
        assertThat(vendorPermission2).isEqualTo(vendorPermissionResult(2, "b2", allowPurposeAndNaturally()));
        assertThat(vendorPermission3).isEqualTo(vendorPermissionResult(3, "b3", allowPurposeAndNaturally()));

        verify(fullEnforcePurposeStrategy, times(2))
                .allowedByTypeStrategy(PURPOSE_CODE, tcString, emptyList(), vendorPermissionsWithGvl, true);
    }

    @Test
    public void processTypePurposeStrategyShouldAllowPurposeAndNaturallyVendorExceptions() {
        // given
        final Purpose purpose = Purpose.of(EnforcePurpose.full, null, asList("b1", "b2"), null);
        final VendorPermission vendorPermission1 = VendorPermission.of(1, "b1", PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission2 = VendorPermission.of(2, "b2", PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission3 = VendorPermission.of(3, "b3", PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl1 = withGvl(vendorPermission1, Vendor.empty(1));
        final VendorPermissionWithGvl vendorPermissionWitGvl2 = withGvl(vendorPermission2, Vendor.empty(2));
        final VendorPermissionWithGvl vendorPermissionWitGvl3 = withGvl(vendorPermission3, Vendor.empty(3));
        final List<VendorPermissionWithGvl> vendorPermissionsWithGvl = asList(
                vendorPermissionWitGvl1,
                vendorPermissionWitGvl2,
                vendorPermissionWitGvl3);

        given(fullEnforcePurposeStrategy.allowedByTypeStrategy(any(), any(), any(), any(), anyBoolean()))
                .willReturn(Stream.of(vendorPermission1, vendorPermission2, vendorPermission3))
                .willReturn(Stream.of(vendorPermission1, vendorPermission2));

        // when
        target.processTypePurposeStrategy(tcString, purpose, vendorPermissionsWithGvl, false);

        // then
        assertThat(vendorPermission1).isEqualTo(vendorPermissionResult(1, "b1", allowPurposeAndNaturally()));
        assertThat(vendorPermission2).isEqualTo(vendorPermissionResult(2, "b2", allowPurposeAndNaturally()));
        assertThat(vendorPermission3).isEqualTo(vendorPermissionResult(3, "b3", allowPurpose()));

        verify(fullEnforcePurposeStrategy, times(2))
                .allowedByTypeStrategy(
                        PURPOSE_CODE,
                        tcString,
                        singletonList(vendorPermissionWitGvl3),
                        asList(vendorPermissionWitGvl1, vendorPermissionWitGvl2),
                        true);
    }

    @Test
    public void processTypePurposeStrategyShouldAllowNaturally() {
        // given
        final Purpose purpose = Purpose.of(EnforcePurpose.full, null, asList("b1", "b2"), null);
        final VendorPermission vendorPermission1 = VendorPermission.of(1, "b1", PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission2 = VendorPermission.of(2, "b2", PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission3 = VendorPermission.of(3, "b3", PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl1 = withGvl(vendorPermission1, Vendor.empty(1));
        final VendorPermissionWithGvl vendorPermissionWitGvl2 = withGvl(vendorPermission2, Vendor.empty(2));
        final VendorPermissionWithGvl vendorPermissionWitGvl3 = withGvl(vendorPermission3, Vendor.empty(3));
        final List<VendorPermissionWithGvl> vendorPermissionsWithGvl = asList(
                vendorPermissionWitGvl1,
                vendorPermissionWitGvl2,
                vendorPermissionWitGvl3);

        given(fullEnforcePurposeStrategy.allowedByTypeStrategy(any(), any(), any(), any(), anyBoolean()))
                .willReturn(Stream.of(vendorPermission3))
                .willReturn(Stream.of(vendorPermission1, vendorPermission2));

        // when
        target.processTypePurposeStrategy(tcString, purpose, vendorPermissionsWithGvl, false);

        // then
        assertThat(vendorPermission1).isEqualTo(vendorPermissionResult(1, "b1", allowNatural()));
        assertThat(vendorPermission2).isEqualTo(vendorPermissionResult(2, "b2", allowNatural()));
        assertThat(vendorPermission3).isEqualTo(vendorPermissionResult(3, "b3", allowPurpose()));

        verify(fullEnforcePurposeStrategy, times(2))
                .allowedByTypeStrategy(
                        PURPOSE_CODE,
                        tcString,
                        singletonList(vendorPermissionWitGvl3),
                        asList(vendorPermissionWitGvl1, vendorPermissionWitGvl2),
                        true);
    }

    @Test
    public void processTypePurposeStrategyShouldAllowPurposeAndNaturallyWhenVendorPermissionsReturnedForDowngraded() {
        // given
        final Purpose purpose = Purpose.of(EnforcePurpose.no, null, asList("b1", "b2"), null);
        final VendorPermission vendorPermission1 = VendorPermission.of(1, "b1", PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission2 = VendorPermission.of(2, "b2", PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission3 = VendorPermission.of(3, "b3", PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl1 = withGvl(vendorPermission1, Vendor.empty(1));
        final VendorPermissionWithGvl vendorPermissionWitGvl2 = withGvl(vendorPermission2, Vendor.empty(2));
        final VendorPermissionWithGvl vendorPermissionWitGvl3 = withGvl(vendorPermission3, Vendor.empty(3));
        final List<VendorPermissionWithGvl> vendorPermissionsWithGvl = asList(
                vendorPermissionWitGvl1,
                vendorPermissionWitGvl2,
                vendorPermissionWitGvl3);

        given(noEnforcePurposeStrategy.allowedByTypeStrategy(any(), any(), any(), any(), anyBoolean()))
                .willReturn(Stream.of(vendorPermission1, vendorPermission2, vendorPermission3));
        given(basicEnforcePurposeStrategy.allowedByTypeStrategy(any(), any(), any(), any(), anyBoolean()))
                .willReturn(Stream.of(vendorPermission1, vendorPermission2));

        // when
        target.processTypePurposeStrategy(tcString, purpose, vendorPermissionsWithGvl, true);

        // then
        assertThat(vendorPermission1).isEqualTo(vendorPermissionResult(1, "b1", allowPurposeAndNaturally()));
        assertThat(vendorPermission2).isEqualTo(vendorPermissionResult(2, "b2", allowPurposeAndNaturally()));
        assertThat(vendorPermission3).isEqualTo(vendorPermissionResult(3, "b3", allowPurpose()));

        verify(noEnforcePurposeStrategy)
                .allowedByTypeStrategy(
                        PURPOSE_CODE,
                        tcString,
                        singletonList(vendorPermissionWitGvl3),
                        asList(vendorPermissionWitGvl1, vendorPermissionWitGvl2),
                        true);
        verify(basicEnforcePurposeStrategy)
                .allowedByTypeStrategy(
                        PURPOSE_CODE,
                        tcString,
                        singletonList(vendorPermissionWitGvl3),
                        asList(vendorPermissionWitGvl1, vendorPermissionWitGvl2),
                        true);
    }

    private static VendorPermissionWithGvl withGvl(VendorPermission vendorPermission, Vendor vendor) {
        return VendorPermissionWithGvl.of(vendorPermission, vendor);
    }

    private static VendorPermission vendorPermissionResult(Integer vendorId,
                                                           String bidderName,
                                                           PrivacyEnforcementAction privacyEnforcementAction) {

        final VendorPermission vendorPermission = VendorPermission.of(vendorId, bidderName, privacyEnforcementAction);
        vendorPermission.consent(PURPOSE_CODE);
        return vendorPermission;
    }

    private static PrivacyEnforcementAction allowPurposeAndNaturally() {
        return allowNatural(allowPurpose());
    }

    private static PrivacyEnforcementAction allowPurpose() {
        final PrivacyEnforcementAction privacyEnforcementAction = PrivacyEnforcementAction.restrictAll();
        privacyEnforcementAction.setBlockPixelSync(false);
        return privacyEnforcementAction;
    }

    private static PrivacyEnforcementAction allowNatural() {
        return allowNatural(PrivacyEnforcementAction.restrictAll());
    }

    private static PrivacyEnforcementAction allowNatural(PrivacyEnforcementAction privacyEnforcementAction) {
        return privacyEnforcementAction;
    }
}
