package org.prebid.server.privacy.gdpr.tcfstrategies.purpose.typestrategies;

import com.iabtcf.decoder.TCString;
import com.iabtcf.utils.IntIterable;
import com.iabtcf.utils.IntIterator;
import com.iabtcf.v2.PublisherRestriction;
import com.iabtcf.v2.RestrictionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.VendorPermission;
import org.prebid.server.privacy.gdpr.model.VendorPermissionWithGvl;
import org.prebid.server.privacy.gdpr.vendorlist.proto.PurposeCode;
import org.prebid.server.privacy.gdpr.vendorlist.proto.Vendor;

import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
public class FullEnforcePurposeStrategyTest {

    private static final PurposeCode PURPOSE_CODE = PurposeCode.ONE;

    private FullEnforcePurposeStrategy target;

    @Mock(strictness = LENIENT)
    private TCString tcString;
    @Mock(strictness = LENIENT)
    private IntIterable allowedVendors;
    @Mock(strictness = LENIENT)
    private IntIterable allowedVendorsLI;
    @Mock(strictness = LENIENT)
    private IntIterable purposesConsent;
    @Mock(strictness = LENIENT)
    private IntIterable purposesLI;
    @Spy
    private IntIterable vendorIds;

    @Mock(strictness = LENIENT)
    private PublisherRestriction publisherRestriction;

    @BeforeEach
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

