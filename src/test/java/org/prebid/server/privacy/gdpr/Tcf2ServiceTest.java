package org.prebid.server.privacy.gdpr;

import com.iabtcf.decoder.TCString;
import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.VendorPermission;
import org.prebid.server.privacy.gdpr.model.VendorPermissionWithGvl;
import org.prebid.server.privacy.gdpr.tcfstrategies.PurposeStrategy;
import org.prebid.server.privacy.gdpr.vendorlist.VendorListServiceV2;
import org.prebid.server.privacy.gdpr.vendorlist.proto.VendorV2;
import org.prebid.server.settings.model.AccountGdprConfig;
import org.prebid.server.settings.model.EnforcePurpose;
import org.prebid.server.settings.model.GdprConfig;
import org.prebid.server.settings.model.Purpose;
import org.prebid.server.settings.model.PurposeOneTreatmentInterpretation;
import org.prebid.server.settings.model.Purposes;

import java.util.Collection;
import java.util.HashSet;

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
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.prebid.server.assertion.FutureAssertion.assertThat;

public class Tcf2ServiceTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private BidderCatalog bidderCatalog;
    @Mock
    private VendorListServiceV2 vendorListService;
    @Mock
    private PurposeStrategy purposeStrategy;
    @Mock
    private TCString tcString;

    private Tcf2Service target;

    private Purposes purposes;

    private Purpose purpose1;

    private GdprConfig gdprConfig;

    @Before
    public void setUp() {
        given(tcString.getVendorListVersion()).willReturn(10);
        given(purposeStrategy.getPurposeId()).willReturn(1);
        given(vendorListService.forVersion(anyInt())).willReturn(Future.succeededFuture(emptyMap()));

        initPurposes();
        initGdpr();

        target = new Tcf2Service(gdprConfig, vendorListService, bidderCatalog, singletonList(purposeStrategy));
    }

    private void initPurposes() {
        purpose1 = Purpose.of(EnforcePurpose.basic, true, emptyList());
        purposes = Purposes.builder()
                .p1(purpose1)
                .build();
    }

    private void initGdpr() {
        gdprConfig = GdprConfig.builder()
                .defaultValue("1")
                .enabled(true)
                .purposes(purposes)
                .purposeOneTreatmentInterpretation(PurposeOneTreatmentInterpretation.ignore)
                .build();
    }

    @Test
    public void permissionsForShouldReturnByGdprPurpose() {
        // given
        given(bidderCatalog.nameByVendorId(any())).willReturn("rubicon");
        target = new Tcf2Service(
                GdprConfig.builder()
                        .purposes(purposes)
                        .purposeOneTreatmentInterpretation(PurposeOneTreatmentInterpretation.ignore)
                        .build(),
                vendorListService,
                bidderCatalog,
                singletonList(purposeStrategy));

        // when
        final Future<Collection<VendorPermission>> result = target.permissionsFor(singleton(1), tcString);

        // then
        final VendorPermission expectedVendorPermission =
                VendorPermission.of(1, "rubicon", PrivacyEnforcementAction.restrictAll());

        assertThat(result).succeededWith(singletonList(expectedVendorPermission));

        verify(purposeStrategy).getPurposeId();
        verify(purposeStrategy).processTypePurposeStrategy(
                tcString,
                purpose1,
                singletonList(VendorPermissionWithGvl.of(expectedVendorPermission, VendorV2.empty(1))));
        verify(bidderCatalog).nameByVendorId(1);
        verify(tcString).getVendorListVersion();
        verify(vendorListService).forVersion(10);

        verifyZeroInteractions(tcString);
        verifyZeroInteractions(purposeStrategy);
    }

    @Test
    public void permissionsForShouldReturnByGdprPurposeWhenVendorListServiceIsFailed() {
        // given
        given(vendorListService.forVersion(anyInt())).willReturn(Future.failedFuture("Bad version"));
        given(bidderCatalog.nameByVendorId(any())).willReturn("rubicon");
        target = new Tcf2Service(GdprConfig.builder().purposes(purposes).build(), vendorListService, bidderCatalog,
                singletonList(purposeStrategy));

        // when
        final Future<Collection<VendorPermission>> result = target.permissionsFor(singleton(1), tcString);

        // then
        final VendorPermission expectedVendorPermission =
                VendorPermission.of(1, "rubicon", PrivacyEnforcementAction.restrictAll());

        assertThat(result).succeededWith(singletonList(expectedVendorPermission));

        verify(purposeStrategy).getPurposeId();
        verify(purposeStrategy).processTypePurposeStrategy(
                tcString,
                purpose1,
                singletonList(VendorPermissionWithGvl.of(expectedVendorPermission, VendorV2.empty(1))));
        verify(bidderCatalog).nameByVendorId(1);
        verify(tcString).getVendorListVersion();
        verify(tcString).getPurposeOneTreatment();
        verify(vendorListService).forVersion(10);

        verifyNoMoreInteractions(tcString);
        verifyNoMoreInteractions(purposeStrategy);
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

        verify(purposeStrategy).getPurposeId();
        verify(purposeStrategy).processTypePurposeStrategy(
                tcString,
                accountPurposeOne,
                singletonList(VendorPermissionWithGvl.of(expectedVendorPermission, VendorV2.empty(null))));
        verify(tcString).getVendorListVersion();
        verify(vendorListService).forVersion(10);

        verifyNoMoreInteractions(tcString);
        verifyZeroInteractions(purposeStrategy);
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

        verify(purposeStrategy).getPurposeId();
        verify(purposeStrategy).processTypePurposeStrategy(
                tcString,
                purpose1,
                asList(expectedVendorPermissionWitGvl2, expectedVendorPermissionWitGvl1));
        verify(vendorIdResolver, times(2)).resolve(anyString());
        verify(tcString).getVendorListVersion();

        verifyNoMoreInteractions(tcString);
        verifyZeroInteractions(purposeStrategy);
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

        verify(purposeStrategy).getPurposeId();
        verify(purposeStrategy).processTypePurposeStrategy(tcString, purpose1,
                asList(expectedVendorPermissionWitGvl1, expectedVendorPermissionWitGvl2));
        verify(bidderCatalog, times(2)).nameByVendorId(anyInt());
        verify(tcString).getVendorListVersion();

        verifyZeroInteractions(purposeStrategy);
        verifyNoMoreInteractions(bidderCatalog);
        verifyNoMoreInteractions(tcString);
    }

    @Test
    public void permissionsForShouldReturnAllDeniedWhenP1TIIsNoAccessAllowed() {
        // given
        given(bidderCatalog.nameByVendorId(any())).willReturn("rubicon");

        given(tcString.getPurposeOneTreatment()).willReturn(true);

        target = new Tcf2Service(
                GdprConfig.builder()
                        .purposes(purposes)
                        .purposeOneTreatmentInterpretation(PurposeOneTreatmentInterpretation.noAccessAllowed)
                        .build(),
                vendorListService,
                bidderCatalog,
                singletonList(purposeStrategy));

        // when
        final Future<Collection<VendorPermission>> result = target.permissionsFor(singleton(1), tcString);

        // then
        assertThat(result).succeededWith(
                singletonList(VendorPermission.of(1, "rubicon", PrivacyEnforcementAction.restrictAll())));

        verify(purposeStrategy, never()).processTypePurposeStrategy(any(), any(), anyCollection());
    }

    @Test
    public void permissionsForShouldAllowAllWhenP1TIIsAccessAllowed() {
        // given
        given(bidderCatalog.nameByVendorId(any())).willReturn("rubicon");

        given(tcString.getPurposeOneTreatment()).willReturn(true);

        target = new Tcf2Service(
                GdprConfig.builder()
                        .purposes(purposes)
                        .purposeOneTreatmentInterpretation(PurposeOneTreatmentInterpretation.accessAllowed)
                        .build(),
                vendorListService,
                bidderCatalog,
                singletonList(purposeStrategy));

        // when
        target.permissionsFor(singleton(1), tcString);

        // then
        verify(purposeStrategy).allow(any());
        verify(purposeStrategy, never()).processTypePurposeStrategy(any(), any(), anyCollection());
    }

    @Test
    public void permissionsForShouldNotAllowAllWhenP1TIsFalseAndP1TIIsAccessAllowed() {
        // given
        given(bidderCatalog.nameByVendorId(any())).willReturn("rubicon");

        given(tcString.getPurposeOneTreatment()).willReturn(false);

        target = new Tcf2Service(
                GdprConfig.builder()
                        .purposes(purposes)
                        .purposeOneTreatmentInterpretation(PurposeOneTreatmentInterpretation.accessAllowed)
                        .build(),
                vendorListService,
                bidderCatalog,
                singletonList(purposeStrategy));

        // when
        target.permissionsFor(singleton(1), tcString);

        // then
        verify(purposeStrategy, never()).allow(any());
        verify(purposeStrategy).processTypePurposeStrategy(any(), any(), anyCollection());
    }
}
