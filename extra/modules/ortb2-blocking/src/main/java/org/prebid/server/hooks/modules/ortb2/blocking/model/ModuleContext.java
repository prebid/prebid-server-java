package org.prebid.server.hooks.modules.ortb2.blocking.model;

import org.prebid.server.hooks.modules.ortb2.blocking.core.model.BlockedAttributes;

import java.util.HashMap;
import java.util.Map;

public class ModuleContext {

    private final Map<String, BlockedAttributes> blockedAttributes = new HashMap<>();

    public static ModuleContext create(String bidder, BlockedAttributes blockedAttributes) {
        final ModuleContext moduleContext = new ModuleContext();
        moduleContext.blockedAttributes.put(bidder, blockedAttributes);

        return moduleContext;
    }

    public ModuleContext with(String bidder, BlockedAttributes blockedAttributes) {
        final ModuleContext moduleContext = new ModuleContext();
        moduleContext.blockedAttributes.putAll(this.blockedAttributes);
        moduleContext.blockedAttributes.put(bidder, blockedAttributes);

        return moduleContext;
    }

    public BlockedAttributes blockedAttributesFor(String bidder) {
        return blockedAttributes.get(bidder);
    }
}
