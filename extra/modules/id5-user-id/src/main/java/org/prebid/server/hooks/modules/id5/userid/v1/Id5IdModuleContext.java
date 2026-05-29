package org.prebid.server.hooks.modules.id5.userid.v1;

import io.vertx.core.Future;
import lombok.Getter;
import org.prebid.server.hooks.modules.id5.userid.v1.model.Id5UserId;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;

import javax.validation.constraints.NotNull;

@Getter
public class Id5IdModuleContext {

    private static final Id5IdModuleContext EMPTY = new Id5IdModuleContext(Future.succeededFuture());
    private final Future<Id5UserId> id5UserIdFuture;

    public Id5IdModuleContext(Future<Id5UserId> id5UserIdFuture) {
        this.id5UserIdFuture = id5UserIdFuture;
    }

    @NotNull
    static Id5IdModuleContext from(AuctionInvocationContext invocationContext) {
        final Object moduleContext = invocationContext.moduleContext();
        if (moduleContext instanceof Id5IdModuleContext) {
            return (Id5IdModuleContext) moduleContext;
        }
        return EMPTY;
    }
}
