package org.prebid.server.activity.rule;

import org.prebid.server.activity.ActivityPayload;
import org.prebid.server.activity.ComponentType;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public final class ConditionalRule implements Rule {

    private final Set<ComponentType> componentTypes;
    private final Set<String> componentNames;
    private final boolean allowed;

    private ConditionalRule(Collection<ComponentType> componentTypes,
                            Collection<String> componentNames,
                            boolean allowed) {

        this.componentTypes = setOf(componentTypes);
        this.componentNames = setOf(componentNames);
        this.allowed = allowed;
    }

    private static <V> Set<V> setOf(Collection<V> collection) {
        return collection != null ? new HashSet<>(collection) : null;
    }

    public static ConditionalRule of(Collection<ComponentType> componentTypes,
                                     Collection<String> componentNames,
                                     boolean allowed) {

        return new ConditionalRule(componentTypes, componentNames, allowed);
    }

    @Override
    public boolean matches(ActivityPayload activityPayload) {
        return (componentTypes == null || componentTypes.contains(activityPayload.getComponentType()))
                && (componentNames == null || componentNames.contains(activityPayload.getComponentName()));
    }

    @Override
    public boolean allowed() {
        return allowed;
    }
}
