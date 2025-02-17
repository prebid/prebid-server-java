package org.prebid.server.functional.model.response.auction

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.model.config.ModuleName

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.KebabCaseStrategy)
@EqualsAndHashCode
class ModuleValue {

   ModuleName module
   String richmediaFormat
}
