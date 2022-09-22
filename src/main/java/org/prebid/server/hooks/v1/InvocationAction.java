package org.prebid.server.hooks.v1;

public sealed interface InvocationAction
        permits InvocationAction.NoAction, InvocationAction.Update, InvocationAction.Reject {

    static NoAction noAction() {
        return NoAction.INSTANCE;
    }

    static Update update() {
        return Update.INSTANCE;
    }

    static Reject reject(Integer nbr) {
        return new Reject(nbr);
    }

    record NoAction() implements InvocationAction {

        private static final NoAction INSTANCE = new NoAction();
    }

    record Update() implements InvocationAction {

        private static final Update INSTANCE = new Update();
    }

    record Reject(Integer nbr) implements InvocationAction {
    }
}
