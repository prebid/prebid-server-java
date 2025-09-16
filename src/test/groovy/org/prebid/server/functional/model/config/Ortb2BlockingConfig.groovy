package org.prebid.server.functional.model.config

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class Ortb2BlockingConfig {

    Map<Ortb2BlockingAttribute, Ortb2BlockingAttributeConfig> attributes
}
