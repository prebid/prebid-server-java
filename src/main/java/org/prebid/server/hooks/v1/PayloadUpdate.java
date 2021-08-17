package org.prebid.server.hooks.v1;

import java.util.function.UnaryOperator;

@FunctionalInterface
public interface PayloadUpdate<PAYLOAD> extends UnaryOperator<PAYLOAD> {

    static <PAYLOAD> PayloadUpdate<PAYLOAD> identity() {
        return payload -> payload;
    }
}
