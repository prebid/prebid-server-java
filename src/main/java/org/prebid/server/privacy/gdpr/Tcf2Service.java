package org.prebid.server.privacy.gdpr;

import com.iabtcf.decoder.TCString;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.privacy.gdpr.model.GdprPurpose;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.VendorPermission;
import org.prebid.server.privacy.gdpr.tcf2stratgies.PurposeStrategy;
import org.prebid.server.settings.model.GdprConfig;
import org.prebid.server.settings.model.Purpose;
import org.prebid.server.settings.model.Purposes;
import org.prebid.server.settings.model.SpecialFeatures;

import java.util.ArrayList;
import java.util.Collection;
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
    private final List<PurposeStrategy> purposeStrategies;
    private final BidderCatalog bidderCatalog;

    public Tcf2Service(GdprConfig gdprConfig, BidderCatalog bidderCatalog, List<PurposeStrategy> purposeStrategies) {
        this.defaultPurposes = gdprConfig.getPurposes() == null ? Purposes.builder().build() : gdprConfig.getPurposes();
        this.defaultSpecialFeatures = gdprConfig.getSpecialFeatures() == null
                ? SpecialFeatures.builder().build()
                : gdprConfig.getSpecialFeatures();
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.purposeStrategies = Objects.requireNonNull(purposeStrategies);
    }

    // TODO remove Purposes from parameters
    public Future<Collection<VendorPermission>> permissionsFor(TCString gdprConsent,
                                                               Set<Integer> vendorIds,
                                                               Set<String> bidderNames,
                                                               Set<GdprPurpose> purposes) {
        // TODO GVL list will be needed only for `full` purpose strategy
        //  vendorIdToPurposesByVersion(gdprConsent)

        final Map<Integer, Purpose> purposeIdToPurpose = purposes.stream()
                .collect(Collectors.toMap(GdprPurpose::getId, gdprPurpose ->
                        findPurposeById(gdprPurpose.getId(), defaultPurposes)));

        return Future.succeededFuture(processEachPurposeStrategies(gdprConsent, bidderNames, vendorIds,
                purposeIdToPurpose));
    }

    private static Purpose findPurposeById(int gdprPurposeId, Purposes purposes) {
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

    private Collection<VendorPermission> processEachPurposeStrategies(TCString gdprConsent,
                                                                      Set<String> bidderNames,
                                                                      Set<Integer> vendorIds,
                                                                      Map<Integer, Purpose> purposeIdToPurpose) {

        final Collection<VendorPermission> vendorPermissions = restrictAllForEach(bidderNames, vendorIds);

        for (Map.Entry<Integer, Purpose> integerPurposeEntry : purposeIdToPurpose.entrySet()) {
            final Purpose purpose = integerPurposeEntry.getValue();
            final Integer purposeId = integerPurposeEntry.getKey();

            final PurposeStrategy purposeStrategyById = findPurposeStrategyById(purposeId);
            purposeStrategyById.processTypePurposeStrategy(gdprConsent, purpose, vendorPermissions);
        }

        return vendorPermissions;
    }

    private PurposeStrategy findPurposeStrategyById(Integer purposeId) {
        return purposeStrategies.stream()
                .filter(purposeStrategy -> Objects.equals(purposeStrategy.getPurposeId(), purposeId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(String.format("There no purpose strategy for purpose"
                        + " id = %s", purposeId)));
    }

    private Collection<VendorPermission> restrictAllForEach(Set<String> bidderNames, Set<Integer> vendorIds) {
        final Collection<Integer> foundVendorIds = new HashSet<>();
        final Collection<VendorPermission> vendorPermissions = new ArrayList<>();
        for (String bidderName : bidderNames) {
            final Integer vendorId = bidderCatalog.isActive(bidderName)
                    ? bidderCatalog.vendorIdByName(bidderName)
                    : null;
            foundVendorIds.add(vendorId);
            vendorPermissions.add(VendorPermission.of(vendorId, bidderName, PrivacyEnforcementAction.restrictAll()));
        }

        vendorIds.stream()
                // this check only for illegal arguments...
                .filter(Objects::nonNull)
                .filter(vendorId -> !foundVendorIds.contains(vendorId))
                .map(vendorId -> VendorPermission.of(vendorId, bidderCatalog.nameByVendorId(vendorId),
                        PrivacyEnforcementAction.restrictAll()))
                .forEach(vendorPermissions::add);

        return vendorPermissions;
    }

    // TODO ASK DO we need to check for purposes even when vendor doesn't need it (GVL) ?

    //    private Map<String, Set<Integer>> bidderNameToPermissions(Map<String, Integer> bidderNameToVendorId,
    //                                                              Map<Integer, Set<Integer>> vendorIdToPermissions) {
    //        final Map<String, Set<Integer>> bidderNameToPermissions = new HashMap<>();
    //        for (Map.Entry<String, Integer> bidderNameVendorId : bidderNameToVendorId.entrySet()) {
    //            final Integer vendorId = bidderNameVendorId.getValue();
    //            final Set<Integer> permissions = vendorId == null
    //                    ? Collections.emptySet()
    //                    : vendorIdToPermissions.getOrDefault(vendorId, Collections.emptySet());
    //
    //            bidderNameToPermissions.put(bidderNameVendorId.getKey(), permissions);
    //        }
    //        return bidderNameToPermissions;
    //    }

    //    private Future<Map<Integer, Set<Integer>>> vendorIdToPurposesByVersion(TCString vendorConsent) {
    //        return vendorListService.forVersion(vendorConsent.getVendorListVersion());
    //    }

    private Purposes mergeAccountPurposes(Purposes accountPurposes) {
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
