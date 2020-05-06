package org.prebid.server.privacy.gdpr;

import com.iabtcf.decoder.TCString;
import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.internal.util.reflection.FieldSetter;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.VendorPermission;
import org.prebid.server.privacy.gdpr.model.VendorPermissionWithGvl;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.PurposeStrategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.specialfeature.SpecialFeaturesStrategy;
import org.prebid.server.privacy.gdpr.vendorlist.VendorListServiceV2;
import org.prebid.server.privacy.gdpr.vendorlist.proto.VendorV2;
import org.prebid.server.settings.model.AccountGdprConfig;
import org.prebid.server.settings.model.EnforcePurpose;
import org.prebid.server.settings.model.GdprConfig;
import org.prebid.server.settings.model.Purpose;
import org.prebid.server.settings.model.PurposeOneTreatmentInterpretation;
import org.prebid.server.settings.model.Purposes;
import org.prebid.server.settings.model.SpecialFeature;
import org.prebid.server.settings.model.SpecialFeatures;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.prebid.server.assertion.FutureAssertion.assertThat;

public class Tcf2ServiceTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private BidderCatalog bidderCatalog;
    @Mock
    private VendorListServiceV2 vendorListService;
    @Mock
    private PurposeStrategy purposeStrategyOne;
    @Mock
    private PurposeStrategy purposeStrategyTwo;
    @Mock
    private PurposeStrategy purposeStrategyFour;
    @Mock
    private PurposeStrategy purposeStrategySeven;
    @Mock
    private SpecialFeaturesStrategy specialFeaturesStrategyOne;
    @Mock
    private TCString tcString;

    private Tcf2Service target;

    private Purposes purposes;

    private Purpose purpose1;
    private Purpose purpose2;
    private Purpose purpose4;
    private Purpose purpose7;

    private SpecialFeature specialFeature1;
    private SpecialFeatures specialFeatures;

    private GdprConfig gdprConfig;

    private List<PurposeStrategy> purposeStrategies;
    private List<SpecialFeaturesStrategy> specialFeaturesStrategies;

    @Before
    public void setUp() throws NoSuchFieldException {
        given(tcString.getVendorListVersion()).willReturn(10);
        given(purposeStrategyOne.getPurposeId()).willReturn(1);
        given(purposeStrategyTwo.getPurposeId()).willReturn(2);
        given(purposeStrategyFour.getPurposeId()).willReturn(4);
        given(purposeStrategySeven.getPurposeId()).willReturn(7);
        purposeStrategies = asList(purposeStrategyOne, purposeStrategyTwo, purposeStrategyFour, purposeStrategySeven);

        given(specialFeaturesStrategyOne.getSpecialFeatureId()).willReturn(1);
        specialFeaturesStrategies = singletonList(specialFeaturesStrategyOne);

        given(vendorListService.forVersion(anyInt())).willReturn(Future.succeededFuture(emptyMap()));

        initPurposes();
        initSpecialFeatures();
        initGdpr();
        target = new Tcf2Service(gdprConfig, vendorListService, bidderCatalog);

        FieldSetter.setField(target,
                target.getClass().getDeclaredField("supportedPurposeStrategies"), purposeStrategies);
        FieldSetter.setField(target,
                target.getClass().getDeclaredField("supportedSpecialFeatureStrategies"), specialFeaturesStrategies);
    }

    private void initPurposes() {
        purpose1 = Purpose.of(EnforcePurpose.basic, true, emptyList());
        purpose2 = Purpose.of(EnforcePurpose.no, true, emptyList());
        purpose4 = Purpose.of(EnforcePurpose.no, false, emptyList());
        purpose7 = Purpose.of(EnforcePurpose.full, false, emptyList());
        purposes = Purposes.builder()
                .p1(purpose1)
                .p2(purpose2)
                .p4(purpose4)
                .p7(purpose7)
                .build();
    }

    private void initSpecialFeatures() {
        specialFeature1 = SpecialFeature.of(true, emptyList());
        specialFeatures = SpecialFeatures.builder()
                .sf1(specialFeature1)
                .build();
    }

    private void initGdpr() {
        gdprConfig = GdprConfig.builder()
                .defaultValue("1")
                .enabled(true)
                .purposes(purposes)
                .specialFeatures(specialFeatures)
                .purposeOneTreatmentInterpretation(PurposeOneTreatmentInterpretation.ignore)
                .build();
    }

    @Test
    public void permissionsForShouldReturnByGdprPurpose() {
        // given
        given(bidderCatalog.nameByVendorId(any())).willReturn("rubicon");

        // when
        final Future<Collection<VendorPermission>> result = target.permissionsFor(singleton(1), tcString);

        // then
        final VendorPermission expectedVendorPermission =
                VendorPermission.of(1, "rubicon", PrivacyEnforcementAction.restrictAll());

        assertThat(result).succeededWith(singletonList(expectedVendorPermission));

        final VendorPermissionWithGvl expectedVendorPermissionWitGvl = VendorPermissionWithGvl.of(
                expectedVendorPermission, VendorV2.empty(1));
        verifyEachPurposeStrategyReceive(singletonList(expectedVendorPermissionWitGvl));
        verifyEachSpecialFeatureStrategyReceive(singletonList(expectedVendorPermission));

        verify(bidderCatalog).nameByVendorId(1);
        verify(tcString).getVendorListVersion();
        verify(vendorListService).forVersion(10);
    }

    @Test
    public void permissionsForShouldReturnByGdprPurposeAndDowngradeToBasicTypeWhenVendorListServiceIsFailed() {
        // given
        given(vendorListService.forVersion(anyInt())).willReturn(Future.failedFuture("Bad version"));
        given(bidderCatalog.nameByVendorId(any())).willReturn("rubicon");

        // when
        final Future<Collection<VendorPermission>> result = target.permissionsFor(singleton(1), tcString);

        // then
        final VendorPermission expectedVendorPermission = VendorPermission.of(1, "rubicon",
                PrivacyEnforcementAction.restrictAll());
        assertThat(result).succeededWith(singletonList(expectedVendorPermission));

        final VendorPermissionWithGvl expectedVendorPermissionWitGvl = VendorPermissionWithGvl.of(
                expectedVendorPermission, VendorV2.empty(1));
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(expectedVendorPermissionWitGvl);
        verify(purposeStrategyOne).processTypePurposeStrategy(tcString, purpose1, vendorPermissionWithGvls);
        verify(purposeStrategyTwo).processTypePurposeStrategy(tcString, purpose2, vendorPermissionWithGvls);
        verify(purposeStrategyFour).processTypePurposeStrategy(tcString, purpose4, vendorPermissionWithGvls);

        final Purpose expectedDowngradedPurpose = Purpose.of(EnforcePurpose.basic, purpose7.getEnforceVendors(),
                purpose1.getVendorExceptions());
        verify(purposeStrategySeven).processTypePurposeStrategy(tcString, expectedDowngradedPurpose,
                vendorPermissionWithGvls);
        verifyEachSpecialFeatureStrategyReceive(singletonList(expectedVendorPermission));

        verify(bidderCatalog).nameByVendorId(1);
        verify(tcString).getVendorListVersion();
        verify(vendorListService).forVersion(10);
    }

    @Test
    public void permissionsForShouldMergeAccountPurposes() {
        // given
        final Purpose accountPurposeOne = Purpose.of(EnforcePurpose.full, false, singletonList("test"));
        final Purposes accountPurposes = Purposes.builder()
                .p1(accountPurposeOne)
                .build();

        final AccountGdprConfig accountGdprConfig = AccountGdprConfig.builder().purposes(accountPurposes).build();

        final VendorIdResolver vendorIdResolver = mock(VendorIdResolver.class);
        given(vendorIdResolver.resolve(anyString())).willReturn(null);

        // when
        final Future<Collection<VendorPermission>> result =
                target.permissionsFor(singleton("b1"), vendorIdResolver, tcString, accountGdprConfig);

        // then
        final VendorPermission expectedVendorPermission =
                VendorPermission.of(null, "b1", PrivacyEnforcementAction.restrictAll());
        assertThat(result).succeededWith(singletonList(expectedVendorPermission));

        verify(purposeStrategyOne).processTypePurposeStrategy(
                tcString,
                accountPurposeOne,
                singletonList(VendorPermissionWithGvl.of(expectedVendorPermission, VendorV2.empty(null))));
        verify(tcString).getVendorListVersion();
        verify(vendorListService).forVersion(10);
    }

    @Test
    public void permissionsForShouldMergeAccountSpecialFeatures() {
        // given
        final SpecialFeature accountSpecialFeatureOne = SpecialFeature.of(false, emptyList());
        final SpecialFeatures specialFeatures = SpecialFeatures.builder()
                .sf1(accountSpecialFeatureOne)
                .build();

        final AccountGdprConfig accountGdprConfig = AccountGdprConfig.builder()
                .specialFeatures(specialFeatures)
                .build();

        final VendorIdResolver vendorIdResolver = mock(VendorIdResolver.class);
        given(vendorIdResolver.resolve(anyString())).willReturn(null);

        // when
        final Future<Collection<VendorPermission>> result =
                target.permissionsFor(singleton("b1"), vendorIdResolver, tcString, accountGdprConfig);

        // then
        final VendorPermission expectedVendorPermission =
                VendorPermission.of(null, "b1", PrivacyEnforcementAction.restrictAll());
        assertThat(result).succeededWith(singletonList(expectedVendorPermission));

        verify(specialFeaturesStrategyOne).processSpecialFeaturesStrategy(
                tcString,
                accountSpecialFeatureOne,
                singletonList(expectedVendorPermission));
    }

    @Test
    public void permissionsForShouldReturnBidderNamesResult() {
        // given
        final VendorIdResolver vendorIdResolver = mock(VendorIdResolver.class);
        given(vendorIdResolver.resolve(eq("b1"))).willReturn(1);
        given(vendorIdResolver.resolve(eq("b2"))).willReturn(null);

        // when
        final Future<Collection<VendorPermission>> result =
                target.permissionsFor(new HashSet<>(asList("b1", "b2")), vendorIdResolver, tcString, null);

        // then
        final VendorPermission expectedVendorPermission1 =
                VendorPermission.of(1, "b1", PrivacyEnforcementAction.restrictAll());
        final VendorPermission expectedVendorPermission2 =
                VendorPermission.of(null, "b2", PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl expectedVendorPermissionWitGvl1 =
                VendorPermissionWithGvl.of(expectedVendorPermission1, VendorV2.empty(1));
        final VendorPermissionWithGvl expectedVendorPermissionWitGvl2 =
                VendorPermissionWithGvl.of(expectedVendorPermission2, VendorV2.empty(null));
        assertThat(result).succeededWith(asList(expectedVendorPermission2, expectedVendorPermission1));

        verifyEachPurposeStrategyReceive(asList(expectedVendorPermissionWitGvl2, expectedVendorPermissionWitGvl1));
        verifyEachSpecialFeatureStrategyReceive(asList(expectedVendorPermission2, expectedVendorPermission1));

        verify(vendorIdResolver, times(2)).resolve(anyString());
        verify(tcString).getVendorListVersion();
    }

    @Test
    public void permissionsForShouldReturnVendorIdsResult() {
        // given
        given(bidderCatalog.nameByVendorId(eq(1))).willReturn("b1");

        // when
        final Future<Collection<VendorPermission>> result =
                target.permissionsFor(new HashSet<>(asList(1, 2)), tcString);

        // then
        final VendorPermission expectedVendorPermission1 = VendorPermission.of(1, "b1",
                PrivacyEnforcementAction.restrictAll());
        final VendorPermission expectedVendorPermission2 = VendorPermission.of(2, null,
                PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl expectedVendorPermissionWitGvl1 = VendorPermissionWithGvl.of(
                expectedVendorPermission1, VendorV2.empty(1));
        final VendorPermissionWithGvl expectedVendorPermissionWitGvl2 = VendorPermissionWithGvl.of(
                expectedVendorPermission2, VendorV2.empty(2));
        assertThat(result).succeededWith(asList(expectedVendorPermission1, expectedVendorPermission2));
        verifyEachPurposeStrategyReceive(asList(expectedVendorPermissionWitGvl1, expectedVendorPermissionWitGvl2));
        verifyEachSpecialFeatureStrategyReceive(asList(expectedVendorPermission1, expectedVendorPermission2));

        verify(bidderCatalog, times(2)).nameByVendorId(anyInt());
        verify(tcString).getVendorListVersion();

        verifyNoMoreInteractions(bidderCatalog);
    }

    @Test
    public void permissionsForShouldReturnAllDeniedWhenP1TIIsNoAccessAllowed() throws NoSuchFieldException {
        // given
        given(bidderCatalog.nameByVendorId(any())).willReturn("rubicon");

        given(tcString.getPurposeOneTreatment()).willReturn(true);

        target = new Tcf2Service(
                GdprConfig.builder()
                        .purposes(purposes)
                        .purposeOneTreatmentInterpretation(PurposeOneTreatmentInterpretation.noAccessAllowed)
                        .build(),
                vendorListService,
                bidderCatalog);
        FieldSetter.setField(target,
                target.getClass().getDeclaredField("supportedPurposeStrategies"), purposeStrategies);
        FieldSetter.setField(target,
                target.getClass().getDeclaredField("supportedSpecialFeatureStrategies"), specialFeaturesStrategies);

        // when
        final Future<Collection<VendorPermission>> result = target.permissionsFor(singleton(1), tcString);

        // then
        assertThat(result).succeededWith(
                singletonList(VendorPermission.of(1, "rubicon", PrivacyEnforcementAction.restrictAll())));

        verify(purposeStrategyOne, never()).processTypePurposeStrategy(any(), any(), anyCollection());
        verify(purposeStrategyTwo).processTypePurposeStrategy(any(), any(), anyCollection());
        verify(purposeStrategySeven).processTypePurposeStrategy(any(), any(), anyCollection());
        verify(purposeStrategyFour).processTypePurposeStrategy(any(), any(), anyCollection());

        verify(specialFeaturesStrategyOne).processSpecialFeaturesStrategy(any(), any(), anyCollection());
    }

    @Test
    public void permissionsForShouldAllowAllWhenP1TIIsAccessAllowed() throws NoSuchFieldException {
        // given
        given(bidderCatalog.nameByVendorId(any())).willReturn("rubicon");

        given(tcString.getPurposeOneTreatment()).willReturn(true);

        target = new Tcf2Service(
                GdprConfig.builder()
                        .purposes(purposes)
                        .purposeOneTreatmentInterpretation(PurposeOneTreatmentInterpretation.accessAllowed)
                        .build(),
                vendorListService,
                bidderCatalog);
        FieldSetter.setField(target,
                target.getClass().getDeclaredField("supportedPurposeStrategies"), purposeStrategies);
        FieldSetter.setField(target,
                target.getClass().getDeclaredField("supportedSpecialFeatureStrategies"), specialFeaturesStrategies);

        // when
        target.permissionsFor(singleton(1), tcString);

        // then
        verify(purposeStrategyOne, never()).processTypePurposeStrategy(any(), any(), anyCollection());
        verify(purposeStrategyOne).allow(any());
        verify(purposeStrategyTwo).processTypePurposeStrategy(any(), any(), anyCollection());
        verify(purposeStrategySeven).processTypePurposeStrategy(any(), any(), anyCollection());
        verify(purposeStrategyFour).processTypePurposeStrategy(any(), any(), anyCollection());

        verify(specialFeaturesStrategyOne).processSpecialFeaturesStrategy(any(), any(), anyCollection());
    }

    @Test
    public void permissionsForShouldNotAllowAllWhenP1TIsFalseAndP1TIIsAccessAllowed() throws NoSuchFieldException {
        // given
        given(bidderCatalog.nameByVendorId(any())).willReturn("rubicon");

        given(tcString.getPurposeOneTreatment()).willReturn(false);

        target = new Tcf2Service(
                GdprConfig.builder()
                        .purposes(purposes)
                        .purposeOneTreatmentInterpretation(PurposeOneTreatmentInterpretation.accessAllowed)
                        .build(),
                vendorListService,
                bidderCatalog);
        FieldSetter.setField(target,
                target.getClass().getDeclaredField("supportedPurposeStrategies"), purposeStrategies);
        FieldSetter.setField(target,
                target.getClass().getDeclaredField("supportedSpecialFeatureStrategies"), specialFeaturesStrategies);

        // when
        target.permissionsFor(singleton(1), tcString);

        // then
        verify(purposeStrategyOne, never()).allow(any());
        verify(purposeStrategyOne).processTypePurposeStrategy(any(), any(), anyCollection());
        verify(purposeStrategyTwo).processTypePurposeStrategy(any(), any(), anyCollection());
        verify(purposeStrategySeven).processTypePurposeStrategy(any(), any(), anyCollection());
        verify(purposeStrategyFour).processTypePurposeStrategy(any(), any(), anyCollection());

        verify(specialFeaturesStrategyOne).processSpecialFeaturesStrategy(any(), any(), anyCollection());
    }

    public void verifyEachPurposeStrategyReceive(List<VendorPermissionWithGvl> vendorPermissionWithGvls) {
        verify(purposeStrategyOne).processTypePurposeStrategy(tcString, purpose1, vendorPermissionWithGvls);
        verify(purposeStrategyTwo).processTypePurposeStrategy(tcString, purpose2, vendorPermissionWithGvls);
        verify(purposeStrategyFour).processTypePurposeStrategy(tcString, purpose4, vendorPermissionWithGvls);
        verify(purposeStrategySeven).processTypePurposeStrategy(tcString, purpose7, vendorPermissionWithGvls);
    }

    public void verifyEachSpecialFeatureStrategyReceive(List<VendorPermission> vendorPermission) {
        verify(specialFeaturesStrategyOne).processSpecialFeaturesStrategy(tcString, specialFeature1, vendorPermission);
    }
}
