package org.prebid.server.privacy.gdpr;

import com.iabtcf.decoder.TCString;
import io.vertx.core.Future;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.VendorPermission;
import org.prebid.server.privacy.gdpr.model.VendorPermissionWithGvl;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.PurposeFourStrategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.PurposeOneStrategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.PurposeSevenStrategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.PurposeStrategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.PurposeTwoStrategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.typestrategies.BasicEnforcePurposeStrategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.typestrategies.FullEnforcePurposeStrategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.purpose.typestrategies.NoEnforcePurposeStrategy;
import org.prebid.server.privacy.gdpr.tcfstrategies.specialfeature.SpecialFeaturesOneStrategy;
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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class Tcf2Service {

    private final Purposes defaultPurposes;
    private final SpecialFeatures defaultSpecialFeatures;
    private final VendorListServiceV2 vendorListServiceV2;
    private final List<PurposeStrategy> supportedPurposeStrategies;
    private final List<SpecialFeaturesStrategy> supportedSpecialFeatureStrategies;
    private final BidderCatalog bidderCatalog;
    private PurposeOneTreatmentInterpretation purposeOneTreatmentInterpretation;

    public Tcf2Service(GdprConfig gdprConfig, VendorListServiceV2 vendorListServiceV2, BidderCatalog bidderCatalog) {

        this.defaultPurposes = gdprConfig.getPurposes() == null ? Purposes.builder().build() : gdprConfig.getPurposes();
        this.defaultSpecialFeatures = gdprConfig.getSpecialFeatures() == null
                ? SpecialFeatures.builder().build()
                : gdprConfig.getSpecialFeatures();
        this.purposeOneTreatmentInterpretation = gdprConfig.getPurposeOneTreatmentInterpretation();
        this.vendorListServiceV2 = Objects.requireNonNull(vendorListServiceV2);
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.supportedPurposeStrategies = supportedPurposeStrategies();
        this.supportedSpecialFeatureStrategies = supportedSpecialFeatureStrategies();
    }

    private static List<PurposeStrategy> supportedPurposeStrategies() {
        final FullEnforcePurposeStrategy fullEnforcePurposeStrategy = new FullEnforcePurposeStrategy();
        final BasicEnforcePurposeStrategy basicEnforcePurposeStrategy = new BasicEnforcePurposeStrategy();
        final NoEnforcePurposeStrategy noEnforcePurposeStrategy = new NoEnforcePurposeStrategy();

        final PurposeOneStrategy purposeOneStrategy = new PurposeOneStrategy(fullEnforcePurposeStrategy,
                basicEnforcePurposeStrategy,
                noEnforcePurposeStrategy);
        final PurposeTwoStrategy purposeTwoStrategy = new PurposeTwoStrategy(fullEnforcePurposeStrategy,
                basicEnforcePurposeStrategy,
                noEnforcePurposeStrategy);
        final PurposeFourStrategy purposeFourStrategy = new PurposeFourStrategy(fullEnforcePurposeStrategy,
                basicEnforcePurposeStrategy,
                noEnforcePurposeStrategy);
        final PurposeSevenStrategy purposeSevenStrategy = new PurposeSevenStrategy(fullEnforcePurposeStrategy,
                basicEnforcePurposeStrategy,
                noEnforcePurposeStrategy);

        return Arrays.asList(purposeOneStrategy, purposeTwoStrategy, purposeFourStrategy, purposeSevenStrategy);
    }

    private static List<SpecialFeaturesStrategy> supportedSpecialFeatureStrategies() {
        return Collections.singletonList(new SpecialFeaturesOneStrategy());
    }

    public Future<Collection<VendorPermission>> permissionsFor(Set<Integer> vendorIds, TCString tcfConsent) {
        final Collection<VendorPermission> vendorPermissions = vendorPermissions(vendorIds);
        return permissionsForInternal(vendorPermissions, tcfConsent, null);
    }

    public Future<Collection<VendorPermission>> permissionsFor(Set<String> bidderNames,
                                                               VendorIdResolver vendorIdResolver,
                                                               TCString tcfConsent,
                                                               AccountGdprConfig accountGdprConfig) {
        final Collection<VendorPermission> vendorPermissions = vendorPermissions(bidderNames, vendorIdResolver);
        return permissionsForInternal(vendorPermissions, tcfConsent, accountGdprConfig);
    }

    private Collection<VendorPermission> vendorPermissions(Set<Integer> vendorIds) {
        return vendorIds.stream()
                // this check only for illegal arguments...
                .filter(Objects::nonNull)
                .map(vendorId -> VendorPermission.of(
                        vendorId, bidderCatalog.nameByVendorId(vendorId), PrivacyEnforcementAction.restrictAll()))
                .collect(Collectors.toList());
    }

    private Collection<VendorPermission> vendorPermissions(Set<String> bidderNames,
                                                           VendorIdResolver vendorIdResolver) {

        return bidderNames.stream()
                // this check only for illegal arguments...
                .filter(Objects::nonNull)
                .map(bidderName -> VendorPermission.of(
                        vendorIdResolver.resolve(bidderName), bidderName, PrivacyEnforcementAction.restrictAll()))
                .collect(Collectors.toList());
    }

    private Future<Collection<VendorPermission>> permissionsForInternal(Collection<VendorPermission> vendorPermissions,
                                                                        TCString tcfConsent,
                                                                        AccountGdprConfig accountGdprConfig) {

        final Purposes mergedPurposes = mergeAccountPurposes(accountGdprConfig);
        final SpecialFeatures mergedSpecialFeatures = mergeAccountSpecialFeatures(accountGdprConfig);
        final PurposeOneTreatmentInterpretation mergedPurposeOneTreatmentInterpretation =
                mergePurposeOneTreatmentInterpretation(accountGdprConfig);

        return vendorListServiceV2.forVersion(tcfConsent.getVendorListVersion())
                .map(vendorGvlPermissions -> wrapWithGVL(vendorPermissions, vendorGvlPermissions))

                .compose(gvlResult -> processSupportedPurposeStrategies(tcfConsent, gvlResult, mergedPurposes,
                        purposeOneTreatmentInterpretation),
                        ignoredFailed -> processDowngradedSupportedPurposeStrategies(tcfConsent, vendorPermissions,
                                mergedPurposes, mergedPurposeOneTreatmentInterpretation))

                .map(changedVendorPermissions -> processSupportedSpecialFeatureStrategies(tcfConsent,
                        changedVendorPermissions, mergedSpecialFeatures));

    }

    private static Collection<VendorPermissionWithGvl> wrapWithGVL(Collection<VendorPermission> vendorPermissions,
                                                                   Map<Integer, VendorV2> vendorGvlPermissions) {
        return vendorPermissions.stream()
                .map(vendorPermission -> wrapWithGVL(vendorPermission, vendorGvlPermissions))
                .collect(Collectors.toList());
    }

    private static VendorPermissionWithGvl wrapWithGVL(VendorPermission vendorPermission,
                                                       Map<Integer, VendorV2> vendorGvlPermissions) {
        final Integer vendorId = vendorPermission.getVendorId();
        final VendorV2 vendorGvlByVendorId = vendorId != null
                ? vendorGvlPermissions.getOrDefault(vendorId, VendorV2.empty(vendorId))
                : VendorV2.empty(vendorId);

        return VendorPermissionWithGvl.of(vendorPermission, vendorGvlByVendorId);
    }

    private Future<Collection<VendorPermission>> processSupportedPurposeStrategies(
            TCString tcfConsent,
            Collection<VendorPermissionWithGvl> vendorPermissionsWithGvl,
            Purposes purposes,
            PurposeOneTreatmentInterpretation purposeOneTreatmentInterpretation) {

        for (PurposeStrategy purposeStrategy : supportedPurposeStrategies) {
            final int purposeId = purposeStrategy.getPurposeId();
            final Purpose purposeById = findPurposeById(purposeId, purposes);
            processPurposeStrategy(tcfConsent, vendorPermissionsWithGvl, purposeById, purposeStrategy,
                    purposeOneTreatmentInterpretation);
        }

        return Future.succeededFuture(vendorPermissionsWithGvl.stream()
                .map(VendorPermissionWithGvl::getVendorPermission)
                .collect(Collectors.toList()));
    }

    private Future<Collection<VendorPermission>> processDowngradedSupportedPurposeStrategies(
            TCString tcfConsent,
            Collection<VendorPermission> vendorPermissions,
            Purposes purposes,
            PurposeOneTreatmentInterpretation purposeOneTreatmentInterpretation) {

        final List<VendorPermissionWithGvl> vendorPermissionsWithGvl = wrapWithEmptyGVL(vendorPermissions);

        for (PurposeStrategy purposeStrategy : supportedPurposeStrategies) {
            final int purposeId = purposeStrategy.getPurposeId();
            final Purpose downgradedPurpose = downgradePurpose(findPurposeById(purposeId, purposes));
            processPurposeStrategy(tcfConsent, vendorPermissionsWithGvl, downgradedPurpose, purposeStrategy,
                    purposeOneTreatmentInterpretation);
        }
        return Future.succeededFuture(vendorPermissions);
    }

    private void processPurposeStrategy(TCString tcfConsent,
                                        Collection<VendorPermissionWithGvl> vendorPermissionsWithGvl,
                                        Purpose purpose,
                                        PurposeStrategy purposeStrategy,
                                        PurposeOneTreatmentInterpretation purposeOneTreatmentInterpretation) {
        if (purposeStrategy.getPurposeId() == 1 && tcfConsent.getPurposeOneTreatment()) {
            processPurposeOneTreatment(purposeOneTreatmentInterpretation, tcfConsent, purpose,
                    purposeStrategy, vendorPermissionsWithGvl);
        } else {
            purposeStrategy.processTypePurposeStrategy(tcfConsent, purpose, vendorPermissionsWithGvl);
        }
    }

    private void processPurposeOneTreatment(PurposeOneTreatmentInterpretation purposeOneTreatmentInterpretation,
                                            TCString tcfConsent,
                                            Purpose purposeOne,
                                            PurposeStrategy purposeOneStrategy,
                                            Collection<VendorPermissionWithGvl> vendorPermissionsWithGvl) {
        switch (purposeOneTreatmentInterpretation) {
            case accessAllowed:
                vendorPermissionsWithGvl.forEach(vendorPermission -> purposeOneStrategy.allow(
                        vendorPermission.getVendorPermission().getPrivacyEnforcementAction()));
                break;
            case noAccessAllowed:
                // no need for special processing of no-access-allowed since everything is disallowed from the beginning
                break;
            case ignore:
            default:
                purposeOneStrategy.processTypePurposeStrategy(tcfConsent, purposeOne, vendorPermissionsWithGvl);
        }
    }

    private static List<VendorPermissionWithGvl> wrapWithEmptyGVL(Collection<VendorPermission> vendorPermissions) {
        return vendorPermissions.stream()
                .map(vendorPermission -> VendorPermissionWithGvl.of(vendorPermission,
                        VendorV2.empty(vendorPermission.getVendorId())))
                .collect(Collectors.toList());
    }

    private static Purpose downgradePurpose(Purpose purpose) {
        final EnforcePurpose enforcePurpose = purpose.getEnforcePurpose();
        return enforcePurpose == null || Objects.equals(enforcePurpose, EnforcePurpose.full)
                ? Purpose.of(EnforcePurpose.basic, purpose.getEnforceVendors(), purpose.getVendorExceptions())
                : purpose;
    }

    private Collection<VendorPermission> processSupportedSpecialFeatureStrategies(
            TCString tcfConsent,
            Collection<VendorPermission> vendorPermissions,
            SpecialFeatures specialFeatures) {

        for (SpecialFeaturesStrategy specialFeaturesStrategy : supportedSpecialFeatureStrategies) {
            final int specialFeatureId = specialFeaturesStrategy.getSpecialFeatureId();
            final SpecialFeature specialFeatureById = findSpecialFeatureById(specialFeatureId, specialFeatures);
            specialFeaturesStrategy.processSpecialFeaturesStrategy(tcfConsent, specialFeatureById, vendorPermissions);
        }

        return vendorPermissions;
    }

    private Purposes mergeAccountPurposes(AccountGdprConfig accountGdprConfig) {
        if (accountGdprConfig == null || accountGdprConfig.getPurposes() == null) {
            return defaultPurposes;
        }
        final Purposes accountPurposes = accountGdprConfig.getPurposes();
        return Purposes.builder()
                .p1(mergeItem(accountPurposes.getP1(), defaultPurposes.getP1()))
                .p2(mergeItem(accountPurposes.getP2(), defaultPurposes.getP2()))
                .p3(mergeItem(accountPurposes.getP3(), defaultPurposes.getP3()))
                .p4(mergeItem(accountPurposes.getP4(), defaultPurposes.getP4()))
                .p5(mergeItem(accountPurposes.getP5(), defaultPurposes.getP5()))
                .p6(mergeItem(accountPurposes.getP6(), defaultPurposes.getP6()))
                .p7(mergeItem(accountPurposes.getP7(), defaultPurposes.getP7()))
                .p8(mergeItem(accountPurposes.getP8(), defaultPurposes.getP8()))
                .p9(mergeItem(accountPurposes.getP9(), defaultPurposes.getP9()))
                .p9(mergeItem(accountPurposes.getP10(), defaultPurposes.getP10()))
                .build();
    }

    private SpecialFeatures mergeAccountSpecialFeatures(AccountGdprConfig accountGdprConfig) {
        if (accountGdprConfig == null || accountGdprConfig.getSpecialFeatures() == null) {
            return defaultSpecialFeatures;
        }
        final SpecialFeatures accountSpecialFeatures = accountGdprConfig.getSpecialFeatures();
        return SpecialFeatures.builder()
                .sf1(mergeItem(accountSpecialFeatures.getSf1(), defaultSpecialFeatures.getSf1()))
                .sf2(mergeItem(accountSpecialFeatures.getSf2(), defaultSpecialFeatures.getSf2()))
                .build();
    }

    private Purpose findPurposeById(int tcfPurposeId, Purposes purposes) {
        switch (tcfPurposeId) {
            case 1:
                return purposes.getP1();
            case 2:
                return purposes.getP2();
            case 3:
                return purposes.getP3();
            case 4:
                return purposes.getP4();
            case 5:
                return purposes.getP5();
            case 6:
                return purposes.getP6();
            case 7:
                return purposes.getP7();
            case 8:
                return purposes.getP8();
            case 9:
                return purposes.getP9();
            case 10:
                return purposes.getP10();
            default:
                throw new IllegalArgumentException(String.format("Illegal TCF code for purpose: %d",
                        tcfPurposeId));
        }
    }

    private SpecialFeature findSpecialFeatureById(int tcfSpecialFeaturesId, SpecialFeatures specialFeatures) {
        switch (tcfSpecialFeaturesId) {
            case 1:
                return specialFeatures.getSf1();
            case 2:
                return specialFeatures.getSf2();
            default:
                throw new IllegalArgumentException(String.format("Illegal TCF code for special feature: %d",
                        tcfSpecialFeaturesId));
        }
    }

    private PurposeOneTreatmentInterpretation mergePurposeOneTreatmentInterpretation(
            AccountGdprConfig accountGdprConfig) {

        if (accountGdprConfig == null || accountGdprConfig.getPurposeOneTreatmentInterpretation() == null) {
            return purposeOneTreatmentInterpretation;
        }

        return mergeItem(accountGdprConfig.getPurposeOneTreatmentInterpretation(), purposeOneTreatmentInterpretation);
    }

    private static <T> T mergeItem(T prioritisedItem, T item) {
        return prioritisedItem == null ? item : prioritisedItem;
    }
}
