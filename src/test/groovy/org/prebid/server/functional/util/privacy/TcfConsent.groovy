package org.prebid.server.functional.util.privacy

import com.iabtcf.encoder.PublisherRestrictionEntry
import com.iabtcf.encoder.TCStringEncoder
import com.iabtcf.utils.BitSetIntIterable
import com.iabtcf.v2.RestrictionType
import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.config.Purpose
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.util.privacy.TcfConsent.TcfPolicyVersion.TCF_POLICY_V2

class TcfConsent implements ConsentString {

    public static final Integer RUBICON_VENDOR_ID = PBSUtils.getRandomNumber(0, 65534)
    public static final Integer GENERIC_VENDOR_ID = PBSUtils.getRandomNumber(0, 65534)
    public static final Integer VENDOR_LIST_VERSION = PBSUtils.getRandomNumber(0, 4095)

    private final TCStringEncoder.Builder tcStringEncoder

    private TcfConsent(Builder builder) {
        this.tcStringEncoder = builder.tcStringEncoder
    }

    @Override
    String getConsentString() {
        tcStringEncoder.encode()
    }

    @Override
    String toString() {
        consentString
    }

    static class Builder {

        private TCStringEncoder.Builder tcStringEncoder

        Builder() {
            tcStringEncoder = TCStringEncoder.newBuilder()
            setVersion(2)
            setTcfPolicyVersion(TCF_POLICY_V2.value)
            setVendorListVersion(VENDOR_LIST_VERSION)
        }

        Builder setVersion(Integer version) {
            tcStringEncoder.version(version)
            this
        }

        Builder setVendorListVersion(Integer vendorListVersion) {
            tcStringEncoder.vendorListVersion(vendorListVersion)
            this
        }

        Builder setTcfPolicyVersion(Integer tcfPolicyVersion) {
            tcStringEncoder.tcfPolicyVersion(tcfPolicyVersion)
            this
        }

        Builder setPurposesConsent(PurposeId purposeConsent) {
            tcStringEncoder.addPurposesConsent(purposeConsent.value)
            this
        }

        Builder setPurposesConsent(List<PurposeId> purposesConsent) {
            tcStringEncoder.addPurposesConsent(BitSetIntIterable.from(purposesConsent.collect { it.value }))
            this
        }

        Builder setVendorConsent(Integer vendorConsent) {
            tcStringEncoder.addVendorConsent(vendorConsent)
            this
        }

        Builder setVendorConsent(List<Integer> vendorConsent) {
            tcStringEncoder.addVendorConsent(BitSetIntIterable.from(vendorConsent))
            this
        }

        Builder setVendorLegitimateInterest(Integer vendorLegitimateInterest) {
            tcStringEncoder.addVendorLegitimateInterest(vendorLegitimateInterest)
            this
        }

        Builder setVendorLegitimateInterest(List<Integer> vendorLegitimateInterest) {
            tcStringEncoder.addVendorLegitimateInterest(BitSetIntIterable.from(vendorLegitimateInterest))
            this
        }



        Builder setPurposesLITransparency(PurposeId purposesLITransparency) {
            tcStringEncoder.addPurposesLITransparency(purposesLITransparency.value)
            this
        }

        Builder setPublisherRestrictionEntry(PurposeId purposeId, RestrictionType restrictionType, Integer vendorId) {
            def publisherRestrictionEntry = PublisherRestrictionEntry
                    .newBuilder()
                    .purposeId(purposeId.value)
                    .restrictionType(restrictionType)
                    .addVendor(vendorId)
                    .build()
            tcStringEncoder.addPublisherRestrictionEntry(publisherRestrictionEntry)
            this
        }

        TcfConsent build() {
            new TcfConsent(this)
        }
    }

    enum PurposeId {

        DEVICE_ACCESS(1),
        BASIC_ADS(2),
        PERSONALIZED_ADS_PROFILE(3),
        PERSONALIZED_ADS(4),
        PERSONALIZED_CONTENT_PROFILE(5),
        PERSONALIZED_CONTENT(6),
        MEASURE_AD_PERFORMANCE(7),
        MEASURE_CONTENT_PERFORMANCE(8),
        AUDIENCE_MARKET_RESEARCH(9),
        DEVELOPMENT_IMPROVE_PRODUCTS(10)

        final int value

        PurposeId(int value) {
            this.value = value
        }

        static PurposeId convertPurposeToPurposeId(Purpose purpose) {
            int purposeValue = purpose.ordinal() + 1
            values().find { it.value == purposeValue }
        }
    }

    enum TcfPolicyVersion {

        TCF_POLICY_V2(2),
        TCF_POLICY_V3(4)

        final int value

        TcfPolicyVersion(int value) {
            this.value = value
        }

        int getVendorListVersion() {
            (this == TCF_POLICY_V3) ? 3 : 2
        }

        int getReversedListVersion() {
            (this == TCF_POLICY_V3) ? 2 : 3
        }
    }
}
