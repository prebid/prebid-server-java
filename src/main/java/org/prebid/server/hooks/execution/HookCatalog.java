package org.prebid.server.hooks.execution;

import org.prebid.server.hooks.execution.model.HookId;
import org.prebid.server.hooks.execution.model.StageWithHookType;
import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.Module;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.log.LoggerFactory;

import java.util.Collection;
import java.util.Objects;

public class HookCatalog {

    private static final ConditionalLogger conditionalLogger =
            new ConditionalLogger(LoggerFactory.getLogger(HookCatalog.class));

    private final Collection<Module> modules;

    public HookCatalog(Collection<Module> modules) {
        this.modules = Objects.requireNonNull(modules);
    }

    public <HOOK extends Hook<?, ? extends InvocationContext>> HOOK hookById(HookId hookId,
                                                                             StageWithHookType<HOOK> stage) {

        final Class<HOOK> clazz = stage.hookType();
        return modules.stream()
                .filter(module -> Objects.equals(module.code(), hookId.getModuleCode()))
                .map(Module::hooks)
                .flatMap(Collection::stream)
                .filter(hook -> Objects.equals(hook.code(), hookId.getHookImplCode()))
                .filter(clazz::isInstance)
                .map(clazz::cast)
                .findFirst()
                .orElseThrow(() -> {
                    logAbsentHook(hookId);
                    return new IllegalArgumentException("Hook implementation does not exist or disabled");
                });
    }

    private static void logAbsentHook(HookId hookId) {
        conditionalLogger.error("Hook implementation %s does not exist or disabled".formatted(hookId), 0.01d);
    }
}
