package org.prebid.server.hooks.v1;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;

/**
 * Provides simple access to all {@link Hook}s registered in application.
 */
public class HookCatalog {

    private final Set<Module> modules;

    public HookCatalog(Set<Module> modules) {
        this.modules = Objects.requireNonNull(modules);
    }

    public Hook getHookBy(String moduleCode, String hookImplCode) {
        return modules.stream()
                .filter(module -> Objects.equals(module.code(), moduleCode))
                .map(Module::hooks)
                .flatMap(Collection::stream)
                .filter(hook -> Objects.equals(hook.name(), hookImplCode))
                .findFirst()
                .orElse(null);
    }
}
