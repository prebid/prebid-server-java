package org.prebid.server.activity.infrastructure.rule;

import org.prebid.server.activity.ComponentType;
import org.prebid.server.activity.infrastructure.debug.Loggable;
import org.prebid.server.activity.infrastructure.payload.ActivityInvocationPayload;

import java.util.Set;

public final class ComponentRule extends AbstractMatchRule implements Loggable {

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
    public boolean matches(ActivityInvocationPayload activityInvocationPayload) {
        return (componentTypes == null || componentTypes.contains(activityInvocationPayload.componentType()))
                && (componentNames == null || componentNames.contains(activityInvocationPayload.componentName()));
    }

    @Override
    public boolean allowed() {
        return allowed;
    }

    @Override
    public Object asLogEntry() {
        return new ComponentRuleLogEntry(componentTypes, componentNames, allowed);
    }

    private record ComponentRuleLogEntry(Set<ComponentType> componentTypes,
                                         Set<String> componentNames,
                                         boolean allow) {
    }
}
