package org.prebid.server.privacy.gdpr.tcfstrategies.purpose.typestrategies;

import com.iabtcf.decoder.TCString;
import com.iabtcf.utils.IntIterable;
import com.iabtcf.v2.PublisherRestriction;
import com.iabtcf.v2.RestrictionType;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.VendorPermission;
import org.prebid.server.privacy.gdpr.model.VendorPermissionWithGvl;
import org.prebid.server.privacy.gdpr.vendorlist.proto.PurposeCode;
import org.prebid.server.privacy.gdpr.vendorlist.proto.VendorV2;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

public class FullEnforcePurposeStrategyTest {

    private static final PurposeCode PURPOSE_CODE = PurposeCode.ONE;

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private FullEnforcePurposeStrategy target;

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
    @Mock
    private IntIterable vendorIds;

    @Mock
    private PublisherRestriction publisherRestriction;

    @Before
    public void setUp() {
        given(tcString.getVendorConsent()).willReturn(allowedVendors);
        given(tcString.getVendorLegitimateInterest()).willReturn(allowedVendorsLI);
        given(tcString.getPurposesConsent()).willReturn(purposesConsent);
        given(tcString.getPurposesLITransparency()).willReturn(purposesLI);

        given(tcString.getPublisherRestrictions()).willReturn(singletonList(publisherRestriction));

        given(publisherRestriction.getPurposeId()).willReturn(PURPOSE_CODE.code());
        given(publisherRestriction.getVendorIds()).willReturn(vendorIds);
        given(publisherRestriction.getRestrictionType()).willReturn(RestrictionType.UNDEFINED);

        given(allowedVendors.contains(anyInt())).willReturn(false);
        given(allowedVendorsLI.contains(anyInt())).willReturn(false);
        given(purposesConsent.contains(anyInt())).willReturn(false);
        given(purposesLI.contains(anyInt())).willReturn(false);
        given(vendorIds.contains(anyInt())).willReturn(false);

        target = new FullEnforcePurposeStrategy();
    }

