package org.prebid.server.hooks.v1;

import java.util.Collection;

/**
 * Cares of the module identification among other modules and supplies a collection of available {@link Hook}s.
 * <p>
 * This interface is used to keep knowledge of which {@link Hook} is belongs to certain {@link Module}
 * while running execution plan.
 */
public interface Module {

    /**
     * An identifier that should be unique among other available modules.
     */
    String code();

    /**
     * Collection of hooks available through the module.
     */
    Collection<? extends Hook<?, ? extends InvocationContext>> hooks();
}
