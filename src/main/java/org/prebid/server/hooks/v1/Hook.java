package org.prebid.server.hooks.v1;

import io.vertx.core.Future;

public interface Hook<PAYLOAD, CONTEXT extends InvocationContext> {

    Future<InvocationResult<PAYLOAD>> call(PAYLOAD payload, CONTEXT invocationContext);

    String code();
}
