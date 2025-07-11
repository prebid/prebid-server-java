package org.prebid.server.hooks.modules.rule.engine.core.request.context;

import com.iab.openrtb.request.BidRequest;
import lombok.Value;
import org.prebid.server.hooks.modules.rule.engine.core.request.Granularity;

@Value(staticConstructor = "of")
public class RequestSchemaContext {

    BidRequest bidRequest;

    Granularity granularity;

    String datacenter;
}
