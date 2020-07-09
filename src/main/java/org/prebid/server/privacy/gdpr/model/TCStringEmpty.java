package org.prebid.server.privacy.gdpr.model;

import com.iabtcf.decoder.TCString;
import com.iabtcf.utils.BitSetIntIterable;
import com.iabtcf.utils.IntIterable;
import com.iabtcf.v2.PublisherRestriction;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

public class TCStringEmpty implements TCString {

    public static TCString create() {
        return new TCStringEmpty();
    }

    @Override
    public int getVersion() {
        return 2;
    }

    @Override
    public Instant getCreated() {
        return null;
    }

    @Override
    public Instant getLastUpdated() {
        return null;
    }

    @Override
    public int getCmpId() {
        return 0;
    }

    @Override
    public int getCmpVersion() {
        return 0;
    }

    @Override
    public int getConsentScreen() {
        return 0;
    }

    @Override
    public String getConsentLanguage() {
        return null;
    }

    @Override
    public int getVendorListVersion() {
        return 0;
    }

    @Override
    public IntIterable getPurposesConsent() {
        return BitSetIntIterable.EMPTY;
    }

    @Override
    public IntIterable getVendorConsent() {
        return BitSetIntIterable.EMPTY;
    }

    @Override
    public boolean getDefaultVendorConsent() {
        return false;
    }

    @Override
    public int getTcfPolicyVersion() {
        return 0;
    }

    @Override
    public boolean isServiceSpecific() {
        return false;
    }

    @Override
    public boolean getUseNonStandardStacks() {
        return false;
    }

    @Override
    public IntIterable getSpecialFeatureOptIns() {
        return BitSetIntIterable.EMPTY;
    }

    @Override
    public IntIterable getPurposesLITransparency() {
        return BitSetIntIterable.EMPTY;
    }

    @Override
    public boolean getPurposeOneTreatment() {
        return false;
    }

    @Override
    public String getPublisherCC() {
        return null;
    }

    @Override
    public IntIterable getVendorLegitimateInterest() {
        return BitSetIntIterable.EMPTY;
    }

    @Override
    public List<PublisherRestriction> getPublisherRestrictions() {
        return Collections.emptyList();
    }

    @Override
    public IntIterable getAllowedVendors() {
        return BitSetIntIterable.EMPTY;
    }

    @Override
    public IntIterable getDisclosedVendors() {
        return BitSetIntIterable.EMPTY;
    }

    @Override
    public IntIterable getPubPurposesConsent() {
        return BitSetIntIterable.EMPTY;
    }

    @Override
    public IntIterable getPubPurposesLITransparency() {
        return BitSetIntIterable.EMPTY;
    }

    @Override
    public IntIterable getCustomPurposesConsent() {
        return BitSetIntIterable.EMPTY;
    }

    @Override
    public IntIterable getCustomPurposesLITransparency() {
        return BitSetIntIterable.EMPTY;
    }
}
