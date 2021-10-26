package org.prebid.server.functional.model.config

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class StageExecutionPlan {

    List<ExecutionGroup> groups
}
