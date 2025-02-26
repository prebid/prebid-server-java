package org.prebid.server.hooks.modules.rule.engine.core.request;

import com.iab.openrtb.request.Imp;
import lombok.Value;

@Value(staticConstructor = "of")
public class RequestRuleResult {

    Imp adjustedImp;


}
