package org.prebid.server.hooks.execution;

import org.prebid.server.hooks.execution.model.StageWithHookType;
import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.Module;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;

/**
 * Provides simple access to all {@link Hook}s registered in application.
 */
public class HookCatalog {

    private final Collection<Module> modules;
    private final Set<String> moduleConfigPresenceSet;

    public HookCatalog(Collection<Module> modules, Set<String> moduleConfigPresenceSet) {
        this.modules = Objects.requireNonNull(modules);
        this.moduleConfigPresenceSet = Objects.requireNonNull(moduleConfigPresenceSet);
    }

    public <HOOK extends Hook<?, ? extends InvocationContext>> HOOK hookById(
            String moduleCode,
            String hookImplCode,
            StageWithHookType<HOOK> stage) {

        final Class<HOOK> clazz = stage.hookType();
        return modules.stream()
                .filter(module -> Objects.equals(module.code(), moduleCode))
                .map(Module::hooks)
                .flatMap(Collection::stream)
                .filter(hook -> Objects.equals(hook.code(), hookImplCode))
                .filter(clazz::isInstance)
                .map(clazz::cast)
                .findFirst()
                .orElse(null);
    }

    public boolean hasHostConfig(String moduleCode) {
        return moduleConfigPresenceSet.contains(moduleCode);
    }
}
