package org.prebid.server.functional.util.privacy.gpp

import com.iab.gpp.encoder.GppModel
import com.iab.gpp.encoder.section.TcfEuV2
import com.iab.gpp.encoder.section.UspV1
import org.prebid.server.functional.util.privacy.ConsentString

abstract class GppConsent implements ConsentString {

    protected final gppModel = new GppModel()

    protected GppConsent(Section regions, Map<String, Object> fieldValues) {
        fieldValues.each { fieldName, fieldValue ->
            this.gppModel.setFieldValue(regions.name, fieldName, fieldValue)
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

    protected abstract static class Builder {

        protected Section section
        protected def fieldValues = [:]

        Builder(Section regions) {
            this.section = regions
            setVersion(regions.version)
        }

        Builder fieldValue(String fieldName, Object fieldValue) {
            fieldValues[fieldName] = fieldValue
            this
        }

        Builder fieldValues(Map<String, Object> fieldValues) {
            this.fieldValues = [*this.fieldValues, *fieldValues]
            this
        }

        Builder setVersion(int version) {
            fieldValue("Version", version)
        }

        abstract GppConsent build();
    }

    enum Section {

        TCFEUV2(TcfEuV2.NAME, TcfEuV2.VERSION),
        USPV1(UspV1.NAME, UspV1.VERSION)

        final String name
        final int version

        Section(String name, int version) {
            this.name = name
            this.version = version
        }
    }
}
