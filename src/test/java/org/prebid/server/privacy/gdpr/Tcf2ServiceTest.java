package org.prebid.server.privacy.gdpr;

import com.iabtcf.decoder.TCString;
import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.privacy.gdpr.model.VendorPermission;
import org.prebid.server.privacy.gdpr.model.VendorPermissionWithGvl;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.PurposeStrategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.specialfeature.SpecialFeaturesStrategy;
import org.prebid.server.privacy.gdpr.vendorlist.VersionedVendorListService;
import org.prebid.server.privacy.gdpr.vendorlist.proto.Vendor;
import org.prebid.server.settings.model.AccountGdprConfig;
import org.prebid.server.settings.model.EnforcePurpose;
import org.prebid.server.settings.model.GdprConfig;
import org.prebid.server.settings.model.Purpose;
import org.prebid.server.settings.model.PurposeEid;
import org.prebid.server.settings.model.PurposeOneTreatmentInterpretation;
import org.prebid.server.settings.model.Purposes;
import org.prebid.server.settings.model.SpecialFeature;
import org.prebid.server.settings.model.SpecialFeatures;

import java.util.Collection;
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.apache.commons.collections4.SetUtils.hashSet;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.prebid.server.assertion.FutureAssertion.assertThat;
import static org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction.restrictAll;
import static org.prebid.server.privacy.gdpr.vendorlist.proto.PurposeCode.FOUR;
import static org.prebid.server.privacy.gdpr.vendorlist.proto.PurposeCode.ONE;
import static org.prebid.server.privacy.gdpr.vendorlist.proto.PurposeCode.SEVEN;
import static org.prebid.server.privacy.gdpr.vendorlist.proto.PurposeCode.TWO;

