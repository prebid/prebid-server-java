package org.prebid.server.functional.model.request

import com.fasterxml.jackson.annotation.JsonValue
import com.iab.gpp.encoder.section.HeaderV1
import com.iab.gpp.encoder.section.TcfCaV1
import com.iab.gpp.encoder.section.TcfEuV2
import com.iab.gpp.encoder.section.UsCaV1
import com.iab.gpp.encoder.section.UsCoV1
import com.iab.gpp.encoder.section.UsCtV1
import com.iab.gpp.encoder.section.UsNatV1
import com.iab.gpp.encoder.section.UsUtV1
import com.iab.gpp.encoder.section.UsVaV1
import com.iab.gpp.encoder.section.UspV1

enum GppSectionId {

    TCF_EU_V2(TcfEuV2.ID),
    HEADER_V1(HeaderV1.ID),
    TCF_CA_V1(TcfCaV1.ID),
    USP_V1(UspV1.ID),
    USP_NAT_V1(UsNatV1.ID),
    USP_CA_V1(UsCaV1.ID),
    USP_VA_V1(UsVaV1.ID),
    USP_CO_V1(UsCoV1.ID),
    USP_UT_V1(UsUtV1.ID),
    USP_CT_V1(UsCtV1.ID)

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
