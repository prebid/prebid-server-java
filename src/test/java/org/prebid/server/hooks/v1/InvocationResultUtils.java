package org.prebid.server.hooks.v1;

import org.prebid.server.hooks.execution.v1.InvocationResultImpl;

public class InvocationResultUtils {

    private InvocationResultUtils() {

    }

    public static <PAYLOAD> InvocationResult<PAYLOAD> succeeded(PayloadUpdate<PAYLOAD> payloadUpdate) {
        return InvocationResultImpl.<PAYLOAD>builder()
                .status(InvocationStatus.success)
                .action(InvocationAction.update)
                .payloadUpdate(payloadUpdate)
                .build();
    }

    public static <PAYLOAD> InvocationResult<PAYLOAD> succeeded(PayloadUpdate<PAYLOAD> payloadUpdate,
                                                                Object moduleContext) {

        return InvocationResultImpl.<PAYLOAD>builder()
                .status(InvocationStatus.success)
                .action(InvocationAction.update)
                .payloadUpdate(payloadUpdate)
                .moduleContext(moduleContext)
                .build();
    }

    public static <PAYLOAD> InvocationResult<PAYLOAD> failed(String message) {
        return InvocationResultImpl.<PAYLOAD>builder()
                .status(InvocationStatus.failure)
                .message(message)
                .build();
    }

    public static <PAYLOAD> InvocationResult<PAYLOAD> noAction() {
        return InvocationResultImpl.<PAYLOAD>builder()
                .status(InvocationStatus.success)
                .action(InvocationAction.no_action)
                .build();
    }

    public static <PAYLOAD> InvocationResult<PAYLOAD> rejected(String message) {
        return InvocationResultImpl.<PAYLOAD>builder()
                .status(InvocationStatus.success)
                .action(InvocationAction.reject)
                .message(message)
                .build();
    }
}