public class Tcf2ServiceTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private BidderCatalog bidderCatalog;
    @Mock
    private VersionedVendorListService vendorListService;
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
    @Mock
    private VendorIdResolver vendorIdResolver;

    private Tcf2Service target;

    private Purposes purposes;

    private Purpose purpose1;
    private Purpose purpose2;
    private Purpose purpose4;
    private Purpose purpose7;

    private Purpose weakPurpose1;
    private Purpose weakPurpose2;
    private Purpose weakPurpose4;
    private Purpose weakPurpose7;

    private SpecialFeature specialFeature1;
    private SpecialFeatures specialFeatures;

    private GdprConfig gdprConfig;

    private List<PurposeStrategy> purposeStrategies;
    private List<SpecialFeaturesStrategy> specialFeaturesStrategies;

    @Before
    public void setUp() {
        given(vendorListService.forConsent(any())).willReturn(Future.succeededFuture(emptyMap()));

        given(purposeStrategyOne.getPurpose()).willReturn(ONE);
        given(purposeStrategyTwo.getPurpose()).willReturn(TWO);
        given(purposeStrategyFour.getPurpose()).willReturn(FOUR);
        given(purposeStrategySeven.getPurpose()).willReturn(SEVEN);
        purposeStrategies = asList(purposeStrategyOne, purposeStrategyTwo, purposeStrategyFour, purposeStrategySeven);

        given(specialFeaturesStrategyOne.getSpecialFeatureId()).willReturn(1);
        specialFeaturesStrategies = singletonList(specialFeaturesStrategyOne);

        given(tcString.getVendorListVersion()).willReturn(10);
        given(vendorIdResolver.resolve(anyString())).willReturn(null);

        initTcf2Service(PurposeOneTreatmentInterpretation.ignore);
    }

    private void initTcf2Service(PurposeOneTreatmentInterpretation p1TI) {
        initPurposes();
        initSpecialFeatures();
        initGdpr(p1TI);

        target = new Tcf2Service(
                gdprConfig,
                purposeStrategies,
                specialFeaturesStrategies,
                vendorListService,
                bidderCatalog);
    }

    private void initPurposes() {
        purpose1 = Purpose.of(EnforcePurpose.basic, true, emptyList(), null);
        purpose2 = Purpose.of(EnforcePurpose.no, true, emptyList(), null);
        purpose4 = Purpose.of(EnforcePurpose.no, false, emptyList(), null);
        purpose7 = Purpose.of(EnforcePurpose.full, false, emptyList(), null);
        purposes = Purposes.builder()
                .p1(purpose1)
                .p2(purpose2)
                .p4(purpose4)
                .p7(purpose7)
                .build();

        weakPurpose1 = Purpose.of(EnforcePurpose.basic, false, emptyList(), null);
        weakPurpose2 = Purpose.of(EnforcePurpose.no, false, emptyList(), null);
        weakPurpose4 = Purpose.of(EnforcePurpose.no, false, emptyList(), null);
        weakPurpose7 = Purpose.of(EnforcePurpose.basic, false, emptyList(), null);
    }

    private void initSpecialFeatures() {
        specialFeature1 = SpecialFeature.of(true, emptyList());
        specialFeatures = SpecialFeatures.builder()
                .sf1(specialFeature1)
                .build();
    }

    private void initGdpr(PurposeOneTreatmentInterpretation p1TI) {
        gdprConfig = GdprConfig.builder()
                .defaultValue("1")
                .enabled(true)
                .purposes(purposes)
                .specialFeatures(specialFeatures)
                .purposeOneTreatmentInterpretation(p1TI)
                .build();
    }

    @Test
    public void permissionsForShouldReturnByGdprPurpose() {
        // given
        given(bidderCatalog.nameByVendorId(any())).willReturn("rubicon");

        // when
        final Future<Collection<VendorPermission>> result = target.permissionsFor(singleton(1), tcString);

        // then
        final VendorPermission expectedVendorPermission = VendorPermission.of(1, "rubicon", restrictAll());
        assertThat(result).succeededWith(singletonList(expectedVendorPermission));

        verifyEachPurposeStrategyReceive(singletonList(withGvl(expectedVendorPermission, 1)));
        verifyEachSpecialFeatureStrategyReceive(singletonList(expectedVendorPermission));

        verify(vendorListService).forConsent(argThat(tcString -> tcString.getVendorListVersion() == 10));
    }

    @Test
    public void permissionsForShouldReturnByGdprPurposeAndDowngradeToBasicTypeWhenVendorListServiceFailed() {
        // given
        given(vendorListService.forConsent(any())).willReturn(Future.failedFuture("Bad version"));
        given(bidderCatalog.nameByVendorId(any())).willReturn("rubicon");

        // when
        final Future<Collection<VendorPermission>> result = target.permissionsFor(singleton(1), tcString);

        // then
        final VendorPermission expectedVendorPermission = VendorPermission.of(1, "rubicon", restrictAll());
        assertThat(result).succeededWith(singletonList(expectedVendorPermission));

        final Purpose downgradedPurpose7 = Purpose.of(
                EnforcePurpose.basic,
                purpose7.getEnforceVendors(),
                purpose7.getVendorExceptions(),
                purpose7.getEid());
        final List<VendorPermissionWithGvl> permissionsWithGvl = singletonList(withGvl(expectedVendorPermission, 1));
        verify(purposeStrategyOne).processTypePurposeStrategy(tcString, purpose1, permissionsWithGvl, true);
        verify(purposeStrategyTwo).processTypePurposeStrategy(tcString, purpose2, permissionsWithGvl, true);
        verify(purposeStrategyFour).processTypePurposeStrategy(tcString, purpose4, permissionsWithGvl, true);
        verify(purposeStrategySeven)
                .processTypePurposeStrategy(tcString, downgradedPurpose7, permissionsWithGvl, true);
        verifyEachSpecialFeatureStrategyReceive(singletonList(expectedVendorPermission));

        verify(vendorListService).forConsent(any());
    }

    @Test
    public void permissionsForShouldMergeAccountPurposes() {
        // given
        final Purpose accountPurposeOne = Purpose.of(EnforcePurpose.full, false, singletonList("test"), null);
        final AccountGdprConfig accountGdprConfig = AccountGdprConfig.builder()
                .purposes(Purposes.builder().p1(accountPurposeOne).build())
                .build();

        // when
        final Future<Collection<VendorPermission>> result = target.permissionsFor(
                singleton("b1"), vendorIdResolver, tcString, accountGdprConfig);

        // then
        final VendorPermission expectedVendorPermission = VendorPermission.of(null, "b1", restrictAll());
        assertThat(result).succeededWith(singletonList(expectedVendorPermission));

        verify(purposeStrategyOne).processTypePurposeStrategy(
                tcString,
                accountPurposeOne,
                singletonList(withGvl(expectedVendorPermission, null)),
                false);

        verify(vendorIdResolver).resolve(anyString());
        verify(vendorListService).forConsent(argThat(tcString -> tcString.getVendorListVersion() == 10));
    }

    @Test
    public void permissionsForShouldMergeAccountSpecialFeatures() {
        // given
        final SpecialFeature accountSpecialFeatureOne = SpecialFeature.of(false, emptyList());
        final AccountGdprConfig accountGdprConfig = AccountGdprConfig.builder()
                .specialFeatures(SpecialFeatures.builder().sf1(accountSpecialFeatureOne).build())
                .build();

        // when
        final Future<Collection<VendorPermission>> result = target.permissionsFor(
                singleton("b1"), vendorIdResolver, tcString, accountGdprConfig);

        // then
        final VendorPermission expectedVendorPermission = VendorPermission.of(null, "b1", restrictAll());
        assertThat(result).succeededWith(singletonList(expectedVendorPermission));

        verify(specialFeaturesStrategyOne).processSpecialFeaturesStrategy(
                tcString,
                accountSpecialFeatureOne,
                singletonList(expectedVendorPermission));
    }

    @Test
    public void permissionsForShouldSplitIntoWeakPurposesWhenAccountHaveBasicEnforcementBidders() {
        // given
        given(vendorIdResolver.resolve(eq("b1"))).willReturn(1);
        given(vendorIdResolver.resolve(eq("b2"))).willReturn(2);
        final AccountGdprConfig accountGdprConfig = AccountGdprConfig.builder()
                .basicEnforcementVendors(singletonList("b2"))
                .build();

        // when
        final Future<Collection<VendorPermission>> result = target.permissionsFor(
                hashSet("b1", "b2"), vendorIdResolver, tcString, accountGdprConfig);

        // then
        final VendorPermission expectedVendorPermission1 = VendorPermission.of(1, "b1", restrictAll());
        final VendorPermission expectedVendorPermission2 = VendorPermission.of(2, "b2", restrictAll());
        assertThat(result).succeededWith(asList(expectedVendorPermission2, expectedVendorPermission1));

        verifyEachPurposeStrategyReceive(singletonList(withGvl(expectedVendorPermission1, 1)));
        verifyEachPurposeStrategyReceiveWeak(singletonList(withGvl(expectedVendorPermission2, 2)));
        verifyEachSpecialFeatureStrategyReceive(asList(expectedVendorPermission2, expectedVendorPermission1));

        verify(vendorIdResolver, times(2)).resolve(anyString());
        verify(vendorListService).forConsent(any());
    }

    @Test
    public void permissionsForShouldReturnBidderNamesResult() {
        // given
        given(vendorIdResolver.resolve(eq("b1"))).willReturn(1);

        // when
        final Future<Collection<VendorPermission>> result = target.permissionsFor(
                hashSet("b1", "b2"), vendorIdResolver, tcString, null);

        // then
        final VendorPermission expectedVendorPermission1 = VendorPermission.of(1, "b1", restrictAll());
        final VendorPermission expectedVendorPermission2 = VendorPermission.of(null, "b2", restrictAll());
        assertThat(result).succeededWith(asList(expectedVendorPermission2, expectedVendorPermission1));

        verifyEachPurposeStrategyReceive(asList(
                withGvl(expectedVendorPermission2, null),
                withGvl(expectedVendorPermission1, 1)));
        verifyEachSpecialFeatureStrategyReceive(asList(expectedVendorPermission2, expectedVendorPermission1));

        verify(vendorIdResolver, times(2)).resolve(anyString());
        verify(vendorListService).forConsent(any());
    }

    @Test
    public void permissionsForShouldReturnVendorIdsResult() {
        // given
        given(bidderCatalog.nameByVendorId(eq(1))).willReturn("b1");

        // when
        final Future<Collection<VendorPermission>> result = target.permissionsFor(hashSet(1, 2), tcString);

        // then
        final VendorPermission expectedVendorPermission1 = VendorPermission.of(1, "b1", restrictAll());
        final VendorPermission expectedVendorPermission2 = VendorPermission.of(2, null, restrictAll());
        assertThat(result).succeededWith(asList(expectedVendorPermission1, expectedVendorPermission2));

        verifyEachPurposeStrategyReceive(asList(
                withGvl(expectedVendorPermission1, 1),
                withGvl(expectedVendorPermission2, 2)));
        verifyEachSpecialFeatureStrategyReceive(asList(expectedVendorPermission1, expectedVendorPermission2));

        verify(vendorListService).forConsent(any());
    }

    @Test
    public void permissionsForShouldReturnAllDeniedWhenP1TIIsNoAccessAllowed() {
        // given
        given(bidderCatalog.nameByVendorId(any())).willReturn("rubicon");
        given(tcString.getPurposeOneTreatment()).willReturn(true);
        initTcf2Service(PurposeOneTreatmentInterpretation.noAccessAllowed);

        // when
        final Future<Collection<VendorPermission>> result = target.permissionsFor(singleton(1), tcString);

        // then
        final VendorPermission expectedVendorPermission = VendorPermission.of(1, "rubicon", restrictAll());
        assertThat(result).succeededWith(singletonList(expectedVendorPermission));

        final List<VendorPermissionWithGvl> permissions = singletonList(withGvl(expectedVendorPermission, 1));
        verify(purposeStrategyOne, never())
                .processTypePurposeStrategy(any(), any(), anyCollection(), anyBoolean());
        verify(purposeStrategyTwo).processTypePurposeStrategy(any(), any(), eq(permissions), eq(false));
        verify(purposeStrategySeven).processTypePurposeStrategy(any(), any(), eq(permissions), eq(false));
        verify(purposeStrategyFour).processTypePurposeStrategy(any(), any(), eq(permissions), eq(false));
        verify(purposeStrategyTwo).processTypePurposeStrategy(any(), any(), eq(emptyList()), eq(true));
        verify(purposeStrategySeven).processTypePurposeStrategy(any(), any(), eq(emptyList()), eq(true));
        verify(purposeStrategyFour).processTypePurposeStrategy(any(), any(), eq(emptyList()), eq(true));
        verifyEachSpecialFeatureStrategyReceive(singletonList(expectedVendorPermission));

        verify(vendorListService).forConsent(any());
    }

    @Test
    public void permissionsForShouldAllowAllWhenP1TIIsAccessAllowed() {
        // given
        given(bidderCatalog.nameByVendorId(any())).willReturn("rubicon");
        given(tcString.getPurposeOneTreatment()).willReturn(true);
        initTcf2Service(PurposeOneTreatmentInterpretation.accessAllowed);

        // when
        final Future<Collection<VendorPermission>> result = target.permissionsFor(singleton(1), tcString);

        // then
        final VendorPermission expectedVendorPermission = VendorPermission.of(1, "rubicon", restrictAll());
        assertThat(result).succeededWith(singletonList(expectedVendorPermission));

        final List<VendorPermissionWithGvl> permissions = singletonList(withGvl(expectedVendorPermission, 1));
        verify(purposeStrategyOne, never())
                .processTypePurposeStrategy(any(), any(), anyCollection(), anyBoolean());
        verify(purposeStrategyOne).allow(Mockito.<VendorPermission>any());
        verify(purposeStrategyTwo).processTypePurposeStrategy(any(), any(), eq(permissions), eq(false));
        verify(purposeStrategySeven).processTypePurposeStrategy(any(), any(), eq(permissions), eq(false));
        verify(purposeStrategyFour).processTypePurposeStrategy(any(), any(), eq(permissions), eq(false));
        verify(purposeStrategyTwo).processTypePurposeStrategy(any(), any(), eq(emptyList()), eq(true));
        verify(purposeStrategySeven).processTypePurposeStrategy(any(), any(), eq(emptyList()), eq(true));
        verify(purposeStrategyFour).processTypePurposeStrategy(any(), any(), eq(emptyList()), eq(true));
        verifyEachSpecialFeatureStrategyReceive(singletonList(expectedVendorPermission));

        verify(vendorListService).forConsent(any());
    }

    @Test
    public void permissionsForShouldNotAllowAllWhenP1TIsFalseAndP1TIIsAccessAllowed() {
        // given
        given(bidderCatalog.nameByVendorId(any())).willReturn("rubicon");
        given(tcString.getPurposeOneTreatment()).willReturn(false);
        initTcf2Service(PurposeOneTreatmentInterpretation.accessAllowed);

        // when
        final Future<Collection<VendorPermission>> result = target.permissionsFor(singleton(1), tcString);

        // then
        final VendorPermission expectedVendorPermission = VendorPermission.of(1, "rubicon", restrictAll());
        assertThat(result).succeededWith(singletonList(expectedVendorPermission));

        final List<VendorPermissionWithGvl> standardPermissions = singletonList(withGvl(expectedVendorPermission, 1));
        verify(purposeStrategyOne, never()).allow(Mockito.<VendorPermission>any());
        verifyEachPurposeStrategyReceive(standardPermissions);
        verifyEachPurposeStrategyReceiveWeak(emptyList());
        verifyEachSpecialFeatureStrategyReceive(singletonList(expectedVendorPermission));

        verify(vendorListService).forConsent(any());
    }

    @Test
    public void permissionsForShouldRequirePurpose4ConsentIfConfigured() {
        // given
        given(vendorIdResolver.resolve(eq("b1"))).willReturn(1);
        given(vendorIdResolver.resolve(eq("b2"))).willReturn(2);

        final Purpose purposeFour = Purpose.of(
                purpose4.getEnforcePurpose(),
                purpose4.getEnforceVendors(),
                purpose4.getVendorExceptions(),
                PurposeEid.of(null, true, null));
        final AccountGdprConfig accountGdprConfig = AccountGdprConfig.builder()
                .basicEnforcementVendors(singletonList("b2"))
                .purposes(Purposes.builder().p4(purposeFour).build())
                .build();

        // when
        final Future<Collection<VendorPermission>> result = target.permissionsFor(
                hashSet("b1", "b2"), vendorIdResolver, tcString, accountGdprConfig);

        // then
        final VendorPermission expectedVendorPermission1 = VendorPermission.of(1, "b1", restrictAll());
        final VendorPermission expectedVendorPermission2 = VendorPermission.of(2, "b2", restrictAll());
        assertThat(result).succeededWith(asList(expectedVendorPermission2, expectedVendorPermission1));

        final List<VendorPermissionWithGvl> permissions1 = singletonList(withGvl(expectedVendorPermission1, 1));
        verify(purposeStrategyOne).processTypePurposeStrategy(tcString, purpose1, permissions1, false);
        verify(purposeStrategyTwo).processTypePurposeStrategy(tcString, purpose2, permissions1, false);
        verify(purposeStrategyFour).processTypePurposeStrategy(tcString, purposeFour, permissions1, false);
        verify(purposeStrategySeven).processTypePurposeStrategy(tcString, purpose7, permissions1, false);

        final Purpose weakPurposeFour = Purpose.of(
                purposeFour.getEnforcePurpose(),
                false,
                purposeFour.getVendorExceptions(),
                purposeFour.getEid());
        final List<VendorPermissionWithGvl> permissions2 = singletonList(withGvl(expectedVendorPermission2, 2));
        verify(purposeStrategyOne).processTypePurposeStrategy(tcString, weakPurpose1, permissions2, true);
        verify(purposeStrategyTwo).processTypePurposeStrategy(tcString, weakPurpose2, permissions2, true);
        verify(purposeStrategyFour).processTypePurposeStrategy(tcString, weakPurposeFour, permissions2, true);
        verify(purposeStrategySeven).processTypePurposeStrategy(tcString, weakPurpose7, permissions2, true);

        verifyEachSpecialFeatureStrategyReceive(asList(expectedVendorPermission2, expectedVendorPermission1));

        verify(vendorIdResolver, times(2)).resolve(anyString());
        verify(vendorListService).forConsent(any());
    }

    public void verifyEachPurposeStrategyReceive(List<VendorPermissionWithGvl> permissions) {
        verify(purposeStrategyOne).processTypePurposeStrategy(tcString, purpose1, permissions, false);
        verify(purposeStrategyTwo).processTypePurposeStrategy(tcString, purpose2, permissions, false);
        verify(purposeStrategyFour).processTypePurposeStrategy(tcString, purpose4, permissions, false);
        verify(purposeStrategySeven).processTypePurposeStrategy(tcString, purpose7, permissions, false);
    }

    public void verifyEachPurposeStrategyReceiveWeak(List<VendorPermissionWithGvl> permissions) {
        verify(purposeStrategyOne).processTypePurposeStrategy(tcString, weakPurpose1, permissions, true);
        verify(purposeStrategyTwo).processTypePurposeStrategy(tcString, weakPurpose2, permissions, true);
        verify(purposeStrategyFour).processTypePurposeStrategy(tcString, weakPurpose4, permissions, true);
        verify(purposeStrategySeven).processTypePurposeStrategy(tcString, weakPurpose7, permissions, true);
    }

    public void verifyEachSpecialFeatureStrategyReceive(List<VendorPermission> vendorPermission) {
        verify(specialFeaturesStrategyOne).processSpecialFeaturesStrategy(tcString, specialFeature1, vendorPermission);
    }

    private static VendorPermissionWithGvl withGvl(VendorPermission vendorPermission, Integer vendorId) {
        return VendorPermissionWithGvl.of(vendorPermission, Vendor.empty(vendorId));
    }
}
