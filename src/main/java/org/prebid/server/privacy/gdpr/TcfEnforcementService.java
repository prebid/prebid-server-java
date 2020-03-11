package org.prebid.server.privacy.gdpr;

import org.apache.commons.lang3.BooleanUtils;
import org.prebid.server.geolocation.GeoLocationService;
import org.prebid.server.metric.Metrics;
import org.prebid.server.settings.model.Purpose;
import org.prebid.server.settings.model.Purposes;

import java.util.List;
import java.util.Objects;

public class TcfEnforcementService {

    private final Boolean gdprEnabled;
    private final String gdprDefaultValue;
    private final Purposes defaultPurposes;
    private final GdprService gdprService;
    private final List<String> eeaCountries;
    private final GeoLocationService geoLocationService;
    private final Metrics metrics;

    public TcfEnforcementService(Boolean gdprEnabled,
                                 String gdprDefaultValue,
                                 Purposes defaultPurposes,
                                 GdprService gdprService,
                                 List<String> eeaCountries,
                                 GeoLocationService geoLocationService,
                                 Metrics metrics) {
        this.gdprEnabled = gdprEnabled;
        this.gdprDefaultValue = gdprDefaultValue;
        this.defaultPurposes = defaultPurposes;
        this.gdprService = gdprService;
        this.eeaCountries = eeaCountries;
        this.geoLocationService = geoLocationService;
        this.metrics = metrics;

        validateParameters(gdprEnabled);
    }

    private void validateParameters(Boolean isEnabled) {
        if (BooleanUtils.isTrue(isEnabled)) {
            Objects.requireNonNull(gdprDefaultValue);
            Objects.requireNonNull(defaultPurposes);
            Objects.requireNonNull(gdprService);
            Objects.requireNonNull(eeaCountries);
            Objects.requireNonNull(geoLocationService);
            Objects.requireNonNull(metrics);
        }
    }

    private Purposes mergeAccountPurposes(Purposes accountPurposes) {
        return Purposes.builder()
                .p1(mergePurpose(accountPurposes.getP1(), defaultPurposes.getP1()))
                .p2(mergePurpose(accountPurposes.getP2(), defaultPurposes.getP2()))
                .p3(mergePurpose(accountPurposes.getP3(), defaultPurposes.getP3()))
                .p4(mergePurpose(accountPurposes.getP4(), defaultPurposes.getP4()))
                .p5(mergePurpose(accountPurposes.getP5(), defaultPurposes.getP5()))
                .p6(mergePurpose(accountPurposes.getP6(), defaultPurposes.getP6()))
                .p7(mergePurpose(accountPurposes.getP7(), defaultPurposes.getP7()))
                .p8(mergePurpose(accountPurposes.getP8(), defaultPurposes.getP8()))
                .p9(mergePurpose(accountPurposes.getP9(), defaultPurposes.getP9()))
                .build();
    }

    private static Purpose mergePurpose(Purpose prioritisedPurpose, Purpose purpose) {
        return prioritisedPurpose == null ? purpose : prioritisedPurpose;
    }
}
