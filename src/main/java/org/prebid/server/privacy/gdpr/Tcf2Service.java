package org.prebid.server.privacy.gdpr;

import com.iabtcf.decoder.TCString;
import io.vertx.core.Future;
import lombok.Value;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.VendorPermission;
import org.prebid.server.privacy.gdpr.model.VendorPermissionWithGvl;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.PurposeStrategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.specialfeature.SpecialFeaturesStrategy;
import org.prebid.server.privacy.gdpr.vendorlist.VersionedVendorListService;
import org.prebid.server.privacy.gdpr.vendorlist.proto.PurposeCode;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class Tcf2Service {

    private final Purposes defaultPurposes;
    private final SpecialFeatures defaultSpecialFeatures;
    private final VersionedVendorListService versionedVendorListService;
    private final List<PurposeStrategy> purposeStrategies;
    private final List<SpecialFeaturesStrategy> specialFeaturesStrategies;
    private final BidderCatalog bidderCatalog;
    private final PurposeOneTreatmentInterpretation purposeOneTreatmentInterpretation;

    public Tcf2Service(GdprConfig gdprConfig,
                       List<PurposeStrategy> purposeStrategies,
                       List<SpecialFeaturesStrategy> specialFeaturesStrategies,
                       VersionedVendorListService versionedVendorListService,
                       BidderCatalog bidderCatalog) {

        this.defaultPurposes = gdprConfig.getPurposes() == null ? Purposes.builder().build() : gdprConfig.getPurposes();
        this.defaultSpecialFeatures = ObjectUtils.defaultIfNull(
                gdprConfig.getSpecialFeatures(),
                SpecialFeatures.builder().build());
        this.purposeOneTreatmentInterpretation = gdprConfig.getPurposeOneTreatmentInterpretation();
        this.versionedVendorListService = Objects.requireNonNull(versionedVendorListService);
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.purposeStrategies = Objects.requireNonNull(purposeStrategies);
        this.specialFeaturesStrategies = Objects.requireNonNull(specialFeaturesStrategies);
    }

    public Future<Collection<VendorPermission>> permissionsFor(Set<Integer> vendorIds, TCString tcfConsent) {
        return permissionsForInternal(vendorPermissions(vendorIds), tcfConsent, null);
    }

    public Future<Collection<VendorPermission>> permissionsFor(Set<String> bidderNames,
                                                               VendorIdResolver vendorIdResolver,
                                                               TCString tcfConsent,
                                                               AccountGdprConfig accountGdprConfig) {

        return permissionsForInternal(vendorPermissions(bidderNames, vendorIdResolver), tcfConsent, accountGdprConfig);
    }

    private Collection<VendorPermission> vendorPermissions(Set<Integer> vendorIds) {
        return vendorIds.stream()
                // this check only for illegal arguments...
                .filter(Objects::nonNull)
                .map(vendorId -> VendorPermission.of(
                        vendorId,
                        bidderCatalog.nameByVendorId(vendorId),
                        PrivacyEnforcementAction.restrictAll()))
                .toList();
    }

    private Collection<VendorPermission> vendorPermissions(Set<String> bidderNames, VendorIdResolver vendorIdResolver) {
        return bidderNames.stream()
                // this check only for illegal arguments...
                .filter(Objects::nonNull)
                .map(bidderName -> VendorPermission.of(
                        vendorIdResolver.resolve(bidderName),
                        bidderName,
                        PrivacyEnforcementAction.restrictAll()))
                .toList();
    }

    private Future<Collection<VendorPermission>> permissionsForInternal(Collection<VendorPermission> vendorPermissions,
                                                                        TCString tcfConsent,
                                                                        AccountGdprConfig accountGdprConfig) {

        final Purposes mergedPurposes = mergeAccountPurposes(accountGdprConfig);
        final VendorPermissionsByType<VendorPermission> vendorPermissionsByType =
                toVendorPermissionsByType(vendorPermissions, accountGdprConfig);

        // TODO: always merge account config for purpose1 with next major release
        return versionedVendorListService.forConsent(tcfConsent)
                .compose(vendorGvlPermissions -> processSupportedPurposeStrategies(
                                tcfConsent,
                                wrapWithGVL(vendorPermissionsByType, vendorGvlPermissions),
                                mergedPurposes,
                                purposeOneTreatmentInterpretation),
                        ignored -> processDowngradedSupportedPurposeStrategies(
                                tcfConsent,
                                wrapWithGVL(vendorPermissionsByType, Collections.emptyMap()),
                                mergedPurposes,
                                mergePurposeOneTreatmentInterpretation(accountGdprConfig)))
                .map(ignored -> processSupportedSpecialFeatureStrategies(
                        tcfConsent,
                        vendorPermissions,
                        mergeAccountSpecialFeatures(accountGdprConfig)));
    }

    private static VendorPermissionsByType<VendorPermission> toVendorPermissionsByType(
            Collection<VendorPermission> vendorPermissions,
            AccountGdprConfig accountGdprConfig) {

        final List<String> basicEnforcedVendors = accountGdprConfig != null
                ? accountGdprConfig.getBasicEnforcementVendors()
                : null;
        if (CollectionUtils.isEmpty(basicEnforcedVendors)) {
            return VendorPermissionsByType.of(Collections.emptyList(), vendorPermissions);
        }

        final Map<Boolean, List<VendorPermission>> isBasicEnforcedToPermissions = vendorPermissions.stream()
                .collect(Collectors.partitioningBy(vendorPermission ->
                        basicEnforcedVendors.contains(vendorPermission.getBidderName())));

        return VendorPermissionsByType.of(
                isBasicEnforcedToPermissions.getOrDefault(true, Collections.emptyList()),
                isBasicEnforcedToPermissions.getOrDefault(false, Collections.emptyList()));
    }

    private static VendorPermissionsByType<VendorPermissionWithGvl> wrapWithGVL(
            VendorPermissionsByType<VendorPermission> vendorPermissionsByType,
            Map<Integer, Vendor> vendorGvlPermissions) {

        final List<VendorPermissionWithGvl> weakPermissions = vendorPermissionsByType.getWeakPermissions().stream()
                .map(vendorPermission -> wrapWithGVL(vendorPermission, vendorGvlPermissions))
                .toList();

        final List<VendorPermissionWithGvl> standardPermissions = vendorPermissionsByType.getStandardPermissions()
                .stream()
                .map(vendorPermission -> wrapWithGVL(vendorPermission, vendorGvlPermissions))
                .toList();

        return VendorPermissionsByType.of(weakPermissions, standardPermissions);
    }

    private static VendorPermissionWithGvl wrapWithGVL(VendorPermission vendorPermission,
                                                       Map<Integer, Vendor> vendorGvlPermissions) {

        final Integer vendorId = vendorPermission.getVendorId();
        final Vendor vendorGvlByVendorId = Optional.ofNullable(vendorId)
                .map(vendorGvlPermissions::get)
                .orElseGet(() -> Vendor.empty(vendorId));

        return VendorPermissionWithGvl.of(vendorPermission, vendorGvlByVendorId);
    }

    private Future<Void> processSupportedPurposeStrategies(
            TCString tcfConsent,
            VendorPermissionsByType<VendorPermissionWithGvl> permissions,
            Purposes purposes,
            PurposeOneTreatmentInterpretation purposeOneTreatmentInterpretation) {

        for (PurposeStrategy purposeStrategy : purposeStrategies) {
            final PurposeCode tcfPurpose = purposeStrategy.getPurpose();
            final Purpose purposeById = findPurposeByTcfPurpose(tcfPurpose, purposes);
            final Purpose weakPurpose = weakPurpose(purposeById);

            final Collection<VendorPermissionWithGvl> standardPermissions = permissions.getStandardPermissions();
            final Collection<VendorPermissionWithGvl> weakPermissions = permissions.getWeakPermissions();

            processPurposeStrategy(
                    tcfConsent,
                    standardPermissions,
                    purposeById,
                    purposeStrategy,
                    purposeOneTreatmentInterpretation,
                    false);
            processPurposeStrategy(
                    tcfConsent,
                    weakPermissions,
                    weakPurpose,
                    purposeStrategy,
                    purposeOneTreatmentInterpretation,
                    true);
        }

        enforcePurpose4IfRequired(purposes, permissions);

        return Future.succeededFuture();
    }

    private Future<Void> processDowngradedSupportedPurposeStrategies(
            TCString tcfConsent,
            VendorPermissionsByType<VendorPermissionWithGvl> permissions,
            Purposes purposes,
            PurposeOneTreatmentInterpretation purposeOneTreatmentInterpretation) {

        for (PurposeStrategy purposeStrategy : purposeStrategies) {
            final PurposeCode tcfPurpose = purposeStrategy.getPurpose();
            final Purpose downgradedPurposeById = downgradePurpose(findPurposeByTcfPurpose(tcfPurpose, purposes));
            final Purpose weakPurpose = weakPurpose(downgradedPurposeById);

            final Collection<VendorPermissionWithGvl> standardPermissions = permissions.getStandardPermissions();
            final Collection<VendorPermissionWithGvl> weakPermissions = permissions.getWeakPermissions();

            processPurposeStrategy(
                    tcfConsent,
                    standardPermissions,
                    downgradedPurposeById,
                    purposeStrategy,
                    purposeOneTreatmentInterpretation,
                    true);
            processPurposeStrategy(
                    tcfConsent,
                    weakPermissions,
                    weakPurpose,
                    purposeStrategy,
                    purposeOneTreatmentInterpretation,
                    true);
        }

        enforcePurpose4IfRequired(purposes, permissions);

        return Future.succeededFuture();
    }

    private static Purpose downgradePurpose(Purpose purpose) {
        final EnforcePurpose enforcePurpose = purpose.getEnforcePurpose();

        return enforcePurpose == null || enforcePurpose == EnforcePurpose.full
                ? Purpose.of(
                EnforcePurpose.basic,
                purpose.getEnforceVendors(),
                purpose.getVendorExceptions(),
                purpose.getEid())
                : purpose;
    }

    private static Purpose weakPurpose(Purpose purpose) {
        final EnforcePurpose enforcePurpose = purpose.getEnforcePurpose();
        final EnforcePurpose downgradedEnforce = enforcePurpose == null || enforcePurpose == EnforcePurpose.full
                ? EnforcePurpose.basic
                : enforcePurpose;

        return Purpose.of(downgradedEnforce, false, purpose.getVendorExceptions(), purpose.getEid());
    }

    private static void processPurposeStrategy(TCString tcfConsent,
                                               Collection<VendorPermissionWithGvl> vendorPermissionsWithGvl,
                                               Purpose purpose,
                                               PurposeStrategy purposeStrategy,
                                               PurposeOneTreatmentInterpretation purposeOneTreatmentInterpretation,
                                               boolean wasDowngraded) {

        if (purposeStrategy.getPurpose() == PurposeCode.ONE && tcfConsent.getPurposeOneTreatment()) {
            processPurposeOneTreatment(
                    purposeOneTreatmentInterpretation,
                    tcfConsent,
                    purpose,
                    purposeStrategy,
                    vendorPermissionsWithGvl,
                    wasDowngraded);
        } else {
            purposeStrategy.processTypePurposeStrategy(tcfConsent, purpose, vendorPermissionsWithGvl, wasDowngraded);
        }
    }

    private static void processPurposeOneTreatment(PurposeOneTreatmentInterpretation purposeOneTreatmentInterpretation,
                                                   TCString tcfConsent,
                                                   Purpose purposeOne,
                                                   PurposeStrategy purposeOneStrategy,
                                                   Collection<VendorPermissionWithGvl> vendorPermissionsWithGvl,
                                                   boolean wasDowngraded) {

        switch (purposeOneTreatmentInterpretation) {
            case accessAllowed -> vendorPermissionsWithGvl.stream()
                    .map(VendorPermissionWithGvl::getVendorPermission)
                    .forEach(purposeOneStrategy::allow);
            case noAccessAllowed -> {
                // no need for special processing of no-access-allowed since everything is disallowed from the beginning
            }
            case ignore -> purposeOneStrategy
                    .processTypePurposeStrategy(tcfConsent, purposeOne, vendorPermissionsWithGvl, wasDowngraded);
        }
    }

    // TODO: remove after transition period
    private static void enforcePurpose4IfRequired(Purposes purposes,
                                                  VendorPermissionsByType<VendorPermissionWithGvl> permissions) {

        if (isConsentRequiredForPurpose4(purposes)) {
            requireConsentForPurpose4(permissions.getStandardPermissions());
            requireConsentForPurpose4(permissions.getWeakPermissions());
        }
    }

    private static boolean isConsentRequiredForPurpose4(Purposes purposes) {
        final PurposeEid purposeEid = findPurposeByTcfPurpose(PurposeCode.FOUR, purposes).getEid();
        return purposeEid != null && purposeEid.isRequireConsent();
    }

    private static void requireConsentForPurpose4(Collection<VendorPermissionWithGvl> permissions) {
        permissions.stream()
                .map(VendorPermissionWithGvl::getVendorPermission)
                .filter(vendorPermission -> !vendorPermission.isConsented(PurposeCode.FOUR))
                .map(VendorPermission::getPrivacyEnforcementAction)
                .forEach(privacyEnforcementAction -> privacyEnforcementAction.setRemoveUserIds(true));
    }

    private Collection<VendorPermission> processSupportedSpecialFeatureStrategies(
            TCString tcfConsent,
            Collection<VendorPermission> vendorPermissions,
            SpecialFeatures specialFeatures) {

        for (SpecialFeaturesStrategy specialFeaturesStrategy : specialFeaturesStrategies) {
            final int specialFeatureId = specialFeaturesStrategy.getSpecialFeatureId();
            final SpecialFeature specialFeatureById = findSpecialFeatureById(specialFeatureId, specialFeatures);
            specialFeaturesStrategy.processSpecialFeaturesStrategy(tcfConsent, specialFeatureById, vendorPermissions);
        }

        return vendorPermissions;
    }

    private Purposes mergeAccountPurposes(AccountGdprConfig accountGdprConfig) {
        final Purposes accountPurposes = accountGdprConfig != null
                ? accountGdprConfig.getPurposes()
                : null;

        return accountPurposes != null
                ? Purposes.builder()
                .p1(mergeItem(accountPurposes.getP1(), defaultPurposes.getP1()))
                .p2(mergeItem(accountPurposes.getP2(), defaultPurposes.getP2()))
                .p3(mergeItem(accountPurposes.getP3(), defaultPurposes.getP3()))
                .p4(mergeItem(accountPurposes.getP4(), defaultPurposes.getP4()))
                .p5(mergeItem(accountPurposes.getP5(), defaultPurposes.getP5()))
                .p6(mergeItem(accountPurposes.getP6(), defaultPurposes.getP6()))
                .p7(mergeItem(accountPurposes.getP7(), defaultPurposes.getP7()))
                .p8(mergeItem(accountPurposes.getP8(), defaultPurposes.getP8()))
                .p9(mergeItem(accountPurposes.getP9(), defaultPurposes.getP9()))
                .p10(mergeItem(accountPurposes.getP10(), defaultPurposes.getP10()))
                .build()
                : defaultPurposes;
    }

    private SpecialFeatures mergeAccountSpecialFeatures(AccountGdprConfig accountGdprConfig) {
        final SpecialFeatures accountSpecialFeatures = accountGdprConfig != null
                ? accountGdprConfig.getSpecialFeatures()
                : null;

        return accountSpecialFeatures != null
                ? SpecialFeatures.builder()
                .sf1(mergeItem(accountSpecialFeatures.getSf1(), defaultSpecialFeatures.getSf1()))
                .sf2(mergeItem(accountSpecialFeatures.getSf2(), defaultSpecialFeatures.getSf2()))
                .build()
                : defaultSpecialFeatures;
    }

    private static Purpose findPurposeByTcfPurpose(PurposeCode tcfPurpose, Purposes purposes) {
        return switch (tcfPurpose) {
            case ONE -> purposes.getP1();
            case TWO -> purposes.getP2();
            case THREE -> purposes.getP3();
            case FOUR -> purposes.getP4();
            case FIVE -> purposes.getP5();
            case SIX -> purposes.getP6();
            case SEVEN -> purposes.getP7();
            case EIGHT -> purposes.getP8();
            case NINE -> purposes.getP9();
            case TEN -> purposes.getP10();
            default -> throw new IllegalArgumentException("Illegal TCF code for purpose: " + tcfPurpose);
        };
    }

    private static SpecialFeature findSpecialFeatureById(int specialFeatureId, SpecialFeatures specialFeatures) {
        return switch (specialFeatureId) {
            case 1 -> specialFeatures.getSf1();
            case 2 -> specialFeatures.getSf2();
            default -> throw new IllegalArgumentException("Illegal TCF code for special feature: " + specialFeatureId);
        };
    }

    private PurposeOneTreatmentInterpretation mergePurposeOneTreatmentInterpretation(
            AccountGdprConfig accountGdprConfig) {

        return accountGdprConfig != null
                ? mergeItem(accountGdprConfig.getPurposeOneTreatmentInterpretation(), purposeOneTreatmentInterpretation)
                : purposeOneTreatmentInterpretation;
    }

    private static <T> T mergeItem(T prioritisedItem, T item) {
        return prioritisedItem == null ? item : prioritisedItem;
    }

    @Value(staticConstructor = "of")
    private static class VendorPermissionsByType<T> {

        Collection<T> weakPermissions;

        Collection<T> standardPermissions;
    }
}
