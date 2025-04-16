package org.prebid.server.hooks.modules.rule.engine.core;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.prebid.server.hooks.modules.rule.engine.core.rules.request.BasicRequestRule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.request.RequestRule;

public class RuleParser {

    public RequestRule parse(ObjectNode accountConfig) {
        return new BasicRequestRule(null, null);
    }
}
