package org.prebid.server.activity.infrastructure.rule;

import org.prebid.server.activity.ComponentType;
import org.prebid.server.activity.infrastructure.payload.ActivityCallPayload;

import java.util.Set;

public final class ComponentRule implements Rule {

    private final Set<ComponentType> componentTypes;
    private final Set<String> componentNames;
    private final boolean allowed;

    public ComponentRule(Set<ComponentType> componentTypes,
                         Set<String> componentNames,
                         boolean allowed) {

        this.componentTypes = componentTypes;
        this.componentNames = componentNames;
        this.allowed = allowed;
    }

    @Override
    public boolean matches(ActivityCallPayload activityCallPayload) {
        return (componentTypes == null || componentTypes.contains(activityCallPayload.componentType()))
                && (componentNames == null || componentNames.contains(activityCallPayload.componentName()));
    }

    @Override
    public boolean allowed() {
        return allowed;
    }
}
