package org.prebid.server.hooks.modules.optable.targeting.model.net;

import lombok.Getter;
import lombok.Value;

@Value(staticConstructor = "of")
public class OptableError {

    String message;

    Type type;

    @Getter
    public enum Type {

        BAD_INPUT(2),

        BAD_SERVER_RESPONSE(3),

        TIMEOUT(4),

        GENERIC(5);

        private final Integer code;

        Type(Integer errorCode) {
            this.code = errorCode;
        }
    }
}
