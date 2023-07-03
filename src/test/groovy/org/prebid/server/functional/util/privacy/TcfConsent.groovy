package org.prebid.server.functional.util.privacy

import com.iabtcf.encoder.TCStringEncoder
import com.iabtcf.utils.BitSetIntIterable

import static io.restassured.RestAssured.given

class TcfConsent implements ConsentString {

    public static final Integer RUBICON_VENDOR_ID = 52
    public static final Integer GENERIC_VENDOR_ID = RUBICON_VENDOR_ID

    private final TCStringEncoder.Builder tcStringEncoder

    private TcfConsent(Builder builder) {
        this.tcStringEncoder = builder.tcStringEncoder
    }

    private static Integer getVendorListVersion() {
        def vendorListVersion = given().get("https://vendor-list.consensu.org/v2/vendor-list.json")
                .path("vendorListVersion") as Integer
        if (!vendorListVersion) {
            throw new IllegalStateException("Vendor list version is null")
        } else {
            return vendorListVersion
        }

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
            setTcfPolicyVersion(2)
            setVendorListVersion(getVendorListVersion())
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

        Builder setPurposesConsent(List<Integer> purposesConsent) {
            tcStringEncoder.addPurposesConsent(BitSetIntIterable.from(purposesConsent))
            this
        }

        Builder setVendorConsent(List<Integer> vendorConsent) {
            tcStringEncoder.addVendorConsent(BitSetIntIterable.from(vendorConsent))
            this
        }

        Builder addVendorLegitimateInterest(List<Integer> vendorLegitimateInterest) {
            tcStringEncoder.addVendorLegitimateInterest(BitSetIntIterable.from(vendorLegitimateInterest))
            this
        }

        Builder setPurposesLITransparency(PurposeId purposesLITransparency) {
            tcStringEncoder.addPurposesLITransparency(purposesLITransparency.value)
            this
        }

        TcfConsent build() {
            new TcfConsent(this)
        }
    }

    enum PurposeId {

        DEVICE_ACCESS(1),
        BASIC_ADS(2),
        PERSONALIZED_ADS(4),
        MEASURE_AD_PERFORMANCE(7)

        final int value

        PurposeId(int value) {
            this.value = value
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