        target = new FullEnforcePurposeStrategy();
    }

    @Test
    public void shouldReturnOnlyExcludedAllowedWhenMultiplePublisherRestrictionsProvided() {
        // given
        final IntIterable requireConsentIterable = spy(IntIterable.class);
        final PublisherRestriction publisherRestriction1 = new PublisherRestriction(
                PURPOSE_CODE.code(), RestrictionType.REQUIRE_CONSENT, requireConsentIterable);
        given(requireConsentIterable.intIterator()).willReturn(intIterator(1));

        final IntIterable notAllowedIterable = spy(IntIterable.class);
        final PublisherRestriction publisherRestriction2 = new PublisherRestriction(
                PURPOSE_CODE.code(), RestrictionType.NOT_ALLOWED, notAllowedIterable);
        given(notAllowedIterable.intIterator()).willReturn(intIterator(4, 2));

        given(tcString.getPublisherRestrictions()).willReturn(asList(publisherRestriction1, publisherRestriction2));

        final VendorPermission vendorPermission1 = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission2 = VendorPermission.of(2, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission3 = VendorPermission.of(3, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission4 = VendorPermission.of(4, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission5 = VendorPermission.of(5, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl requireConsentPermission = withGvl(vendorPermission1, Vendor.empty(1));
        final VendorPermissionWithGvl notAllowedPermission = withGvl(vendorPermission2, Vendor.empty(2));
        final VendorPermissionWithGvl excludedNotMentionedPermission = withGvl(vendorPermission3, Vendor.empty(3));
        final VendorPermissionWithGvl excludedNotAllowedPermission = withGvl(vendorPermission4, Vendor.empty(4));
        final VendorPermissionWithGvl notMentionedPermission = withGvl(vendorPermission5, Vendor.empty(5));

        // when
        final Stream<VendorPermission> result = target.allowedByTypeStrategy(
                PURPOSE_CODE,
                tcString,
                asList(requireConsentPermission, notAllowedPermission, notMentionedPermission),
                asList(excludedNotMentionedPermission, excludedNotAllowedPermission),
                true);

        // then
        assertThat(result).usingRecursiveFieldByFieldElementComparator().containsOnly(vendorPermission3);
    }

    @Test
    public void shouldReturnExpectedWhenMultiplePublisherRestrictionsProvided() {
        // given
        final IntIterable requireConsentIterable = spy(IntIterable.class);
        final PublisherRestriction publisherRestriction1 = new PublisherRestriction(
                PURPOSE_CODE.code(), RestrictionType.REQUIRE_CONSENT, requireConsentIterable);
        given(requireConsentIterable.intIterator()).willReturn(intIterator(1));

        final IntIterable notAllowedIterable = spy(IntIterable.class);
        final PublisherRestriction publisherRestriction2 = new PublisherRestriction(
                PURPOSE_CODE.code(), RestrictionType.NOT_ALLOWED, notAllowedIterable);
        given(notAllowedIterable.intIterator()).willReturn(intIterator(4, 2));

        given(tcString.getPublisherRestrictions()).willReturn(asList(publisherRestriction1, publisherRestriction2));

        final VendorPermission vendorPermission1 = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission2 = VendorPermission.of(2, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission3 = VendorPermission.of(3, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission4 = VendorPermission.of(4, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermission vendorPermission5 = VendorPermission.of(5, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl requireConsentPermission = withGvl(
                vendorPermission1, Vendor.builder().id(1).purposes(EnumSet.of(PURPOSE_CODE)).build());
        final VendorPermissionWithGvl notAllowedPermission = withGvl(
                vendorPermission2, Vendor.builder().id(2).purposes(EnumSet.of(PURPOSE_CODE)).build());
        final VendorPermissionWithGvl excludedNotMentionedPermission = withGvl(
                vendorPermission3, Vendor.builder().id(3).purposes(EnumSet.of(PURPOSE_CODE)).build());
        final VendorPermissionWithGvl excludedNotAllowedPermission = withGvl(
                vendorPermission4, Vendor.builder().id(4).purposes(EnumSet.of(PURPOSE_CODE)).build());
        final VendorPermissionWithGvl notMentionedPermission = withGvl(
                vendorPermission5, Vendor.builder().id(5).purposes(EnumSet.of(PURPOSE_CODE)).build());

        given(purposesConsent.contains(anyInt())).willReturn(true);

        // when
        final Stream<VendorPermission> result = target.allowedByTypeStrategy(
                PURPOSE_CODE,
                tcString,
                asList(requireConsentPermission, notAllowedPermission, notMentionedPermission),
                asList(excludedNotMentionedPermission, excludedNotAllowedPermission),
                false);

        // then
        assertThat(result)
                .usingRecursiveFieldByFieldElementComparator()
                .containsOnly(vendorPermission1, vendorPermission3, vendorPermission5);
    }

    // GVL Purpose part

    @Test
    public void shouldAllowWhenInGvlPurposeAndPurposeConsentAllowed() {
        // given
        final Vendor vendorGvl = Vendor.builder()
                .purposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.noneOf(PurposeCode.class))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = withGvl(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        given(purposesConsent.contains(anyInt())).willReturn(true);
        given(vendorIds.intIterator()).willReturn(intIterator(1));

        // when
        final Stream<VendorPermission> result = target.allowedByTypeStrategy(
                PURPOSE_CODE, tcString, vendorPermissionWithGvls, emptyList(), false);

        // then
        assertThat(result).usingRecursiveFieldByFieldElementComparator().containsOnly(vendorPermission);

        verify(purposesConsent).contains(PURPOSE_CODE.code());
    }

    @Test
    public void shouldAllowWhenInGvlPurposeAndPurposeConsentAllowedAndRequireConsent() {
        // given
        final Vendor vendorGvl = Vendor.builder()
                .purposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.noneOf(PurposeCode.class))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = withGvl(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_CONSENT);

        given(purposesConsent.contains(anyInt())).willReturn(true);
        given(vendorIds.intIterator()).willReturn(intIterator(1));

        // when
        final Stream<VendorPermission> result = target.allowedByTypeStrategy(
                PURPOSE_CODE, tcString, vendorPermissionWithGvls, emptyList(), false);

        // then
        assertThat(result).usingRecursiveFieldByFieldElementComparator().containsOnly(vendorPermission);

        verify(purposesConsent).contains(PURPOSE_CODE.code());
    }

    @Test
    public void shouldEmptyWhenInGvlPurposeAndPurposeConsentAllowedAndRequireLI() {
        // given
        final Vendor vendorGvl = Vendor.builder()
                .purposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.noneOf(PurposeCode.class))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = withGvl(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_LEGITIMATE_INTEREST);

        given(purposesConsent.contains(anyInt())).willReturn(true);
        given(vendorIds.intIterator()).willReturn(intIterator(1));

        // when
        final Stream<VendorPermission> result = target.allowedByTypeStrategy(
                PURPOSE_CODE, tcString, vendorPermissionWithGvls, emptyList(), false);

        // then
        assertThat(result).isEmpty();

        verifyNoInteractions(purposesConsent);
    }

    @Test
    public void shouldEmptyWhenInGvlPurposeAndPurposeLIAllowed() {
        // given
        final Vendor vendorGvl = Vendor.builder()
                .purposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.noneOf(PurposeCode.class))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = withGvl(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        given(purposesLI.contains(anyInt())).willReturn(true);
        given(vendorIds.intIterator()).willReturn(intIterator(1));

        // when
        final Stream<VendorPermission> result = target.allowedByTypeStrategy(
                PURPOSE_CODE, tcString, vendorPermissionWithGvls, emptyList(), false);

        // then
        assertThat(result).isEmpty();

        verify(purposesConsent).contains(PURPOSE_CODE.code());
        verifyNoInteractions(purposesLI);
    }

    @Test
    public void shouldAllowWhenInGvlPurposeAndPurposeConsentAllowedAndVendorConsentAllowedAndEnforced() {
        // given
        final Vendor vendorGvl = Vendor.builder()
                .purposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.noneOf(PurposeCode.class))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = withGvl(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        given(purposesConsent.contains(anyInt())).willReturn(true);
        given(allowedVendors.contains(anyInt())).willReturn(true);
        given(vendorIds.intIterator()).willReturn(intIterator(1));

        // when
        final Stream<VendorPermission> result = target.allowedByTypeStrategy(
                PURPOSE_CODE, tcString, vendorPermissionWithGvls, emptyList(), true);

        // then
        assertThat(result).usingRecursiveFieldByFieldElementComparator().containsOnly(vendorPermission);

        verify(purposesConsent).contains(PURPOSE_CODE.code());
        verify(allowedVendors).contains(1);
    }

    @Test
    public void shouldEmptyWhenInGvlPurposeAndPurposeConsentAllowedAndVendorLIAllowedAndEnforced() {
        // given
        final Vendor vendorGvl = Vendor.builder()
                .purposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.noneOf(PurposeCode.class))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = withGvl(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        given(purposesConsent.contains(anyInt())).willReturn(true);
        given(allowedVendorsLI.contains(anyInt())).willReturn(true);
        given(vendorIds.intIterator()).willReturn(intIterator(1));

        // when
        final Stream<VendorPermission> result = target.allowedByTypeStrategy(
                PURPOSE_CODE, tcString, vendorPermissionWithGvls, emptyList(), true);

        // then
        assertThat(result).isEmpty();

        verify(purposesConsent).contains(PURPOSE_CODE.code());
        verifyNoInteractions(purposesLI);
    }

    // GVL Legitimate interest Purpose part

    @Test
    public void shouldAllowWhenInGvlPurposeLIAndPurposeLI() {
        // given
        final Vendor vendorGvl = Vendor.builder()
                .legIntPurposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.noneOf(PurposeCode.class))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = withGvl(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        given(purposesLI.contains(anyInt())).willReturn(true);
        given(vendorIds.intIterator()).willReturn(intIterator(1));

        // when
        final Stream<VendorPermission> result = target.allowedByTypeStrategy(
                PURPOSE_CODE, tcString, vendorPermissionWithGvls, emptyList(), false);

        // then
        assertThat(result).usingRecursiveFieldByFieldElementComparator().containsOnly(vendorPermission);

        verify(purposesLI).contains(PURPOSE_CODE.code());
    }

    @Test
    public void shouldAllowWhenInGvlPurposeLIAndPurposeLIAndRequireLI() {
        // given
        final Vendor vendorGvl = Vendor.builder()
                .legIntPurposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.noneOf(PurposeCode.class))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = withGvl(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_LEGITIMATE_INTEREST);

        given(purposesLI.contains(anyInt())).willReturn(true);
        given(vendorIds.intIterator()).willReturn(intIterator(1));

        // when
        final Stream<VendorPermission> result = target.allowedByTypeStrategy(
                PURPOSE_CODE, tcString, vendorPermissionWithGvls, emptyList(), false);

        // then
        assertThat(result).usingRecursiveFieldByFieldElementComparator().containsOnly(vendorPermission);

        verify(purposesLI).contains(PURPOSE_CODE.code());
    }

    @Test
    public void shouldEmptyWhenInGvlPurposeLIAndPurposeLIAndRequireConsent() {
        // given
        final Vendor vendorGvl = Vendor.builder()
                .legIntPurposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.noneOf(PurposeCode.class))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = withGvl(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        final RestrictionType requireConsent = RestrictionType.REQUIRE_CONSENT;
        setRestriction(requireConsent);

        given(purposesLI.contains(anyInt())).willReturn(true);
        given(vendorIds.intIterator()).willReturn(intIterator(1));

        // when
        final Stream<VendorPermission> result = target.allowedByTypeStrategy(
                PURPOSE_CODE, tcString, vendorPermissionWithGvls, emptyList(), false);

        // then
        assertThat(result).isEmpty();

        verifyNoInteractions(purposesLI);
    }

    @Test
    public void shouldAllowWhenInGvlPurposeLIAndPurposeLIAndVendorLIAllowedAndEnforced() {
        // given
        final Vendor vendorGvl = Vendor.builder()
                .legIntPurposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.noneOf(PurposeCode.class))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = withGvl(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        given(purposesLI.contains(anyInt())).willReturn(true);
        given(allowedVendorsLI.contains(anyInt())).willReturn(true);
        given(vendorIds.intIterator()).willReturn(intIterator(1));

        // when
        final Stream<VendorPermission> result = target.allowedByTypeStrategy(
                PURPOSE_CODE, tcString, vendorPermissionWithGvls, emptyList(), true);

        // then
        assertThat(result).usingRecursiveFieldByFieldElementComparator().containsOnly(vendorPermission);

        verify(purposesLI).contains(PURPOSE_CODE.code());
        verify(allowedVendorsLI).contains(1);
    }

    @Test
    public void shouldEmptyWhenInGvlPurposeLIAndPurposeConsentAllowedAndVendorConsentAllowedAndEnforced() {
        // given
        final Vendor vendorGvl = Vendor.builder()
                .legIntPurposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.noneOf(PurposeCode.class))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = withGvl(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        given(purposesLI.contains(anyInt())).willReturn(true);
        given(purposesConsent.contains(anyInt())).willReturn(true);
        given(allowedVendors.contains(anyInt())).willReturn(true);
        given(vendorIds.intIterator()).willReturn(intIterator(1));

        // when
        final Stream<VendorPermission> result = target.allowedByTypeStrategy(
                PURPOSE_CODE, tcString, vendorPermissionWithGvls, emptyList(), true);

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
        final Vendor vendorGvl = Vendor.builder()
                .purposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.of(PURPOSE_CODE))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = withGvl(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_CONSENT);

        given(purposesConsent.contains(anyInt())).willReturn(true);
        given(vendorIds.intIterator()).willReturn(intIterator(1));

        // when
        final Stream<VendorPermission> result = target.allowedByTypeStrategy(
                PURPOSE_CODE, tcString, vendorPermissionWithGvls, emptyList(), false);

        // then
        assertThat(result).usingRecursiveFieldByFieldElementComparator().containsOnly(vendorPermission);

        verify(purposesConsent).contains(PURPOSE_CODE.code());
    }

    @Test
    public void shouldAllowWhenInGvlPurposeAndPurposeConsentAndVendorConsentAndEnforcedAndFlexibleAndRequireConsent() {
        // given
        final Vendor vendorGvl = Vendor.builder()
                .purposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.of(PURPOSE_CODE))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = withGvl(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_CONSENT);

        given(purposesConsent.contains(anyInt())).willReturn(true);
        given(allowedVendors.contains(anyInt())).willReturn(true);
        given(vendorIds.intIterator()).willReturn(intIterator(1));

        // when
        final Stream<VendorPermission> result = target.allowedByTypeStrategy(
                PURPOSE_CODE, tcString, vendorPermissionWithGvls, emptyList(), true);

        // then
        assertThat(result).usingRecursiveFieldByFieldElementComparator().containsOnly(vendorPermission);

        verify(purposesConsent).contains(PURPOSE_CODE.code());
        verify(allowedVendors).contains(1);
    }

    @Test
    public void shouldEmptyWhenInGvlPurposeAndPurposeLIAllowedAndFlexibleAndRequireConsent() {
        // given
        final Vendor vendorGvl = Vendor.builder()
                .purposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.of(PURPOSE_CODE))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = withGvl(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_CONSENT);

        given(purposesLI.contains(anyInt())).willReturn(true);
        given(vendorIds.intIterator()).willReturn(intIterator(1));

        // when
        final Stream<VendorPermission> result = target.allowedByTypeStrategy(
                PURPOSE_CODE, tcString, vendorPermissionWithGvls, emptyList(), false);

        // then
        assertThat(result).isEmpty();

        verifyNoInteractions(purposesLI);
    }

    @Test
    public void shouldEmptyWhenInGvlPurposeAndPurposeLIAndVendorLIAllowedAndEnforcedAndFlexibleAndRequireConsent() {
        // given
        final Vendor vendorGvl = Vendor.builder()
                .purposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.of(PURPOSE_CODE))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = withGvl(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_CONSENT);

        given(purposesLI.contains(anyInt())).willReturn(true);
        given(allowedVendorsLI.contains(anyInt())).willReturn(true);
        given(vendorIds.intIterator()).willReturn(intIterator(1));

        // when
        final Stream<VendorPermission> result = target.allowedByTypeStrategy(
                PURPOSE_CODE, tcString, vendorPermissionWithGvls, emptyList(), true);

        // then
        assertThat(result).isEmpty();

        verifyNoInteractions(purposesLI);
        verifyNoInteractions(allowedVendorsLI);
    }

    @Test
    public void shouldEmptyWhenInGvlPurposeAndPurposeConsentAndVendorLIAndEnforcedAndFlexibleAndRequireConsent() {
        // given
        final Vendor vendorGvl = Vendor.builder()
                .purposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.of(PURPOSE_CODE))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = withGvl(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_CONSENT);

        given(purposesConsent.contains(anyInt())).willReturn(true);
        given(allowedVendorsLI.contains(anyInt())).willReturn(true);
        given(vendorIds.intIterator()).willReturn(intIterator(1));

        // when
        final Stream<VendorPermission> result = target.allowedByTypeStrategy(
                PURPOSE_CODE, tcString, vendorPermissionWithGvls, emptyList(), true);

        // then
        assertThat(result).isEmpty();

        verify(purposesConsent).contains(PURPOSE_CODE.code());
    }

    @Test
    public void shouldEmptyWhenInGvlPurposeAndPurposeLIAndVendorConsentAndEnforcedAndFlexibleAndRequireConsent() {
        // given
        final Vendor vendorGvl = Vendor.builder()
                .purposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.of(PURPOSE_CODE))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = withGvl(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_CONSENT);

        given(purposesLI.contains(anyInt())).willReturn(true);
        given(allowedVendors.contains(anyInt())).willReturn(true);
        given(vendorIds.intIterator()).willReturn(intIterator(1));

        // when
        final Stream<VendorPermission> result = target.allowedByTypeStrategy(
                PURPOSE_CODE, tcString, vendorPermissionWithGvls, emptyList(), true);

        // then
        assertThat(result).isEmpty();
    }

    // Restriction type is REQUIRE_LEGITIMATE_INTEREST part

    @Test
    public void shouldEmptyWhenInGvlPurposeAndPurposeConsentAllowedAndFlexibleAndRequireLI() {
        // given
        final Vendor vendorGvl = Vendor.builder()
                .purposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.of(PURPOSE_CODE))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = withGvl(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_LEGITIMATE_INTEREST);

        given(purposesConsent.contains(anyInt())).willReturn(true);
        given(vendorIds.intIterator()).willReturn(intIterator(1));

        // when
        final Stream<VendorPermission> result = target.allowedByTypeStrategy(
                PURPOSE_CODE, tcString, vendorPermissionWithGvls, emptyList(), false);

        // then
        assertThat(result).isEmpty();

        verifyNoInteractions(purposesConsent);
    }

    @Test
    public void shouldEmptyWhenInGvlPurposeAndPurposeConsentAndVendorConsentAndEnforcedAndFlexibleAndRequireLI() {
        // given
        final Vendor vendorGvl = Vendor.builder()
                .purposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.of(PURPOSE_CODE))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = withGvl(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_LEGITIMATE_INTEREST);

        given(purposesConsent.contains(anyInt())).willReturn(true);
        given(allowedVendors.contains(anyInt())).willReturn(true);
        given(vendorIds.intIterator()).willReturn(intIterator(1));

        // when
        final Stream<VendorPermission> result = target.allowedByTypeStrategy(
                PURPOSE_CODE, tcString, vendorPermissionWithGvls, emptyList(), true);

        // then
        assertThat(result).isEmpty();

        verifyNoInteractions(purposesConsent);
        verifyNoInteractions(allowedVendors);
    }

    @Test
    public void shouldAllowWhenInGvlPurposeAndPurposeLIAllowedAndFlexibleAndRequireLI() {
        // given
        final Vendor vendorGvl = Vendor.builder()
                .purposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.of(PURPOSE_CODE))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = withGvl(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_LEGITIMATE_INTEREST);

        given(purposesLI.contains(anyInt())).willReturn(true);
        given(vendorIds.intIterator()).willReturn(intIterator(1));

        // when
        final Stream<VendorPermission> result = target.allowedByTypeStrategy(
                PURPOSE_CODE, tcString, vendorPermissionWithGvls, emptyList(), false);

        // then
        assertThat(result).usingRecursiveFieldByFieldElementComparator().containsOnly(vendorPermission);

        verify(purposesLI).contains(PURPOSE_CODE.code());
    }

    @Test
    public void shouldAllowWhenInGvlPurposeAndPurposeLIAndVendorLIAllowedAndEnforcedAndFlexibleAndRequireLI() {
        // given
        final Vendor vendorGvl = Vendor.builder()
                .purposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.of(PURPOSE_CODE))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = withGvl(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_LEGITIMATE_INTEREST);

        given(purposesLI.contains(anyInt())).willReturn(true);
        given(allowedVendorsLI.contains(anyInt())).willReturn(true);
        given(vendorIds.intIterator()).willReturn(intIterator(1));

        // when
        final Stream<VendorPermission> result = target.allowedByTypeStrategy(
                PURPOSE_CODE, tcString, vendorPermissionWithGvls, emptyList(), true);

        // then
        assertThat(result).usingRecursiveFieldByFieldElementComparator().containsOnly(vendorPermission);

        verify(purposesLI).contains(PURPOSE_CODE.code());
        verify(allowedVendorsLI).contains(1);
    }

    @Test
    public void shouldEmptyWhenInGvlPurposeAndPurposeConsentAndVendorLIAllowedAndEnforcedAndFlexibleAndRequireLI() {
        // given
        final Vendor vendorGvl = Vendor.builder()
                .purposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.of(PURPOSE_CODE))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = withGvl(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_LEGITIMATE_INTEREST);

        given(purposesConsent.contains(anyInt())).willReturn(true);
        given(allowedVendorsLI.contains(anyInt())).willReturn(true);
        given(vendorIds.intIterator()).willReturn(intIterator(1));

        // when
        final Stream<VendorPermission> result = target.allowedByTypeStrategy(
                PURPOSE_CODE, tcString, vendorPermissionWithGvls, emptyList(), true);

        // then
        assertThat(result).isEmpty();

        verify(allowedVendorsLI).contains(1);
        verifyNoInteractions(purposesConsent);
    }

    @Test
    public void shouldEmptyWhenInGvlPurposeAndPurposeLIAndVendorConsentAllowedAndEnforcedAndFlexibleAndRequireLI() {
        // given
        final Vendor vendorGvl = Vendor.builder()
                .purposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.of(PURPOSE_CODE))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = withGvl(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_LEGITIMATE_INTEREST);

        given(purposesLI.contains(anyInt())).willReturn(true);
        given(allowedVendors.contains(anyInt())).willReturn(true);
        given(vendorIds.intIterator()).willReturn(intIterator(1));

        // when
        final Stream<VendorPermission> result = target.allowedByTypeStrategy(
                PURPOSE_CODE, tcString, vendorPermissionWithGvls, emptyList(), true);

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
        final Vendor vendorGvl = Vendor.builder()
                .legIntPurposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.of(PURPOSE_CODE))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = withGvl(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_CONSENT);

        given(purposesConsent.contains(anyInt())).willReturn(true);
        given(vendorIds.intIterator()).willReturn(intIterator(1));

        // when
        final Stream<VendorPermission> result = target.allowedByTypeStrategy(
                PURPOSE_CODE, tcString, vendorPermissionWithGvls, emptyList(), false);

        // then
        assertThat(result).usingRecursiveFieldByFieldElementComparator().containsOnly(vendorPermission);

        verify(purposesConsent).contains(PURPOSE_CODE.code());
    }

    @Test
    public void shouldAllowWhenInGvlPurposeLIAndPurposeAndVendorConsentAndEnforcedAndFlexibleAndRequireConsent() {
        // given
        final Vendor vendorGvl = Vendor.builder()
                .legIntPurposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.of(PURPOSE_CODE))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = withGvl(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_CONSENT);

        given(purposesConsent.contains(anyInt())).willReturn(true);
        given(allowedVendors.contains(anyInt())).willReturn(true);
        given(vendorIds.intIterator()).willReturn(intIterator(1));

        // when
        final Stream<VendorPermission> result = target.allowedByTypeStrategy(
                PURPOSE_CODE, tcString, vendorPermissionWithGvls, emptyList(), true);

        // then
        assertThat(result).usingRecursiveFieldByFieldElementComparator().containsOnly(vendorPermission);

        verify(purposesConsent).contains(PURPOSE_CODE.code());
        verify(allowedVendors).contains(1);
    }

    @Test
    public void shouldEmptyWhenInGvlPurposeLIAndPurposeLIAllowedAndFlexibleAndRequireConsent() {
        // given
        final Vendor vendorGvl = Vendor.builder()
                .legIntPurposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.of(PURPOSE_CODE))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = withGvl(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_CONSENT);

        given(purposesLI.contains(anyInt())).willReturn(true);
        given(vendorIds.intIterator()).willReturn(intIterator(1));

        // when
        final Stream<VendorPermission> result = target.allowedByTypeStrategy(
                PURPOSE_CODE, tcString, vendorPermissionWithGvls, emptyList(), false);

        // then
        assertThat(result).isEmpty();

        verifyNoInteractions(purposesLI);
    }

    @Test
    public void shouldEmptyWhenInGvlPurposeLIAndPurposeLIAndVendorLIAllowedAndEnforcedAndFlexibleAndRequireConsent() {
        // given
        final Vendor vendorGvl = Vendor.builder()
                .legIntPurposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.of(PURPOSE_CODE))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = withGvl(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_CONSENT);

        given(purposesLI.contains(anyInt())).willReturn(true);
        given(allowedVendorsLI.contains(anyInt())).willReturn(true);
        given(vendorIds.intIterator()).willReturn(intIterator(1));

        // when
        final Stream<VendorPermission> result = target.allowedByTypeStrategy(
                PURPOSE_CODE, tcString, vendorPermissionWithGvls, emptyList(), true);

        // then
        assertThat(result).isEmpty();

        verifyNoInteractions(purposesLI);
        verifyNoInteractions(allowedVendorsLI);
    }

    @Test
    public void shouldEmptyWhenInGvlPurposeLIAndPurposeConsentAndVendorLIAndEnforcedAndFlexibleAndRequireConsent() {
        // given
        final Vendor vendorGvl = Vendor.builder()
                .legIntPurposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.of(PURPOSE_CODE))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = withGvl(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_CONSENT);

        given(purposesConsent.contains(anyInt())).willReturn(true);
        given(allowedVendorsLI.contains(anyInt())).willReturn(true);
        given(vendorIds.intIterator()).willReturn(intIterator(1));

        // when
        final Stream<VendorPermission> result = target.allowedByTypeStrategy(
                PURPOSE_CODE, tcString, vendorPermissionWithGvls, emptyList(), true);

        // then
        assertThat(result).isEmpty();

        verify(purposesConsent).contains(PURPOSE_CODE.code());
        verifyNoInteractions(allowedVendorsLI);
    }

    @Test
    public void shouldEmptyWhenInGvlPurposeLIAndPurposeLIAndVendorConsentAndEnforcedAndFlexibleAndRequireConsent() {
        // given
        final Vendor vendorGvl = Vendor.builder()
                .legIntPurposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.of(PURPOSE_CODE))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = withGvl(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_CONSENT);

        given(purposesLI.contains(anyInt())).willReturn(true);
        given(allowedVendors.contains(anyInt())).willReturn(true);
        given(vendorIds.intIterator()).willReturn(intIterator(1));

        // when
        final Stream<VendorPermission> result = target.allowedByTypeStrategy(
                PURPOSE_CODE, tcString, vendorPermissionWithGvls, emptyList(), true);

        // then
        assertThat(result).isEmpty();

        verify(allowedVendors).contains(PURPOSE_CODE.code());
        verifyNoInteractions(purposesLI);
    }

    // Restriction type is REQUIRE_LEGITIMATE_INTEREST part

    @Test
    public void shouldEmptyWhenInGvlPurposeLIAndPurposeConsentAllowedAndFlexibleAndRequireLI() {
        // given
        final Vendor vendorGvl = Vendor.builder()
                .legIntPurposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.of(PURPOSE_CODE))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = withGvl(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_LEGITIMATE_INTEREST);

        given(purposesConsent.contains(anyInt())).willReturn(true);
        given(vendorIds.intIterator()).willReturn(intIterator(1));

        // when
        final Stream<VendorPermission> result = target.allowedByTypeStrategy(
                PURPOSE_CODE, tcString, vendorPermissionWithGvls, emptyList(), false);

        // then
        assertThat(result).isEmpty();

        verifyNoInteractions(purposesConsent);
    }

    @Test
    public void shouldEmptyWhenInGvlPurposeLIAndPurposeConsentAndVendorConsentAndEnforcedAndFlexibleAndRequireLI() {
        // given
        final Vendor vendorGvl = Vendor.builder()
                .legIntPurposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.of(PURPOSE_CODE))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = withGvl(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_LEGITIMATE_INTEREST);

        given(purposesConsent.contains(anyInt())).willReturn(true);
        given(allowedVendors.contains(anyInt())).willReturn(true);
        given(vendorIds.intIterator()).willReturn(intIterator(1));

        // when
        final Stream<VendorPermission> result = target.allowedByTypeStrategy(
                PURPOSE_CODE, tcString, vendorPermissionWithGvls, emptyList(), true);

        // then
        assertThat(result).isEmpty();

        verifyNoInteractions(purposesConsent);
        verifyNoInteractions(allowedVendors);
    }

    @Test
    public void shouldAllowWhenInGvlPurposeLIAndPurposeLIAllowedAndFlexibleAndRequireLI() {
        // given
        final Vendor vendorGvl = Vendor.builder()
                .legIntPurposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.of(PURPOSE_CODE))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = withGvl(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_LEGITIMATE_INTEREST);

        given(purposesLI.contains(anyInt())).willReturn(true);
        given(vendorIds.intIterator()).willReturn(intIterator(1));

        // when
        final Stream<VendorPermission> result = target.allowedByTypeStrategy(
                PURPOSE_CODE, tcString, vendorPermissionWithGvls, emptyList(), false);

        // then
        assertThat(result).usingRecursiveFieldByFieldElementComparator().containsOnly(vendorPermission);

        verify(purposesLI).contains(PURPOSE_CODE.code());
    }

    @Test
    public void shouldAllowWhenInGvlPurposeLIAndPurposeLIAndVendorLIAllowedAndEnforcedAndFlexibleAndRequireLI() {
        // given
        final Vendor vendorGvl = Vendor.builder()
                .legIntPurposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.of(PURPOSE_CODE))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = withGvl(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_LEGITIMATE_INTEREST);

        given(purposesLI.contains(anyInt())).willReturn(true);
        given(allowedVendorsLI.contains(anyInt())).willReturn(true);
        given(vendorIds.intIterator()).willReturn(intIterator(1));

        // when
        final Stream<VendorPermission> result = target.allowedByTypeStrategy(
                PURPOSE_CODE, tcString, vendorPermissionWithGvls, emptyList(), true);

        // then
        assertThat(result).usingRecursiveFieldByFieldElementComparator().containsOnly(vendorPermission);

        verify(purposesLI).contains(PURPOSE_CODE.code());
        verify(allowedVendorsLI).contains(1);
    }

    @Test
    public void shouldEmptyWhenInGvlPurposeLIAndPurposeConsentAndVendorLIAllowedAndEnforcedAndFlexibleAndRequireLI() {
        // given
        final Vendor vendorGvl = Vendor.builder()
                .legIntPurposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.of(PURPOSE_CODE))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = withGvl(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_LEGITIMATE_INTEREST);

        given(purposesConsent.contains(anyInt())).willReturn(true);
        given(allowedVendorsLI.contains(anyInt())).willReturn(true);
        given(vendorIds.intIterator()).willReturn(intIterator(1));

        // when
        final Stream<VendorPermission> result = target.allowedByTypeStrategy(
                PURPOSE_CODE, tcString, vendorPermissionWithGvls, emptyList(), true);

        // then
        assertThat(result).isEmpty();

        verify(allowedVendorsLI).contains(1);
        verifyNoInteractions(purposesConsent);
    }

    @Test
    public void shouldEmptyWhenInGvlPurposeLIAndPurposeLIAndVendorConsentAllowedAndEnforcedAndFlexibleAndRequireLI() {
        // given
        final Vendor vendorGvl = Vendor.builder()
                .legIntPurposes(EnumSet.of(PURPOSE_CODE))
                .flexiblePurposes(EnumSet.of(PURPOSE_CODE))
                .build();

        final VendorPermission vendorPermission = VendorPermission.of(1, null, PrivacyEnforcementAction.restrictAll());
        final VendorPermissionWithGvl vendorPermissionWitGvl = withGvl(vendorPermission, vendorGvl);
        final List<VendorPermissionWithGvl> vendorPermissionWithGvls = singletonList(vendorPermissionWitGvl);

        setRestriction(RestrictionType.REQUIRE_LEGITIMATE_INTEREST);

        given(purposesLI.contains(anyInt())).willReturn(true);
        given(allowedVendors.contains(anyInt())).willReturn(true);
        given(vendorIds.intIterator()).willReturn(intIterator(1));

        // when
        final Stream<VendorPermission> result = target.allowedByTypeStrategy(
                PURPOSE_CODE, tcString, vendorPermissionWithGvls, emptyList(), true);

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
        final VendorPermissionWithGvl vendorPermissionWitGvl1 = withGvl(vendorPermission1, Vendor.empty(1));
        final VendorPermissionWithGvl vendorPermissionWitGvl2 = withGvl(vendorPermission2, Vendor.empty(2));

        given(vendorIds.intIterator()).willReturn(intIterator(1));

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

    private void setRestriction(RestrictionType requireConsent) {
        given(publisherRestriction.getRestrictionType()).willReturn(requireConsent);
    }

    private static IntIterator intIterator(Integer... ints) {
        return new IntIterator() {

            private final Iterator<Integer> iterator = asList(ints).iterator();

            @Override
            public int nextInt() {
                return iterator.next();
            }

            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }
        };
    }

    private static VendorPermissionWithGvl withGvl(VendorPermission vendorPermission, Vendor vendor) {
        return VendorPermissionWithGvl.of(vendorPermission, vendor);
    }
}
