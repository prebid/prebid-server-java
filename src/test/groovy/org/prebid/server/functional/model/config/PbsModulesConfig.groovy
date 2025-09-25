package org.prebid.server.functional.model.config

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString
import org.prebid.server.functional.model.request.auction.RichmediaFilter

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.KebabCaseStrategy)
class PbsModulesConfig {

    RichmediaFilter pbRichmediaFilter
    Ortb2BlockingConfig ortb2Blocking
    PbResponseCorrection pbResponseCorrection
    PbRequestCorrectionConfig pbRequestCorrection
    OptableTargetingConfig optableTargeting
}
