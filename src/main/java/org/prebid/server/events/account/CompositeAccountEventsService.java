package org.prebid.server.events.account;

import io.vertx.core.Future;
import org.prebid.server.execution.Timeout;

import java.util.List;
import java.util.ListIterator;
import java.util.Objects;

public class CompositeAccountEventsService implements AccountEventsService {
    private final Proxy proxy;

    public CompositeAccountEventsService(List<AccountEventsService> delegates) {
        if (Objects.requireNonNull(delegates).isEmpty()) {
            throw new IllegalArgumentException("At least one application settings implementation required");
        }
        proxy = createProxy(delegates);
    }

    private static Proxy createProxy(List<AccountEventsService> delegates) {
        Proxy proxy = null;

        final ListIterator<AccountEventsService> iterator = delegates.listIterator(delegates.size());
        while (iterator.hasPrevious()) {
            proxy = new Proxy(iterator.previous(), proxy);
        }

        return proxy;
    }

    @Override
    public Future<Boolean> eventsEnabled(String accountId, Timeout timeout) {
        return proxy.eventsEnabled(accountId, timeout);
    }

    private static class Proxy implements AccountEventsService {

        private AccountEventsService accountEventsService;
        private Proxy next;

        private Proxy(AccountEventsService accountEventsService, Proxy next) {
            this.accountEventsService = accountEventsService;
            this.next = next;
        }

        @Override
        public Future<Boolean> eventsEnabled(String accountId, Timeout timeout) {
            return accountEventsService.eventsEnabled(accountId, timeout)
                    .compose(eventEnabled -> eventEnabled == null && next != null
                            ? next.eventsEnabled(accountId, timeout)
                            : Future.succeededFuture(eventEnabled))
                    .recover(throwable -> next != null
                            ? next.eventsEnabled(accountId, timeout)
                            : Future.succeededFuture(false));
        }
    }
}
