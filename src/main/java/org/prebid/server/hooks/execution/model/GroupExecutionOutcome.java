package org.prebid.server.hooks.execution.model;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class GroupExecutionOutcome {

    List<HookExecutionOutcome> hooks;
}
