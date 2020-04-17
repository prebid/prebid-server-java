package org.prebid.server.privacy.gdpr;

import com.iabtcf.decoder.TCString;
import io.vertx.core.Future;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.TcfPurpose;
import org.prebid.server.privacy.gdpr.model.VendorPermission;
import org.prebid.server.privacy.gdpr.model.VendorPermissionWithGvl;
import org.prebid.server.privacy.gdpr.tcfstrategies.PurposeStrategy;
import org.prebid.server.privacy.gdpr.vendorlist.VendorListServiceV2;
import org.prebid.server.privacy.gdpr.vendorlist.proto.VendorV2;
import org.prebid.server.settings.model.AccountGdprConfig;
import org.prebid.server.settings.model.GdprConfig;
import org.prebid.server.settings.model.Purpose;
import org.prebid.server.settings.model.PurposeOneTreatmentInterpretation;
import org.prebid.server.settings.model.Purposes;
import org.prebid.server.settings.model.SpecialFeatures;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Tcf2Service {

    private final Purposes defaultPurposes;
    private final SpecialFeatures defaultSpecialFeatures;
    private final VendorListServiceV2 vendorListServiceV2;
    private final List<PurposeStrategy> supportedPurposeStrategies;
    private final BidderCatalog bidderCatalog;
    private PurposeOneTreatmentInterpretation purposeOneTreatmentInterpretation;

    public Tcf2Service(GdprConfig gdprConfig,
                       VendorListServiceV2 vendorListServiceV2,
                       BidderCatalog bidderCatalog,
                       List<PurposeStrategy> purposeStrategies) {

        this.defaultPurposes = gdprConfig.getPurposes() == null ? Purposes.builder().build() : gdprConfig.getPurposes();
        this.defaultSpecialFeatures = gdprConfig.getSpecialFeatures() == null
                ? SpecialFeatures.builder().build()
                : gdprConfig.getSpecialFeatures();
        this.purposeOneTreatmentInterpretation = gdprConfig.getPurposeOneTreatmentInterpretation();
        this.vendorListServiceV2 = Objects.requireNonNull(vendorListServiceV2);
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.supportedPurposeStrategies = supportedPurposeStrategies(purposeStrategies);
    }

    private static List<PurposeStrategy> supportedPurposeStrategies(List<PurposeStrategy> purposeStrategies) {
        if (CollectionUtils.isEmpty(purposeStrategies)) {
            throw new IllegalArgumentException("No purposeStrategies provided");
        }

        return Arrays.stream(TcfPurpose.values())
                .map(TcfPurpose::getId)
                .map(supportedId -> strategyByPurposeId(supportedId, purposeStrategies))
                .collect(Collectors.toList());
    }

    private static PurposeStrategy strategyByPurposeId(Integer purposeId,
                                                       List<PurposeStrategy> purposeStrategies) {
        return purposeStrategies.stream()
                .filter(purposeStrategy -> Objects.equals(purposeStrategy.getPurposeId(), purposeId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("There no purpose strategy for purpose id = %s", purposeId)));
    }

    public Future<Collection<VendorPermission>> permissionsFor(Set<Integer> vendorIds, TCString gdprConsent) {
        return permissionsForInternal(
                vendorGvlPermissions -> vendorPermissionWithGvls(vendorIds, vendorGvlPermissions),
                gdprConsent,
                null);
    }

    public Future<Collection<VendorPermission>> permissionsFor(Set<String> bidderNames,
                                                               VendorIdResolver vendorIdResolver,
                                                               TCString gdprConsent,
                                                               AccountGdprConfig accountGdprConfig) {
        return permissionsForInternal(
                vendorGvlPermissions -> vendorPermissionWithGvls(bidderNames, vendorIdResolver, vendorGvlPermissions),
                gdprConsent,
                accountGdprConfig);
    }

    private Future<Collection<VendorPermission>> permissionsForInternal(
            Function<Map<Integer, VendorV2>, Collection<VendorPermissionWithGvl>> vendorPermissionsCreator,
            TCString tcfConsent,
            AccountGdprConfig accountGdprConfig) {

        final Purposes mergedPurposes = mergeAccountPurposes(accountGdprConfig);
        final PurposeOneTreatmentInterpretation mergedPurposeOneTreatmentInterpretation =
                mergePurposeOneTreatmentInterpretation(accountGdprConfig);

        return vendorListServiceV2.forVersion(tcfConsent.getVendorListVersion())
                // We can't skip TCF check for all bidders if we can't load GVL list
                .otherwise(Collections.emptyMap())
                .map(vendorPermissionsCreator)
                .map(vendorPermissionWithGvls -> processSupportedPurposeStrategies(
                        tcfConsent,
                        vendorPermissionWithGvls,
                        mergedPurposes,
                        mergedPurposeOneTreatmentInterpretation));
    }

    private Collection<VendorPermissionWithGvl> vendorPermissionWithGvls(Set<Integer> vendorIds,
                                                                         Map<Integer, VendorV2> vendorGvlPermissions) {
        return vendorIds.stream()
                // this check only for illegal arguments...
                .filter(Objects::nonNull)
                .map(vendorId -> createVendorPermission(
                        vendorId, bidderCatalog.nameByVendorId(vendorId), vendorGvlPermissions))
                .collect(Collectors.toList());
    }

    private Collection<VendorPermissionWithGvl> vendorPermissionWithGvls(Set<String> bidderNames,
                                                                         VendorIdResolver vendorIdResolver,
                                                                         Map<Integer, VendorV2> vendorGvlPermissions) {

        return bidderNames.stream()
                .map(bidderName -> createVendorPermission(
                        vendorIdResolver.resolve(bidderName), bidderName, vendorGvlPermissions))
                .collect(Collectors.toList());
    }

    private static VendorPermissionWithGvl createVendorPermission(Integer vendorId,
                                                                  String bidderName,
                                                                  Map<Integer, VendorV2> vendorGvlPermissions) {
        final VendorV2 vendorGvlByVendorId = vendorId != null
                ? vendorGvlPermissions.getOrDefault(vendorId, VendorV2.empty(vendorId))
                : VendorV2.empty(vendorId);

        return VendorPermissionWithGvl.of(
                VendorPermission.of(vendorId, bidderName, PrivacyEnforcementAction.restrictAll()),
                vendorGvlByVendorId);
    }

    private Collection<VendorPermission> processSupportedPurposeStrategies(
            TCString gdprConsent,
            Collection<VendorPermissionWithGvl> vendorPermissionsWithGvl,
            Purposes purposes,
            PurposeOneTreatmentInterpretation purposeOneTreatmentInterpretation) {

        for (PurposeStrategy purposeStrategy : supportedPurposeStrategies) {
            final int purposeId = purposeStrategy.getPurposeId();
            final Purpose purposeById = findPurposeById(purposeId, purposes);
            if (purposeId != 1
                    || purposeOneTreatmentInterpretation == PurposeOneTreatmentInterpretation.ignore
                    || !gdprConsent.getPurposeOneTreatment()) {
                purposeStrategy.processTypePurposeStrategy(gdprConsent, purposeById, vendorPermissionsWithGvl);
            } else if (purposeOneTreatmentInterpretation == PurposeOneTreatmentInterpretation.accessAllowed) {
                vendorPermissionsWithGvl.forEach(vendorPermission -> purposeStrategy.allow(
                        vendorPermission.getVendorPermission().getPrivacyEnforcementAction()));
            }
            // no need for special processing of no-access-allowed since everything is disallowed from the beginning
        }

        return vendorPermissionsWithGvl.stream()
                .map(VendorPermissionWithGvl::getVendorPermission)
                .collect(Collectors.toList());
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
                throw new IllegalArgumentException(String.format("Illegal TCF code: %d", tcfPurposeId));
        }
    }

    private PurposeOneTreatmentInterpretation mergePurposeOneTreatmentInterpretation(
            AccountGdprConfig accountGdprConfig) {

        if (accountGdprConfig == null || accountGdprConfig.getPurposeOneTreatmentInterpretation() == null) {
            return purposeOneTreatmentInterpretation;
        }

        return mergeItem(accountGdprConfig.getPurposeOneTreatmentInterpretation(), purposeOneTreatmentInterpretation);
    }

    private SpecialFeatures mergeAccountSpecialFeatures(SpecialFeatures accountSpecialFeatures) {
        return SpecialFeatures.builder()
                .sf1(mergeItem(accountSpecialFeatures.getSf1(), defaultSpecialFeatures.getSf1()))
                .sf2(mergeItem(accountSpecialFeatures.getSf2(), defaultSpecialFeatures.getSf2()))
                .build();
    }

    private static <T> T mergeItem(T prioritisedItem, T item) {
        return prioritisedItem == null ? item : prioritisedItem;
    }
}
