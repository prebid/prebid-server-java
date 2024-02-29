package org.prebid.server.functional.model.request

import com.fasterxml.jackson.annotation.JsonValue
import com.iab.gpp.encoder.section.HeaderV1
import com.iab.gpp.encoder.section.TcfCaV1
import com.iab.gpp.encoder.section.TcfEuV2
import com.iab.gpp.encoder.section.UspCaV1
import com.iab.gpp.encoder.section.UspCoV1
import com.iab.gpp.encoder.section.UspCtV1
import com.iab.gpp.encoder.section.UspNatV1
import com.iab.gpp.encoder.section.UspUtV1
import com.iab.gpp.encoder.section.UspV1
import com.iab.gpp.encoder.section.UspVaV1

enum GppSectionId {

    TCF_EU_V2(TcfEuV2.ID),
    HEADER_V1(HeaderV1.ID),
    TCF_CA_V1(TcfCaV1.ID),
    USP_V1(UspV1.ID),
    USP_NAT_V1(UspNatV1.ID),
    USP_CA_V1(UspCaV1.ID),
    USP_VA_V1(UspVaV1.ID),
    USP_CO_V1(UspCoV1.ID),
    USP_UT_V1(UspUtV1.ID),
    USP_CT_V1(UspCtV1.ID)

    @JsonValue
    final Integer value

    GppSectionId(Integer value) {
        this.value = value
    }

    String getValue() {
        value as String
    }

    Integer getIntValue(){
        value.toInteger()
    }
}
