package org.prebid.server.hooks.execution.v1;

import lombok.Value;
import lombok.experimental.Accessors;
import org.prebid.server.execution.Timeout;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.model.Endpoint;

@Accessors(fluent = true)
@Value(staticConstructor = "of")
public class InvocationContextImpl implements InvocationContext {

    Timeout timeout;

    Endpoint endpoint;
}
