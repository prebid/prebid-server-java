package org.prebid.server.hooks.modules.ortb2.blocking.model;

import lombok.Value;
import org.prebid.server.auction.versionconverter.OrtbVersion;
import org.prebid.server.hooks.modules.ortb2.blocking.core.model.BlockedAttributes;

import java.util.HashMap;
import java.util.Map;

@Value(staticConstructor = "create")
public class ModuleContext {

    Map<String, OrtbVersion> bidderToOrtbVersion = new HashMap<>();
    Map<String, BlockedAttributes> blockedAttributes = new HashMap<>();

    public ModuleContext with(String bidder, OrtbVersion ortbVersion) {
        final ModuleContext moduleContext = copy();
        moduleContext.bidderToOrtbVersion.put(bidder, ortbVersion);

        return moduleContext;
    }

    public ModuleContext with(String bidder, BlockedAttributes blockedAttributes) {
        final ModuleContext moduleContext = copy();
        moduleContext.blockedAttributes.put(bidder, blockedAttributes);

        return moduleContext;
    }

    public OrtbVersion ortbVersionOf(String bidder) {
        return bidderToOrtbVersion.get(bidder);
    }

    public BlockedAttributes blockedAttributesFor(String bidder) {
        return blockedAttributes.get(bidder);
    }

    private ModuleContext copy() {
        final ModuleContext copy = ModuleContext.create();
        copy.bidderToOrtbVersion.putAll(this.bidderToOrtbVersion);
        copy.blockedAttributes.putAll(this.blockedAttributes);

        return copy;
    }
}
