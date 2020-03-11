package org.prebid.server.privacy.gdpr;

import org.apache.commons.lang3.BooleanUtils;
import org.prebid.server.geolocation.GeoLocationService;
import org.prebid.server.metric.Metrics;
import org.prebid.server.settings.model.Gdpr;
import org.prebid.server.settings.model.Purposes;
import org.prebid.server.settings.model.SpecialFeatures;

import java.util.List;
import java.util.Objects;

public class TcfEnforcementService {

    private final Boolean gdprEnabled;
    private final String gdprDefaultValue;
    private final Purposes defaultPurposes;
    private final SpecialFeatures defaultSpecialFeatures;
    private final GdprService gdprService;
    private final List<String> eeaCountries;
    private final GeoLocationService geoLocationService;
    private final Metrics metrics;

    public TcfEnforcementService(Gdpr gdpr,
                                 List<String> eeaCountries,
                                 GdprService gdprService,
                                 GeoLocationService geoLocationService,
                                 Metrics metrics) {
        if (gdpr != null && BooleanUtils.isTrue(gdpr.getEnabled())) {
            this.gdprEnabled = true;
            this.gdprDefaultValue = Objects.requireNonNull(gdpr.getDefaultValue());
            this.defaultPurposes = gdpr.getPurposes() == null ? Purposes.builder().build() : gdpr.getPurposes();
            this.defaultSpecialFeatures = gdpr.getSpecialFeatures() == null
                    ? SpecialFeatures.builder().build()
                    : gdpr.getSpecialFeatures();
            this.gdprService = Objects.requireNonNull(gdprService);
            this.eeaCountries = Objects.requireNonNull(eeaCountries);
            this.geoLocationService = geoLocationService;
            this.metrics = Objects.requireNonNull(metrics);
        } else {
            this.gdprEnabled = false;
            this.gdprDefaultValue = null;
            this.defaultPurposes = null;
            this.defaultSpecialFeatures = null;
            this.gdprService = gdprService;
            this.eeaCountries = eeaCountries;
            this.geoLocationService = geoLocationService;
            this.metrics = metrics;
        }
    }

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

