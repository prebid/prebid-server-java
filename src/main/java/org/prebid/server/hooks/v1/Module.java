package org.prebid.server.hooks.v1;

import java.util.Set;

/**
 * Cares of the module identification among other modules and supplies a collection of available {@Hook}s.
 */
public interface Module {

    String code();

    Set<Hook> hooks();
}
