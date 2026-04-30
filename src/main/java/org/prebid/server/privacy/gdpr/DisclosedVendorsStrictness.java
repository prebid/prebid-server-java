package org.prebid.server.privacy.gdpr;

import com.iabtcf.decoder.TCString;
import org.prebid.server.settings.model.GdprConfig;

import java.time.Instant;
import java.time.Month;
import java.time.Year;
import java.time.ZoneOffset;

public class DisclosedVendorsStrictness {

    private static final Instant TCF_2_3_ENFORCEMENT_CUTOFF_DATE = Year.of(2026)
            .atMonth(Month.MARCH)
            .atDay(1)
            .atStartOfDay()
            .toInstant(ZoneOffset.UTC);

    private final boolean strictnessEnabled;

    public DisclosedVendorsStrictness(GdprConfig gdprConfig) {
        this.strictnessEnabled = gdprConfig == null || gdprConfig.isStrictDisclosedVendorsTreatment();
    }

    public boolean isValid(TCString consent) {
        return !strictnessEnabled
                || isCreatedBeforeTcfV2M3EnforcementCutoff(consent)
                || !consent.getDisclosedVendors().isEmpty();
    }

    private boolean isCreatedBeforeTcfV2M3EnforcementCutoff(TCString consent) {
        final Instant created = consent.getCreated();
        final Instant lastUpdated = consent.getLastUpdated();
        final Instant latest = lastUpdated.isAfter(created) ? lastUpdated : created;

        return latest.isBefore(TCF_2_3_ENFORCEMENT_CUTOFF_DATE);
    }

    public boolean isVendorDisclosed(TCString consent, Integer vendorId) {
        return !strictnessEnabled
                || (vendorId != null
                && (isCreatedBeforeTcfV2M3EnforcementCutoff(consent)
                || consent.getDisclosedVendors().contains(vendorId)));
    }
}
