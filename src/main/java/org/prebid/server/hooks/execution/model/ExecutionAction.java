package org.prebid.server.hooks.execution.model;

public sealed interface ExecutionAction
        permits ExecutionAction.NoAction, ExecutionAction.Update, ExecutionAction.Reject {

    static NoAction noAction() {
        return NoAction.INSTANCE;
    }

    static Update update() {
        return Update.INSTANCE;
    }

    static Reject reject(Integer nbr) {
        return new Reject(nbr);
    }

    record NoAction() implements ExecutionAction {

        private static final NoAction INSTANCE = new NoAction();
    }

    record Update() implements ExecutionAction {

        private static final Update INSTANCE = new Update();
    }

    record Reject(Integer nbr) implements ExecutionAction {
    }
}
