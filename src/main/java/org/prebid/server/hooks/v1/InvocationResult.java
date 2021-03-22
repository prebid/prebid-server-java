package org.prebid.server.hooks.v1;

import java.util.List;

public interface InvocationResult<PAYLOAD> {

    InvocationStatus status();

    String message();

    InvocationAction action();

    PayloadUpdate<PAYLOAD> payloadUpdate();

    List<String> errors();

    List<String> warnings();

    List<String> debugMessages();
}
