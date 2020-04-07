package org.prebid.server.privacy.gdpr;

import com.iabtcf.decoder.TCString;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.privacy.gdpr.model.GdprPurpose;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.VendorPermission;
import org.prebid.server.privacy.gdpr.model.VendorPermissionWithGvl;
import org.prebid.server.privacy.gdpr.tcfstrategies.PurposeStrategy;
import org.prebid.server.privacy.gdpr.vendorlist.VendorListServiceV2;
import org.prebid.server.privacy.gdpr.vendorlist.proto.VendorV2;
import org.prebid.server.settings.model.AccountGdprConfig;
import org.prebid.server.settings.model.GdprConfig;
import org.prebid.server.settings.model.Purpose;
import org.prebid.server.settings.model.Purposes;
import org.prebid.server.settings.model.SpecialFeatures;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class Tcf2Service {

    private static final Logger logger = LoggerFactory.getLogger(Tcf2Service.class);

    private final Purposes defaultPurposes;
    private final SpecialFeatures defaultSpecialFeatures;
    private final VendorListServiceV2 vendorListServiceV2;
    private final List<PurposeStrategy> purposeStrategies;
    private final BidderCatalog bidderCatalog;

    public Tcf2Service(GdprConfig gdprConfig,
                       VendorListServiceV2 vendorListServiceV2,
                       BidderCatalog bidderCatalog,
                       List<PurposeStrategy> purposeStrategies) {
        this.defaultPurposes = gdprConfig.getPurposes() == null ? Purposes.builder().build() : gdprConfig.getPurposes();
        this.defaultSpecialFeatures = gdprConfig.getSpecialFeatures() == null
                ? SpecialFeatures.builder().build()
                : gdprConfig.getSpecialFeatures();
        this.vendorListServiceV2 = Objects.requireNonNull(vendorListServiceV2);
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.purposeStrategies = Objects.requireNonNull(purposeStrategies);
    }

    // TODO remove Purposes from parameters
    public Future<Collection<VendorPermission>> permissionsFor(TCString gdprConsent,
                                                               Set<Integer> vendorIds,
                                                               Set<String> bidderNames,
                                                               Set<GdprPurpose> purposes,
                                                               AccountGdprConfig accountGdprConfig) {
        final Purposes mergedPurposes = mergeAccountPurposes(accountGdprConfig);
        final Map<Integer, Purpose> purposeIdToPurpose = purposes.stream()
                .collect(Collectors.toMap(GdprPurpose::getId,
                        gdprPurpose -> findPurposeById(gdprPurpose.getId(), mergedPurposes)));

        return vendorListServiceV2.forVersion(gdprConsent.getVendorListVersion())
                // We can't skip TCF check for all bidders if we can't load GVL list
                .otherwise(Collections.emptyMap())
                .map(vendorGvlPermissions -> vendorPermissionWithGvls(bidderNames, vendorIds, vendorGvlPermissions))
                .map(vendorPermissionWithGvls -> processEachPurposeStrategies(gdprConsent, vendorPermissionWithGvls,
                        purposeIdToPurpose));
    }

    private Collection<VendorPermissionWithGvl> vendorPermissionWithGvls(Set<String> bidderNames,
                                                                         Set<Integer> vendorIds,
                                                                         Map<Integer, VendorV2> vendorGvlPermissions) {
        final Collection<Integer> foundVendorIds = new HashSet<>();
        final Collection<VendorPermissionWithGvl> vendorPermissionsWithGvl = new ArrayList<>();
        for (String bidderName : bidderNames) {
            final Integer vendorId = bidderCatalog.isActive(bidderName)
                    ? bidderCatalog.vendorIdByName(bidderName)
                    : null;
            foundVendorIds.add(vendorId);
            vendorPermissionsWithGvl.add(createVendorPermission(vendorId, bidderName, vendorGvlPermissions));
        }

        vendorIds.stream()
                // this check only for illegal arguments...
                .filter(Objects::nonNull)
                .filter(vendorId -> !foundVendorIds.contains(vendorId))
                .map(vendorId -> createVendorPermission(vendorId, bidderCatalog.nameByVendorId(vendorId),
                        vendorGvlPermissions))
                .forEach(vendorPermissionsWithGvl::add);

        return vendorPermissionsWithGvl;
    }

    private static VendorPermissionWithGvl createVendorPermission(Integer vendorId,
                                                                  String bidderName,
                                                                  Map<Integer, VendorV2> vendorGvlPermissions) {
        final VendorV2 vendorGvlByVendorId = vendorId != null
                ? vendorGvlPermissions.getOrDefault(vendorId, VendorV2.empty(vendorId))
                : VendorV2.empty(vendorId);

        final VendorPermission vendorPermission = VendorPermission.of(vendorId, bidderName,
                PrivacyEnforcementAction.restrictAll());
        return VendorPermissionWithGvl.of(vendorPermission, vendorGvlByVendorId);

    }

    private Collection<VendorPermission> processEachPurposeStrategies(
            TCString gdprConsent, Collection<VendorPermissionWithGvl> vendorPermissions,
            Map<Integer, Purpose> purposeIdToPurpose) {

        for (Map.Entry<Integer, Purpose> integerPurposeEntry : purposeIdToPurpose.entrySet()) {
            final Purpose purpose = integerPurposeEntry.getValue();
            final Integer purposeId = integerPurposeEntry.getKey();

            final PurposeStrategy purposeStrategyById = findPurposeStrategyById(purposeId);
            purposeStrategyById.processTypePurposeStrategy(gdprConsent, purpose, vendorPermissions);
        }

        return vendorPermissions.stream()
                .map(VendorPermissionWithGvl::getVendorPermission)
                .collect(Collectors.toList());
    }

    private PurposeStrategy findPurposeStrategyById(Integer purposeId) {
        return purposeStrategies.stream()
                .filter(purposeStrategy -> Objects.equals(purposeStrategy.getPurposeId(), purposeId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(String.format("There no purpose strategy for purpose"
                        + " id = %s", purposeId)));
    }

    private Purpose findPurposeById(int gdprPurposeId, Purposes purposes) {
        switch (gdprPurposeId) {
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
                throw new IllegalArgumentException(String.format("Illegal GDPR code: %d", gdprPurposeId));
        }
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

