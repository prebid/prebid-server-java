package org.prebid.server.hooks.modules.rule.engine.core.request;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import lombok.Value;

@Value(staticConstructor = "of")
public class RequestRuleContext {

    boolean validation;

    Imp imp;

    BidRequest bidRequest;
}
