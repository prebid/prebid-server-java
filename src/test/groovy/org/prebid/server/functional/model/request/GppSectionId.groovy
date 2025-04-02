package org.prebid.server.functional.model.request

import com.fasterxml.jackson.annotation.JsonValue
import com.iab.gpp.encoder.section.HeaderV1
import com.iab.gpp.encoder.section.TcfCaV1
import com.iab.gpp.encoder.section.TcfEuV2
import com.iab.gpp.encoder.section.UsCa
import com.iab.gpp.encoder.section.UsCo
import com.iab.gpp.encoder.section.UsCt
import com.iab.gpp.encoder.section.UsNat
import com.iab.gpp.encoder.section.UsUt
import com.iab.gpp.encoder.section.UsVa
import com.iab.gpp.encoder.section.UspV1

enum GppSectionId {

    TCF_EU_V2(TcfEuV2.ID),
    HEADER_V1(HeaderV1.ID),
    TCF_CA_V1(TcfCaV1.ID),
    USP_V1(UspV1.ID),
    US_NAT_V1(UsNat.ID),
    US_CA_V1(UsCa.ID),
    US_VA_V1(UsVa.ID),
    US_CO_V1(UsCo.ID),
    US_UT_V1(UsUt.ID),
    US_CT_V1(UsCt.ID)

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