    @Test
    public void shouldReturnOnlyExcludedAllowedWhenMultiplePublisherRestrictionsProvided() {
        // given
        final IntIterable requireConsentIterable = mock(IntIterable.class);
        final PublisherRestriction publisherRestriction1 = new PublisherRestriction(PURPOSE_CODE.code(),
                RestrictionType.REQUIRE_CONSENT, requireConsentIterable);
        given(requireConsentIterable.spliterator()).willReturn(singletonList(1).spliterator());

        final IntIterable notAllowedIterable = mock(IntIterable.class);
        final PublisherRestriction publisherRestriction2 = new PublisherRestriction(PURPOSE_CODE.code(),
                RestrictionType.NOT_ALLOWED, notAllowedIterable);
        given(notAllowedIterable.spliterator()).willReturn(Arrays.asList(4, 2).spliterator());

        given(tcString.getPublisherRestrictions()).willReturn(
                Arrays.asList(publisherRestriction1, publisherRestriction2));

        final VendorPermission vendorPermission1 = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission2 = VendorPermission.of(2, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission3 = VendorPermission.of(3, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission4 = VendorPermission.of(4, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission5 = VendorPermission.of(5, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl requireConsentPermission = VendorPermissionWithGvl.of(vendorPermission1,
                VendorV2.empty(1));
        final VendorPermissionWithGvl notAllowedPermission = VendorPermissionWithGvl.of(vendorPermission2,
                VendorV2.empty(2));
        final VendorPermissionWithGvl excludedNotMentionedPermission = VendorPermissionWithGvl.of(vendorPermission3,
                VendorV2.empty(3));
        final VendorPermissionWithGvl excludedNotAllowedPermission = VendorPermissionWithGvl.of(vendorPermission4,
                VendorV2.empty(4));
        final VendorPermissionWithGvl notMentionedPermission = VendorPermissionWithGvl.of(vendorPermission5,
                VendorV2.empty(5));

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_CODE, tcString,
                Arrays.asList(requireConsentPermission, notAllowedPermission, notMentionedPermission),
                Arrays.asList(excludedNotMentionedPermission, excludedNotAllowedPermission), true);

        // then
        assertThat(result).usingFieldByFieldElementComparator().containsOnly(vendorPermission3);
    }

    @Test
    public void shouldReturnExpectedWhenMultiplePublisherRestrictionsProvided() {
        // given
        final IntIterable requireConsentIterable = mock(IntIterable.class);
        final PublisherRestriction publisherRestriction1 = new PublisherRestriction(PURPOSE_CODE.code(),
                RestrictionType.REQUIRE_CONSENT, requireConsentIterable);
        given(requireConsentIterable.spliterator()).willReturn(singletonList(1).spliterator());
        given(requireConsentIterable.contains(eq(1))).willReturn(true);

        final IntIterable notAllowedIterable = mock(IntIterable.class);
        final PublisherRestriction publisherRestriction2 = new PublisherRestriction(PURPOSE_CODE.code(),
                RestrictionType.NOT_ALLOWED, notAllowedIterable);
        given(notAllowedIterable.spliterator()).willReturn(Arrays.asList(4, 2).spliterator());
        given(notAllowedIterable.contains(eq(4))).willReturn(true);
        given(notAllowedIterable.contains(eq(2))).willReturn(true);

        given(tcString.getPublisherRestrictions()).willReturn(
                Arrays.asList(publisherRestriction1, publisherRestriction2));

        final VendorPermission vendorPermission1 = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission2 = VendorPermission.of(2, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission3 = VendorPermission.of(3, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission4 = VendorPermission.of(4, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission5 = VendorPermission.of(5, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl requireConsentPermission = VendorPermissionWithGvl.of(vendorPermission1,
                VendorV2.builder().id(1).purposes(EnumSet.of(PURPOSE_CODE)).build());
        final VendorPermissionWithGvl notAllowedPermission = VendorPermissionWithGvl.of(vendorPermission2,
                VendorV2.builder().id(2).purposes(EnumSet.of(PURPOSE_CODE)).build());
        final VendorPermissionWithGvl excludedNotMentionedPermission = VendorPermissionWithGvl.of(vendorPermission3,
                VendorV2.builder().id(3).purposes(EnumSet.of(PURPOSE_CODE)).build());
        final VendorPermissionWithGvl excludedNotAllowedPermission = VendorPermissionWithGvl.of(vendorPermission4,
                VendorV2.builder().id(4).purposes(EnumSet.of(PURPOSE_CODE)).build());
        final VendorPermissionWithGvl notMentionedPermission = VendorPermissionWithGvl.of(vendorPermission5,
                VendorV2.builder().id(5).purposes(EnumSet.of(PURPOSE_CODE)).build());

        given(purposesConsent.contains(anyInt())).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_CODE, tcString,
                Arrays.asList(requireConsentPermission, notAllowedPermission, notMentionedPermission),
                Arrays.asList(excludedNotMentionedPermission, excludedNotAllowedPermission), false);

        // then
        assertThat(result).usingFieldByFieldElementComparator().containsOnly(vendorPermission1, vendorPermission3,
                vendorPermission5);
    }

    // GVL Purpose part

    @Test
    public void shouldAllowWhenInGvlPurposeAndPurposeConsentAllowed() {
        // given
        final VendorV2 vendorGvl = VendorV2.builder()
                .purposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.noneOf(PurposeCode.class))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = VendorPermissionWithGvl.of(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        given(purposesConsent.contains(anyInt())).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_CODE, tcString,
                vendorPermissionWithGvls, emptyList(), false);

        // then
        assertThat(result).usingFieldByFieldElementComparator().containsOnly(vendorPermission);

        verify(purposesConsent).contains(PURPOSE_CODE.code());
    }

    @Test
    public void shouldAllowWhenInGvlPurposeAndPurposeConsentAllowedAndRequireConsent() {
        // given
        final VendorV2 vendorGvl = VendorV2.builder()
                .purposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.noneOf(PurposeCode.class))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = VendorPermissionWithGvl.of(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_CONSENT);

        given(purposesConsent.contains(anyInt())).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_CODE, tcString,
                vendorPermissionWithGvls, emptyList(), false);

        // then
        assertThat(result).usingFieldByFieldElementComparator().containsOnly(vendorPermission);

        verify(purposesConsent).contains(PURPOSE_CODE.code());
    }

    @Test
    public void shouldEmptyWhenInGvlPurposeAndPurposeConsentAllowedAndRequireLI() {
        // given
        final VendorV2 vendorGvl = VendorV2.builder()
                .purposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.noneOf(PurposeCode.class))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = VendorPermissionWithGvl.of(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_LEGITIMATE_INTEREST);

        given(purposesConsent.contains(anyInt())).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_CODE, tcString,
                vendorPermissionWithGvls, emptyList(), false);

        // then
        assertThat(result).isEmpty();

        verifyNoInteractions(purposesConsent);
    }

    @Test
    public void shouldEmptyWhenInGvlPurposeAndPurposeLIAllowed() {
        // given
        final VendorV2 vendorGvl = VendorV2.builder()
                .purposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.noneOf(PurposeCode.class))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = VendorPermissionWithGvl.of(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        given(purposesLI.contains(anyInt())).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_CODE, tcString,
                vendorPermissionWithGvls, emptyList(), false);

        // then
        assertThat(result).isEmpty();

        verify(purposesConsent).contains(PURPOSE_CODE.code());
        verifyNoInteractions(purposesLI);
    }

