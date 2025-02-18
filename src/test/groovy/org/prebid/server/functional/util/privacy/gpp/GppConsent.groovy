package org.prebid.server.functional.util.privacy.gpp

import com.iab.gpp.encoder.GppModel
import com.iab.gpp.encoder.section.TcfEuV2
import com.iab.gpp.encoder.section.UsCaV1
import com.iab.gpp.encoder.section.UsCoV1
import com.iab.gpp.encoder.section.UsCtV1
import com.iab.gpp.encoder.section.UsNatV1
import com.iab.gpp.encoder.section.UsUtV1
import com.iab.gpp.encoder.section.UsVaV1
import com.iab.gpp.encoder.section.UspV1
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

        private Builder setVersion(int version) {
            fieldValue("Version", version)
        }

        abstract GppConsent build();
    }

    enum Section {

        TCF_EU_V2(TcfEuV2.NAME, TcfEuV2.VERSION),  //2
        USP_V1(UspV1.NAME, UspV1.VERSION),         //6
        US_NAT_V1(UsNatV1.NAME, UsNatV1.VERSION),  //7
        US_NAT_V2(UsNatV1.NAME, 2),        //7
        US_CA_V1(UsCaV1.NAME, UsCaV1.VERSION),     //8
        US_VA_V1(UsVaV1.NAME, UsVaV1.VERSION),     //9
        US_CO_V1(UsCoV1.NAME, UsCoV1.VERSION),     //10
        US_UT_V1(UsUtV1.NAME, UsUtV1.VERSION),     //11
        US_CT_V1(UsCtV1.NAME, UsCtV1.VERSION),     //12

        final String name
        final int version

        Section(String name, int version) {
            this.name = name
            this.version = version
        }
    }
}
