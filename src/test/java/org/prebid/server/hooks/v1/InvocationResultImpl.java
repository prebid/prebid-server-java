package org.prebid.server.hooks.v1;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.Accessors;
import org.prebid.server.hooks.v1.analytics.Tags;

import java.util.List;

@Accessors(fluent = true)
@Builder
@Value
public class InvocationResultImpl<PAYLOAD> implements InvocationResult<PAYLOAD> {

    InvocationStatus status;

    String message;

    InvocationAction action;

    PayloadUpdate<PAYLOAD> payloadUpdate;

    List<String> errors;

    List<String> warnings;

    List<String> debugMessages;

    Object moduleContext;

    Tags analyticsTags;

    public static <PAYLOAD> InvocationResult<PAYLOAD> succeeded(PayloadUpdate<PAYLOAD> payloadUpdate) {
        return InvocationResultImpl.<PAYLOAD>builder()
                .status(InvocationStatus.SUCCESS)
                .action(InvocationAction.UPDATE)
                .payloadUpdate(payloadUpdate)
                .build();
    }

    public static <PAYLOAD> InvocationResult<PAYLOAD> failed(String message) {
        return InvocationResultImpl.<PAYLOAD>builder()
                .status(InvocationStatus.FAILURE)
                .message(message)
                .build();
    }

    public static <PAYLOAD> InvocationResult<PAYLOAD> noAction() {
        return InvocationResultImpl.<PAYLOAD>builder()
                .status(InvocationStatus.SUCCESS)
                .action(InvocationAction.NO_ACTION)
                .build();
    }

    public static <PAYLOAD> InvocationResult<PAYLOAD> rejected(String message) {
        return InvocationResultImpl.<PAYLOAD>builder()
                .status(InvocationStatus.SUCCESS)
                .action(InvocationAction.REJECT)
                .message(message)
                .build();
    }
}