    @Test
    public void shouldAllowWhenInGvlPurposeAndPurposeConsentAllowedAndVendorConsentAllowedAndEnforced() {
        // given
        final VendorV2 vendorGvl = VendorV2.builder()
                .purposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.noneOf(PurposeCode.class))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = VendorPermissionWithGvl.of(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        given(purposesConsent.contains(anyInt())).willReturn(true);
        given(allowedVendors.contains(anyInt())).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_CODE, tcString,
                vendorPermissionWithGvls, emptyList(), true);

        // then
        assertThat(result).usingFieldByFieldElementComparator().containsOnly(vendorPermission);

        verify(purposesConsent).contains(PURPOSE_CODE.code());
        verify(allowedVendors).contains(1);
    }

    @Test
    public void shouldEmptyWhenInGvlPurposeAndPurposeConsentAllowedAndVendorLIAllowedAndEnforced() {
        // given
        final VendorV2 vendorGvl = VendorV2.builder()
                .purposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.noneOf(PurposeCode.class))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = VendorPermissionWithGvl.of(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        given(purposesConsent.contains(anyInt())).willReturn(true);
        given(allowedVendorsLI.contains(anyInt())).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_CODE, tcString,
                vendorPermissionWithGvls, emptyList(), true);

        // then
        assertThat(result).isEmpty();

        verify(purposesConsent).contains(PURPOSE_CODE.code());
        verifyNoInteractions(purposesLI);
    }

    // GVL Legitimate interest Purpose part

    @Test
    public void shouldAllowWhenInGvlPurposeLIAndPurposeLI() {
        // given
        final VendorV2 vendorGvl = VendorV2.builder()
                .legIntPurposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.noneOf(PurposeCode.class))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = VendorPermissionWithGvl.of(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        given(purposesLI.contains(anyInt())).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_CODE, tcString,
                vendorPermissionWithGvls, emptyList(), false);

        // then
        assertThat(result).usingFieldByFieldElementComparator().containsOnly(vendorPermission);

        verify(purposesLI).contains(PURPOSE_CODE.code());
    }

    @Test
    public void shouldAllowWhenInGvlPurposeLIAndPurposeLIAndRequireLI() {
        // given
        final VendorV2 vendorGvl = VendorV2.builder()
                .legIntPurposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.noneOf(PurposeCode.class))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = VendorPermissionWithGvl.of(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_LEGITIMATE_INTEREST);

        given(purposesLI.contains(anyInt())).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_CODE, tcString,
                vendorPermissionWithGvls, emptyList(), false);

        // then
        assertThat(result).usingFieldByFieldElementComparator().containsOnly(vendorPermission);

        verify(purposesLI).contains(PURPOSE_CODE.code());
    }

    @Test
    public void shouldEmptyWhenInGvlPurposeLIAndPurposeLIAndRequireConsent() {
        // given
        final VendorV2 vendorGvl = VendorV2.builder()
                .legIntPurposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.noneOf(PurposeCode.class))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = VendorPermissionWithGvl.of(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        RestrictionType requireConsent = RestrictionType.REQUIRE_CONSENT;
        setRestriction(requireConsent);

        given(purposesLI.contains(anyInt())).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_CODE, tcString,
                vendorPermissionWithGvls, emptyList(), false);

        // then
        assertThat(result).isEmpty();

        verifyNoInteractions(purposesLI);
    }

    @Test
    public void shouldAllowWhenInGvlPurposeLIAndPurposeLIAndVendorLIAllowedAndEnforced() {
        // given
        final VendorV2 vendorGvl = VendorV2.builder()
                .legIntPurposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.noneOf(PurposeCode.class))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = VendorPermissionWithGvl.of(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        given(purposesLI.contains(anyInt())).willReturn(true);
        given(allowedVendorsLI.contains(anyInt())).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_CODE, tcString,
                vendorPermissionWithGvls, emptyList(), true);

        // then
        assertThat(result).usingFieldByFieldElementComparator().containsOnly(vendorPermission);

        verify(purposesLI).contains(PURPOSE_CODE.code());
        verify(allowedVendorsLI).contains(1);
    }

    @Test
    public void shouldEmptyWhenInGvlPurposeLIAndPurposeConsentAllowedAndVendorConsentAllowedAndEnforced() {
        // given
        final VendorV2 vendorGvl = VendorV2.builder()
                .legIntPurposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.noneOf(PurposeCode.class))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = VendorPermissionWithGvl.of(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        given(purposesLI.contains(anyInt())).willReturn(true);
        given(purposesConsent.contains(anyInt())).willReturn(true);
        given(allowedVendors.contains(anyInt())).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_CODE, tcString,
                vendorPermissionWithGvls, emptyList(), true);

        // then
        assertThat(result).isEmpty();

        verify(purposesLI).contains(PURPOSE_CODE.code());
        verifyNoInteractions(allowedVendors);
    }

    // Flexible GVL Purpose part

    // Restriction type is REQUIRE_CONSENT part

    @Test
    public void shouldAllowWhenInGvlPurposeAndPurposeConsentAllowedAndFlexibleAndRequireConsent() {
        // given
        final VendorV2 vendorGvl = VendorV2.builder()
                .purposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.of(PURPOSE_CODE))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = VendorPermissionWithGvl.of(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_CONSENT);

        given(purposesConsent.contains(anyInt())).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_CODE, tcString,
                vendorPermissionWithGvls, emptyList(), false);

        // then
        assertThat(result).usingFieldByFieldElementComparator().containsOnly(vendorPermission);

        verify(purposesConsent).contains(PURPOSE_CODE.code());
    }

    @Test
    public void shouldAllowWhenInGvlPurposeAndPurposeConsentAndVendorConsentAndEnforcedAndFlexibleAndRequireConsent() {
        // given
        final VendorV2 vendorGvl = VendorV2.builder()
                .purposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.of(PURPOSE_CODE))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = VendorPermissionWithGvl.of(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_CONSENT);

        given(purposesConsent.contains(anyInt())).willReturn(true);
        given(allowedVendors.contains(anyInt())).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_CODE, tcString,
                vendorPermissionWithGvls, emptyList(), true);

        // then
        assertThat(result).usingFieldByFieldElementComparator().containsOnly(vendorPermission);

        verify(purposesConsent).contains(PURPOSE_CODE.code());
        verify(allowedVendors).contains(1);
    }

    @Test
    public void shouldEmptyWhenInGvlPurposeAndPurposeLIAllowedAndFlexibleAndRequireConsent() {
        // given
        final VendorV2 vendorGvl = VendorV2.builder()
                .purposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.of(PURPOSE_CODE))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = VendorPermissionWithGvl.of(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_CONSENT);

        given(purposesLI.contains(anyInt())).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_CODE, tcString,
                vendorPermissionWithGvls, emptyList(), false);

        // then
        assertThat(result).isEmpty();

        verifyNoInteractions(purposesLI);
    }

    @Test
    public void shouldEmptyWhenInGvlPurposeAndPurposeLIAndVendorLIAllowedAndEnforcedAndFlexibleAndRequireConsent() {
        // given
        final VendorV2 vendorGvl = VendorV2.builder()
                .purposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.of(PURPOSE_CODE))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = VendorPermissionWithGvl.of(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_CONSENT);

        given(purposesLI.contains(anyInt())).willReturn(true);
        given(allowedVendorsLI.contains(anyInt())).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_CODE, tcString,
                vendorPermissionWithGvls, emptyList(), true);

        // then
        assertThat(result).isEmpty();

        verifyNoInteractions(purposesLI);
        verifyNoInteractions(allowedVendorsLI);
    }

    @Test
    public void shouldEmptyWhenInGvlPurposeAndPurposeConsentAndVendorLIAndEnforcedAndFlexibleAndRequireConsent() {
        // given
        final VendorV2 vendorGvl = VendorV2.builder()
                .purposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.of(PURPOSE_CODE))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = VendorPermissionWithGvl.of(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_CONSENT);

        given(purposesConsent.contains(anyInt())).willReturn(true);
        given(allowedVendorsLI.contains(anyInt())).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_CODE, tcString,
                vendorPermissionWithGvls, emptyList(), true);

        // then
        assertThat(result).isEmpty();

        verify(purposesConsent).contains(PURPOSE_CODE.code());
    }

    @Test
    public void shouldEmptyWhenInGvlPurposeAndPurposeLIAndVendorConsentAndEnforcedAndFlexibleAndRequireConsent() {
        // given
        final VendorV2 vendorGvl = VendorV2.builder()
                .purposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.of(PURPOSE_CODE))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = VendorPermissionWithGvl.of(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_CONSENT);

        given(purposesLI.contains(anyInt())).willReturn(true);
        given(allowedVendors.contains(anyInt())).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_CODE, tcString,
                vendorPermissionWithGvls, emptyList(), true);

        // then
        assertThat(result).isEmpty();
    }

    // Restriction type is REQUIRE_LEGITIMATE_INTEREST part

    @Test
    public void shouldEmptyWhenInGvlPurposeAndPurposeConsentAllowedAndFlexibleAndRequireLI() {
        // given
        final VendorV2 vendorGvl = VendorV2.builder()
                .purposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.of(PURPOSE_CODE))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = VendorPermissionWithGvl.of(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_LEGITIMATE_INTEREST);

        given(purposesConsent.contains(anyInt())).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_CODE, tcString,
                vendorPermissionWithGvls, emptyList(), false);

        // then
        assertThat(result).isEmpty();

        verifyNoInteractions(purposesConsent);
    }

    @Test
    public void shouldEmptyWhenInGvlPurposeAndPurposeConsentAndVendorConsentAndEnforcedAndFlexibleAndRequireLI() {
        // given
        final VendorV2 vendorGvl = VendorV2.builder()
                .purposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.of(PURPOSE_CODE))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = VendorPermissionWithGvl.of(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_LEGITIMATE_INTEREST);

        given(purposesConsent.contains(anyInt())).willReturn(true);
        given(allowedVendors.contains(anyInt())).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_CODE, tcString,
                vendorPermissionWithGvls, emptyList(), true);

        // then
        assertThat(result).isEmpty();

        verifyNoInteractions(purposesConsent);
        verifyNoInteractions(allowedVendors);
    }

    @Test
    public void shouldAllowWhenInGvlPurposeAndPurposeLIAllowedAndFlexibleAndRequireLI() {
        // given
        final VendorV2 vendorGvl = VendorV2.builder()
                .purposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.of(PURPOSE_CODE))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = VendorPermissionWithGvl.of(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_LEGITIMATE_INTEREST);

        given(purposesLI.contains(anyInt())).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_CODE, tcString,
                vendorPermissionWithGvls, emptyList(), false);

        // then
        assertThat(result).usingFieldByFieldElementComparator().containsOnly(vendorPermission);

        verify(purposesLI).contains(PURPOSE_CODE.code());
    }

    @Test
    public void shouldAllowWhenInGvlPurposeAndPurposeLIAndVendorLIAllowedAndEnforcedAndFlexibleAndRequireLI() {
        // given
        final VendorV2 vendorGvl = VendorV2.builder()
                .purposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.of(PURPOSE_CODE))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = VendorPermissionWithGvl.of(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_LEGITIMATE_INTEREST);

        given(purposesLI.contains(anyInt())).willReturn(true);
        given(allowedVendorsLI.contains(anyInt())).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_CODE, tcString,
                vendorPermissionWithGvls, emptyList(), true);

        // then
        assertThat(result).usingFieldByFieldElementComparator().containsOnly(vendorPermission);

        verify(purposesLI).contains(PURPOSE_CODE.code());
        verify(allowedVendorsLI).contains(1);
    }

    @Test
    public void shouldEmptyWhenInGvlPurposeAndPurposeConsentAndVendorLIAllowedAndEnforcedAndFlexibleAndRequireLI() {
        // given
        final VendorV2 vendorGvl = VendorV2.builder()
                .purposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.of(PURPOSE_CODE))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = VendorPermissionWithGvl.of(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_LEGITIMATE_INTEREST);

        given(purposesConsent.contains(anyInt())).willReturn(true);
        given(allowedVendorsLI.contains(anyInt())).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_CODE, tcString,
                vendorPermissionWithGvls, emptyList(), true);

        // then
        assertThat(result).isEmpty();

        verify(allowedVendorsLI).contains(1);
        verifyNoInteractions(purposesConsent);
    }

    @Test
    public void shouldEmptyWhenInGvlPurposeAndPurposeLIAndVendorConsentAllowedAndEnforcedAndFlexibleAndRequireLI() {
        // given
        final VendorV2 vendorGvl = VendorV2.builder()
                .purposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.of(PURPOSE_CODE))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = VendorPermissionWithGvl.of(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_LEGITIMATE_INTEREST);

        given(purposesLI.contains(anyInt())).willReturn(true);
        given(allowedVendors.contains(anyInt())).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_CODE, tcString,
                vendorPermissionWithGvls, emptyList(), true);

        // then
        assertThat(result).isEmpty();

        verify(purposesLI).contains(PURPOSE_CODE.code());
        verifyNoInteractions(allowedVendors);
    }

    // Flexible GVL Purpose Legitimate interest part

    // Restriction type is REQUIRE_CONSENT part

    @Test
    public void shouldAllowWhenInGvlPurposeLIAndPurposeConsentAllowedAndFlexibleAndRequireConsent() {
        // given
        final VendorV2 vendorGvl = VendorV2.builder()
                .legIntPurposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.of(PURPOSE_CODE))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = VendorPermissionWithGvl.of(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_CONSENT);

        given(purposesConsent.contains(anyInt())).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_CODE, tcString,
                vendorPermissionWithGvls, emptyList(), false);

        // then
        assertThat(result).usingFieldByFieldElementComparator().containsOnly(vendorPermission);

        verify(purposesConsent).contains(PURPOSE_CODE.code());
    }

    @Test
    public void shouldAllowWhenInGvlPurposeLIAndPurposeAndVendorConsentAndEnforcedAndFlexibleAndRequireConsent() {
        // given
        final VendorV2 vendorGvl = VendorV2.builder()
                .legIntPurposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.of(PURPOSE_CODE))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = VendorPermissionWithGvl.of(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_CONSENT);

        given(purposesConsent.contains(anyInt())).willReturn(true);
        given(allowedVendors.contains(anyInt())).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_CODE, tcString,
                vendorPermissionWithGvls, emptyList(), true);

        // then
        assertThat(result).usingFieldByFieldElementComparator().containsOnly(vendorPermission);

        verify(purposesConsent).contains(PURPOSE_CODE.code());
        verify(allowedVendors).contains(1);
    }

    @Test
    public void shouldEmptyWhenInGvlPurposeLIAndPurposeLIAllowedAndFlexibleAndRequireConsent() {
        // given
        final VendorV2 vendorGvl = VendorV2.builder()
                .legIntPurposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.of(PURPOSE_CODE))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = VendorPermissionWithGvl.of(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_CONSENT);

        given(purposesLI.contains(anyInt())).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_CODE, tcString,
                vendorPermissionWithGvls, emptyList(), false);

        // then
        assertThat(result).isEmpty();

        verifyNoInteractions(purposesLI);
    }

    @Test
    public void shouldEmptyWhenInGvlPurposeLIAndPurposeLIAndVendorLIAllowedAndEnforcedAndFlexibleAndRequireConsent() {
        // given
        final VendorV2 vendorGvl = VendorV2.builder()
                .legIntPurposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.of(PURPOSE_CODE))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = VendorPermissionWithGvl.of(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_CONSENT);

        given(purposesLI.contains(anyInt())).willReturn(true);
        given(allowedVendorsLI.contains(anyInt())).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_CODE, tcString,
                vendorPermissionWithGvls, emptyList(), true);

        // then
        assertThat(result).isEmpty();

        verifyNoInteractions(purposesLI);
        verifyNoInteractions(allowedVendorsLI);
    }

    @Test
    public void shouldEmptyWhenInGvlPurposeLIAndPurposeConsentAndVendorLIAndEnforcedAndFlexibleAndRequireConsent() {
        // given
        final VendorV2 vendorGvl = VendorV2.builder()
                .legIntPurposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.of(PURPOSE_CODE))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = VendorPermissionWithGvl.of(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_CONSENT);

        given(purposesConsent.contains(anyInt())).willReturn(true);
        given(allowedVendorsLI.contains(anyInt())).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_CODE, tcString,
                vendorPermissionWithGvls, emptyList(), true);

        // then
        assertThat(result).isEmpty();

        verify(purposesConsent).contains(PURPOSE_CODE.code());
        verifyNoInteractions(allowedVendorsLI);
    }

    @Test
    public void shouldEmptyWhenInGvlPurposeLIAndPurposeLIAndVendorConsentAndEnforcedAndFlexibleAndRequireConsent() {
        // given
        final VendorV2 vendorGvl = VendorV2.builder()
                .legIntPurposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.of(PURPOSE_CODE))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = VendorPermissionWithGvl.of(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_CONSENT);

        given(purposesLI.contains(anyInt())).willReturn(true);
        given(allowedVendors.contains(anyInt())).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_CODE, tcString,
                vendorPermissionWithGvls, emptyList(), true);

        // then
        assertThat(result).isEmpty();

        verify(allowedVendors).contains(PURPOSE_CODE.code());
        verifyNoInteractions(purposesLI);
    }

    // Restriction type is REQUIRE_LEGITIMATE_INTEREST part

    @Test
    public void shouldEmptyWhenInGvlPurposeLIAndPurposeConsentAllowedAndFlexibleAndRequireLI() {
        // given
        final VendorV2 vendorGvl = VendorV2.builder()
                .legIntPurposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.of(PURPOSE_CODE))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = VendorPermissionWithGvl.of(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_LEGITIMATE_INTEREST);

        given(purposesConsent.contains(anyInt())).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_CODE, tcString,
                vendorPermissionWithGvls, emptyList(), false);

        // then
        assertThat(result).isEmpty();

        verifyNoInteractions(purposesConsent);
    }

    @Test
    public void shouldEmptyWhenInGvlPurposeLIAndPurposeConsentAndVendorConsentAndEnforcedAndFlexibleAndRequireLI() {
        // given
        final VendorV2 vendorGvl = VendorV2.builder()
                .legIntPurposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.of(PURPOSE_CODE))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = VendorPermissionWithGvl.of(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_LEGITIMATE_INTEREST);

        given(purposesConsent.contains(anyInt())).willReturn(true);
        given(allowedVendors.contains(anyInt())).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_CODE, tcString,
                vendorPermissionWithGvls, emptyList(), true);

        // then
        assertThat(result).isEmpty();

        verifyNoInteractions(purposesConsent);
        verifyNoInteractions(allowedVendors);
    }

    @Test
    public void shouldAllowWhenInGvlPurposeLIAndPurposeLIAllowedAndFlexibleAndRequireLI() {
        // given
        final VendorV2 vendorGvl = VendorV2.builder()
                .legIntPurposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.of(PURPOSE_CODE))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = VendorPermissionWithGvl.of(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_LEGITIMATE_INTEREST);

        given(purposesLI.contains(anyInt())).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_CODE, tcString,
                vendorPermissionWithGvls, emptyList(), false);

        // then
        assertThat(result).usingFieldByFieldElementComparator().containsOnly(vendorPermission);

        verify(purposesLI).contains(PURPOSE_CODE.code());
    }

    @Test
    public void shouldAllowWhenInGvlPurposeLIAndPurposeLIAndVendorLIAllowedAndEnforcedAndFlexibleAndRequireLI() {
        // given
        final VendorV2 vendorGvl = VendorV2.builder()
                .legIntPurposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.of(PURPOSE_CODE))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = VendorPermissionWithGvl.of(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_LEGITIMATE_INTEREST);

        given(purposesLI.contains(anyInt())).willReturn(true);
        given(allowedVendorsLI.contains(anyInt())).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_CODE, tcString,
                vendorPermissionWithGvls, emptyList(), true);

        // then
        assertThat(result).usingFieldByFieldElementComparator().containsOnly(vendorPermission);

        verify(purposesLI).contains(PURPOSE_CODE.code());
        verify(allowedVendorsLI).contains(1);
    }

    @Test
    public void shouldEmptyWhenInGvlPurposeLIAndPurposeConsentAndVendorLIAllowedAndEnforcedAndFlexibleAndRequireLI() {
        // given
        final VendorV2 vendorGvl = VendorV2.builder()
                .legIntPurposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.of(PURPOSE_CODE))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = VendorPermissionWithGvl.of(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_LEGITIMATE_INTEREST);

        given(purposesConsent.contains(anyInt())).willReturn(true);
        given(allowedVendorsLI.contains(anyInt())).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_CODE, tcString,
                vendorPermissionWithGvls, emptyList(), true);

        // then
        assertThat(result).isEmpty();

        verify(allowedVendorsLI).contains(1);
        verifyNoInteractions(purposesConsent);
    }

    @Test
    public void shouldEmptyWhenInGvlPurposeLIAndPurposeLIAndVendorConsentAllowedAndEnforcedAndFlexibleAndRequireLI() {
        // given
        final VendorV2 vendorGvl = VendorV2.builder()
                .legIntPurposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.of(PURPOSE_CODE))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = VendorPermissionWithGvl.of(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_LEGITIMATE_INTEREST);

        given(purposesLI.contains(anyInt())).willReturn(true);
        given(allowedVendors.contains(anyInt())).willReturn(true);

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_CODE, tcString,
                vendorPermissionWithGvls, emptyList(), true);

        // then
        assertThat(result).isEmpty();

        verify(purposesLI).contains(1);
        verifyNoInteractions(allowedVendors);
    }

    @Test
    public void shouldReturnExcludedVendors() {
        // given
        final VendorPermission vendorPermission1 = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission2 = VendorPermission.of(2, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl1 = VendorPermissionWithGvl.of(vendorPermission1,
                VendorV2.empty(1));
        final VendorPermissionWithGvl vendorPermissionWitGvl2 = VendorPermissionWithGvl.of(vendorPermission2,
                VendorV2.empty(2));

        // when
        final Collection<VendorPermission> result = target.allowedByTypeStrategy(PURPOSE_CODE, tcString,
                singleton(vendorPermissionWitGvl1), singleton(vendorPermissionWitGvl2), true);

        // then
        assertThat(result).usingFieldByFieldElementComparator().containsOnly(vendorPermission2);
    }

    private void setRestriction(RestrictionType requireConsent) {
        given(publisherRestriction.getRestrictionType()).willReturn(requireConsent);
        given(vendorIds.contains(anyInt())).willReturn(true);
    }
}
