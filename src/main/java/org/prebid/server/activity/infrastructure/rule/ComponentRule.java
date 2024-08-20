package org.prebid.server.activity.infrastructure.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.prebid.server.activity.ComponentType;
import org.prebid.server.activity.infrastructure.debug.Loggable;
import org.prebid.server.activity.infrastructure.payload.ComponentActivityInvocationPayload;
import org.prebid.server.activity.infrastructure.payload.CompositeActivityInvocationPayload;

import java.util.Set;

public class ComponentRule extends AbstractMatchRule implements Loggable {

    private final Set<ComponentType> componentTypes;
    private final Set<String> componentNames;
    private final boolean isAllowed;

    public ComponentRule(Set<ComponentType> componentTypes, Set<String> componentNames, boolean isAllowed) {
        this.componentTypes = componentTypes;
        this.componentNames = componentNames;
        this.isAllowed = isAllowed;
    }

    @Override
    public boolean matches(CompositeActivityInvocationPayload payload) {
        final ComponentActivityInvocationPayload component = payload.get(ComponentActivityInvocationPayload.class);
        return (componentTypes == null || componentTypes.contains(component.componentType()))
                && (componentNames == null || componentNames.contains(component.componentName()));
    }

    @Override
    public boolean isAllowed() {
        return isAllowed;
    }

    @Override
    public JsonNode asLogEntry(ObjectMapper mapper) {
        return mapper.valueToTree(new ComponentRuleLogEntry(componentTypes, componentNames, isAllowed));
    }

    private record ComponentRuleLogEntry(Set<ComponentType> componentTypes,
                                         Set<String> componentNames,
                                         boolean allow) {
    }
}
