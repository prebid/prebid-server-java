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
import org.prebid.server.privacy.gdpr.model.GdprPurpose;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.VendorPermission;
import org.prebid.server.privacy.gdpr.tcf2stratgies.PurposeStrategy;
import org.prebid.server.settings.model.EnforcePurpose;
import org.prebid.server.settings.model.GdprConfig;
import org.prebid.server.settings.model.Purpose;
import org.prebid.server.settings.model.Purposes;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

public class Tcf2ServiceTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private BidderCatalog bidderCatalog;
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
        given(purposeStrategy.getPurposeId()).willReturn(1);
        given(bidderCatalog.isActive(any())).willReturn(true);
        given(bidderCatalog.vendorIdByName(any())).willReturn(null);

        initPurposes();
        initGdpr();

        target = new Tcf2Service(gdprConfig, bidderCatalog, singletonList(purposeStrategy));
    }

    private void initPurposes() {
        purpose1 = new Purpose(EnforcePurpose.base, true, emptyList());
        purposes = Purposes.builder()
                .p1(purpose1)
                .build();
    }

    private void initGdpr() {
        gdprConfig = GdprConfig.builder()
                .defaultValue("1")
                .enabled(true)
                .purposes(purposes)
                .build();
    }

    @Test
    public void permissionsForShouldReturnByGdprPurpose() {
        // given
        target = new Tcf2Service(GdprConfig.builder().purposes(purposes).build(), bidderCatalog, singletonList(purposeStrategy));

        // when
        final Set<GdprPurpose> firstGdprPurpose = singleton(GdprPurpose.informationStorageAndAccess);
        final Future<Collection<VendorPermission>> result = target.permissionsFor(tcString, singleton(1), emptySet(), firstGdprPurpose);

        // then
        final VendorPermission expectedVendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        assertThat(result.result()).containsOnly(expectedVendorPermission);

        verify(purposeStrategy).getPurposeId();
        verify(purposeStrategy).processTypePurposeStrategy(tcString, purpose1, singletonList(expectedVendorPermission));

        verifyZeroInteractions(tcString);
        verifyZeroInteractions(bidderCatalog);
        verifyZeroInteractions(purposeStrategy);
    }

    @Test
    public void permissionsForShouldMergeBidderNamesAndVendorIds() {
        // given
        target = new Tcf2Service(GdprConfig.builder().purposes(purposes).build(), bidderCatalog, singletonList(purposeStrategy));

        final String bidderNameWithVendor = "b1";
        final Set<Integer> vendorIds = singleton(1);
        final Set<String> bidderNames = new HashSet<>(Arrays.asList(bidderNameWithVendor, "b2"));
        given(bidderCatalog.vendorIdByName(bidderNameWithVendor)).willReturn(1);

        // when
        final Set<GdprPurpose> firstGdprPurpose = singleton(GdprPurpose.informationStorageAndAccess);
        final Future<Collection<VendorPermission>> result = target.permissionsFor(tcString, vendorIds, bidderNames, firstGdprPurpose);

        // then
        final VendorPermission expectedVendorPermission1 = VendorPermission.of(1, bidderNameWithVendor, PrivacyEnforcementAction.restrictAll());
        final VendorPermission expectedVendorPermission2 = VendorPermission.of(null, "b2", PrivacyEnforcementAction.restrictAll());
        assertThat(result.result()).containsOnly(expectedVendorPermission1, expectedVendorPermission2);

        verify(purposeStrategy).getPurposeId();
        verify(purposeStrategy).processTypePurposeStrategy(tcString, purpose1, Arrays.asList(expectedVendorPermission2, expectedVendorPermission1));
        verify(bidderCatalog, times(2)).isActive(anyString());
        verify(bidderCatalog, times(2)).vendorIdByName(anyString());

        verifyZeroInteractions(tcString);
        verifyZeroInteractions(purposeStrategy);
    }
}

