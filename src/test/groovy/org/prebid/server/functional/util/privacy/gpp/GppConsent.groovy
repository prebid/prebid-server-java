package org.prebid.server.functional.util.privacy.gpp

import com.iab.gpp.encoder.GppModel
import com.iab.gpp.encoder.section.TcfEuV2
import com.iab.gpp.encoder.section.UspCaV1
import com.iab.gpp.encoder.section.UspCoV1
import com.iab.gpp.encoder.section.UspCtV1
import com.iab.gpp.encoder.section.UspNatV1
import com.iab.gpp.encoder.section.UspUtV1
import com.iab.gpp.encoder.section.UspV1
import com.iab.gpp.encoder.section.UspVaV1
import org.prebid.server.functional.util.privacy.ConsentString

abstract class GppConsent implements ConsentString {

    protected final gppModel = new GppModel()

    protected GppConsent(Section section, Map<String, Object> fieldValues) {
        fieldValues.each { fieldName, fieldValue ->
            this.gppModel.setFieldValue(section.name, fieldName, fieldValue)
        }
    }

    @Override
    String getConsentString() {
        this.gppModel.encode()
    }

    @Override
    String toString() {
        consentString
    }

    protected abstract String encodeSection()

    protected abstract static class Builder {

        protected Section section
        protected Map<String, Object> fieldValues = [:]

        Builder(Section section) {
            this.section = section
            setVersion(section.version)
        }

        Builder fieldValue(String fieldName, Object fieldValue) {
            fieldValues[fieldName] = fieldValue
            this
        }

        Builder fieldValues(Map<String, Object> fieldValues) {
            this.fieldValues.putAll(fieldValues)
            this
        }

        Builder setVersion(int version) {
            fieldValue("Version", version)
        }

        abstract GppConsent build();
    }

    enum Section {

        TCF_EU_V2(TcfEuV2.NAME, TcfEuV2.VERSION),    //2
        USP_V1(UspV1.NAME, UspV1.VERSION),           //6
        USP_NAT_V1(UspNatV1.NAME, UspNatV1.VERSION), //7
        USP_CA_V1(UspCaV1.NAME, UspCaV1.VERSION),    //8
        USP_VA_V1(UspVaV1.NAME, UspVaV1.VERSION),    //9
        USP_CO_V1(UspCoV1.NAME, UspCoV1.VERSION),    //10
        USP_UT_V1(UspUtV1.NAME, UspUtV1.VERSION),    //11
        USP_CT_V1(UspCtV1.NAME, UspCtV1.VERSION),    // 12

        final String name
        final int version

        Section(String name, int version) {
            this.name = name
            this.version = version
        }
    }
}
