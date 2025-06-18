package org.prebid.server.hooks.modules.rule.engine.core.request.context;

import com.iab.openrtb.request.BidRequest;
import lombok.Value;

@Value(staticConstructor = "of")
public class RequestSchemaContext {

    BidRequest bidRequest;

    String impId;

    String datacenter;
}
