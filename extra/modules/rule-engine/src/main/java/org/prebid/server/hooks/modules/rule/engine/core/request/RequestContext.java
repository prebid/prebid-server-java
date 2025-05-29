package org.prebid.server.hooks.modules.rule.engine.core.request;

import com.iab.openrtb.request.BidRequest;
import lombok.Value;

@Value(staticConstructor = "of")
public class RequestContext {

    BidRequest bidRequest;

    String impId;

    String datacenter;
}
