package org.prebid.server.functional.util.privacy.gpp

import com.iab.gpp.encoder.GppModel
import com.iab.gpp.encoder.section.TcfEuV2
import com.iab.gpp.encoder.section.UsCa
import com.iab.gpp.encoder.section.UsCo
import com.iab.gpp.encoder.section.UsCt
import com.iab.gpp.encoder.section.UsNat
import com.iab.gpp.encoder.section.UsUt
import com.iab.gpp.encoder.section.UsVa
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

        Builder setVersion(int version) {
            fieldValue("Version", version)
        }

        abstract GppConsent build();
    }

    enum Section {

        TCF_EU_V2(TcfEuV2.NAME, TcfEuV2.VERSION),  //2
        USP_V1(UspV1.NAME, UspV1.VERSION),         //6
        US_NAT_V1(UsNat.NAME, UsNat.VERSION),  //7
        US_CA_V1(UsCa.NAME, UsCa.VERSION),     //8
        US_VA_V1(UsVa.NAME, UsVa.VERSION),     //9
        US_CO_V1(UsCo.NAME, UsCo.VERSION),     //10
        US_UT_V1(UsUt.NAME, UsUt.VERSION),     //11
        US_CT_V1(UsCt.NAME, UsCt.VERSION),     //12

        final String name
        final int version

        Section(String name, int version) {
            this.name = name
            this.version = version
        }
    }
}
