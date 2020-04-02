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
import org.prebid.server.privacy.gdpr.tcf2stratgies.typeStrategies.NoTypeStrategy;
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

public class PurposeOneStrategyTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private BasicTypeStrategy basicTypeStrategy;

    @Mock
    private NoTypeStrategy noTypeStrategy;

    private PurposeOneStrategy target;

    @Mock
    private TCString tcString;

    @Before
    public void setUp() {
        target = new PurposeOneStrategy(basicTypeStrategy, noTypeStrategy);
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
    public void processTypePurposeStrategyShouldPassListWithEnforcementsAndExcludeBiddersToNoType() {
        // given
        final Purpose purpose = new Purpose(EnforcePurpose.no, false, Arrays.asList("b1", "b3"));
        final VendorPermission vendorPermission1 = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission2 = VendorPermission.of(2, "b1", PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission3 = VendorPermission.of(3, null, PrivacyEnforcementAction.restrictAll());
        final List<VendorPermission> vendorPermissions = Arrays.asList(vendorPermission1, vendorPermission2, vendorPermission3);

        given(noTypeStrategy.allowedByTypeStrategy(anyInt(), any(), any(), anyBoolean())).willReturn(singletonList(vendorPermission1));

        // when
        final Collection<VendorPermission> result = target.processTypePurposeStrategy(tcString, purpose, vendorPermissions);

        // then
        final VendorPermission vendorPermission1Changed = VendorPermission.of(1, null, allowBlockPixel());
        final VendorPermission vendorPermission2Changed = VendorPermission.of(2, "b1", allowBlockPixel());
        final VendorPermission vendorPermission3Changed = VendorPermission.of(3, null, PrivacyEnforcementAction.restrictAll());
        assertThat(result).usingFieldByFieldElementComparator().isEqualTo(Arrays.asList(vendorPermission1Changed, vendorPermission2Changed, vendorPermission3Changed));

        verify(noTypeStrategy).allowedByTypeStrategy(1, tcString, Arrays.asList(vendorPermission1, vendorPermission3), false);
    }

    @Test
    public void processTypePurposeStrategyShouldPassListWithEnforcements() {
        // given
        final Purpose purpose = new Purpose(EnforcePurpose.base, true, null);
        final VendorPermission vendorPermission1 = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission2 = VendorPermission.of(2, "b1", PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission3 = VendorPermission.of(3, null, PrivacyEnforcementAction.restrictAll());
        final List<VendorPermission> vendorPermissions = Arrays.asList(vendorPermission1, vendorPermission2, vendorPermission3);

        given(basicTypeStrategy.allowedByTypeStrategy(anyInt(), any(), any(), anyBoolean())).willReturn(vendorPermissions);

        // when
        final Collection<VendorPermission> result = target.processTypePurposeStrategy(tcString, purpose, vendorPermissions);

        // then
        final VendorPermission vendorPermission1Changed = VendorPermission.of(1, null, allowBlockPixel());
        final VendorPermission vendorPermission2Changed = VendorPermission.of(2, "b1", allowBlockPixel());
        final VendorPermission vendorPermission3Changed = VendorPermission.of(3, null, allowBlockPixel());
        assertThat(result).usingFieldByFieldElementComparator().isEqualTo(Arrays.asList(vendorPermission1Changed, vendorPermission2Changed, vendorPermission3Changed));

        verify(basicTypeStrategy).allowedByTypeStrategy(1, tcString, vendorPermissions, true);
    }

    @Test
    public void processTypePurposeStrategyShouldPassListWithEnforcementsAndExcludeBiddersToBaseType() {
        // given
        final Purpose purpose = new Purpose(EnforcePurpose.base, false, Arrays.asList("b1", "b3"));
        final VendorPermission vendorPermission1 = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission2 = VendorPermission.of(2, "b1", PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission3 = VendorPermission.of(3, null, PrivacyEnforcementAction.restrictAll());
        final List<VendorPermission> vendorPermissions = Arrays.asList(vendorPermission1, vendorPermission2, vendorPermission3);

        given(basicTypeStrategy.allowedByTypeStrategy(anyInt(), any(), any(), anyBoolean())).willReturn(singletonList(vendorPermission1));

        // when
        final Collection<VendorPermission> result = target.processTypePurposeStrategy(tcString, purpose, vendorPermissions);

        // then
        final VendorPermission vendorPermission1Changed = VendorPermission.of(1, null, allowBlockPixel());
        final VendorPermission vendorPermission2Changed = VendorPermission.of(2, "b1", allowBlockPixel());
        final VendorPermission vendorPermission3Changed = VendorPermission.of(3, null, PrivacyEnforcementAction.restrictAll());
        assertThat(result).usingFieldByFieldElementComparator().isEqualTo(Arrays.asList(vendorPermission1Changed, vendorPermission2Changed, vendorPermission3Changed));

        verify(basicTypeStrategy).allowedByTypeStrategy(1, tcString, Arrays.asList(vendorPermission1, vendorPermission3), false);
    }

    @Test
    public void processTypePurposeStrategyShouldPassEmptyListWithEnforcementsWhenAllBiddersAreExcluded() {
        // given
        final Purpose purpose = new Purpose(EnforcePurpose.base, null, Arrays.asList("b1", "b2", "b3", "b5", "b7"));
        final VendorPermission vendorPermission1 = VendorPermission.of(1, "b1", PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission2 = VendorPermission.of(2, "b2", PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission3 = VendorPermission.of(3, "b3", PrivacyEnforcementAction.restrictAll());
        final List<VendorPermission> vendorPermissions = Arrays.asList(vendorPermission1, vendorPermission2, vendorPermission3);

        given(basicTypeStrategy.allowedByTypeStrategy(anyInt(), any(), any(), anyBoolean())).willReturn(emptyList());

        // when
        final Collection<VendorPermission> result = target.processTypePurposeStrategy(tcString, purpose, vendorPermissions);

        // then
        final VendorPermission vendorPermission1Changed = VendorPermission.of(1, "b1", allowBlockPixel());
        final VendorPermission vendorPermission2Changed = VendorPermission.of(2, "b2", allowBlockPixel());
        final VendorPermission vendorPermission3Changed = VendorPermission.of(3, "b3", allowBlockPixel());
        assertThat(result).usingFieldByFieldElementComparator().isEqualTo(Arrays.asList(vendorPermission1Changed, vendorPermission2Changed, vendorPermission3Changed));

        verify(basicTypeStrategy).allowedByTypeStrategy(1, tcString, emptyList(), true);
    }

    private static PrivacyEnforcementAction allowBlockPixel() {
        final PrivacyEnforcementAction privacyEnforcementAction = PrivacyEnforcementAction.restrictAll();
        privacyEnforcementAction.setBlockPixelSync(false);
        return privacyEnforcementAction;
    }
}
