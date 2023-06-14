package org.prebid.server.functional.util.privacy

import com.iabtcf.encoder.TCStringEncoder
import com.iabtcf.utils.BitSetIntIterable

import static io.restassured.RestAssured.given

class TcfConsent implements ConsentString {

    private static final String VENDOR_LIST_URL = "https://vendor-list.consensu.org/v%d/vendor-list.json"
    public static final Integer RUBICON_VENDOR_ID = 52
    public static final Integer GENERIC_VENDOR_ID = RUBICON_VENDOR_ID

    private final TCStringEncoder.Builder tcStringEncoder

    private TcfConsent(Builder builder) {
        this.tcStringEncoder = builder.tcStringEncoder
    }

    private static Integer getVendorListVersion(TcfPolicyVersion version) {
        def vendorListVersion = given().get(VENDOR_LIST_URL.formatted(version.equivalentVendorListVersion)).path("vendorListVersion") as Integer
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

        Builder(TcfPolicyVersion tcfPolicyVersion = TcfPolicyVersion.TCF_V2) {
            tcStringEncoder = TCStringEncoder.newBuilder()
            setVersion(2)
            setTcfPolicyVersion(tcfPolicyVersion.value)
            setVendorListVersion(getVendorListVersion(tcfPolicyVersion))
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

        TCF_V2(2),
        TCF_V3(4),
        TCF_INVALID(63)

        final int value

        TcfPolicyVersion(int value) {
            this.value = value
        }

        int getEquivalentVendorListVersion() {
            (value == 4) ? 3 : 2
        }
    }
}
