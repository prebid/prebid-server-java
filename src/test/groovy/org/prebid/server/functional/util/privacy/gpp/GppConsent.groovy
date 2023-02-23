package org.prebid.server.functional.util.privacy.gpp

import com.iab.gpp.encoder.GppModel
import com.iab.gpp.encoder.section.TcfEuV2
import com.iab.gpp.encoder.section.UspV1
import org.prebid.server.functional.util.privacy.ConsentString

import java.time.ZoneId
import java.time.ZonedDateTime

abstract class GppConsent implements ConsentString {

    protected final gppModel = new GppModel()

    protected GppConsent(Regions regions, Map<String, Object> fieldValues) {
        fieldValues.each { fieldName, fieldValue ->
            this.gppModel.setFieldValue(regions.value, fieldName, fieldValue)
        }
    }

    @Override
    String getConsentString() {
        this.gppModel?.encode()
    }

    @Override
    String toString() {
        consentString
    }

    protected abstract static class Builder {

        protected Regions regions
        protected def fieldValues = [:]

        Builder(Regions regions) {
            this.regions = regions
            fieldValues.Created = ZonedDateTime.now(ZoneId.of("UTC"))
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

    enum Regions {

        TCFEUV2(TcfEuV2.NAME),
        USPV1(UspV1.NAME)

        final String value

        Regions(String value) {
            this.value = value
        }
    }
}
