package org.prebid.server.hooks.modules.id5.userid.v1;

import io.vertx.core.Future;
import org.prebid.server.hooks.execution.v1.InvocationResultImpl;
import org.prebid.server.hooks.modules.id5.userid.v1.fetch.FetchClient;
import org.prebid.server.hooks.modules.id5.userid.v1.filter.FetchActionFilter;
import org.prebid.server.hooks.modules.id5.userid.v1.filter.FilterResult;
import org.prebid.server.hooks.modules.id5.userid.v1.model.Id5PartnerIdProvider;
import org.prebid.server.hooks.modules.id5.userid.v1.model.Id5UserId;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.hooks.v1.auction.ProcessedAuctionRequestHook;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class Id5IdFetchHook implements ProcessedAuctionRequestHook {

    private static final Logger logger = LoggerFactory.getLogger(Id5IdFetchHook.class);

    public static final String CODE = "id5-user-id-fetch-hook";

    private final FetchClient fetchClient;
    private final List<FetchActionFilter> filters;
    private final Id5PartnerIdProvider partnerIdProvider;

    public Id5IdFetchHook(FetchClient fetchClient,
                          List<FetchActionFilter> filters,
                          Id5PartnerIdProvider partnerIdProvider) {

        this.fetchClient = Objects.requireNonNull(fetchClient);
        this.filters = Objects.requireNonNull(filters);
        this.partnerIdProvider = Objects.requireNonNull(partnerIdProvider);
    }

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(AuctionRequestPayload payload,
                                                                AuctionInvocationContext invocationContext) {

        if (BidRequestUtils.isId5IdPresent(payload.bidRequest())) {
            return noInvocation("id5id already present in bidRequest");
        }
        final FilterResult filterResult = shouldInvoke(payload, invocationContext);
        if (!filterResult.isAccepted()) {
            return noInvocation(filterResult.reason());
        }
        final Optional<Long> maybePartnerId = partnerIdProvider.getPartnerId(invocationContext.auctionContext());
        if (maybePartnerId.isEmpty()) {
            return noInvocation("partner id not configured");
        }
        final Future<Id5UserId> id5IdFuture = fetchClient.fetch(maybePartnerId.get(), payload, invocationContext);
        return Future.succeededFuture(
                InvocationResultImpl.<AuctionRequestPayload>builder()
                        .status(InvocationStatus.success)
                        .action(InvocationAction.no_action)
                        .moduleContext(new Id5IdModuleContext(id5IdFuture))
                        .debugMessages(Collections.singletonList("id5-user-id-fetch: id5id fetched"))
                        .build());
    }

    @Override
    public String code() {
        return CODE;
    }

    private FilterResult shouldInvoke(AuctionRequestPayload payload, AuctionInvocationContext invocationContext) {
        for (FetchActionFilter filter : filters) {
            final FilterResult result = filter.shouldInvoke(payload, invocationContext);
            if (!result.isAccepted()) {
                return result;
            }
        }
        return FilterResult.accepted();
    }

    private static Future<InvocationResult<AuctionRequestPayload>> noInvocation(String msg) {
        logger.debug("id5-user-id-fetch: skipped, {}", msg);
        return Future.succeededFuture(InvocationResultImpl.<AuctionRequestPayload>builder()
                .status(InvocationStatus.success)
                .action(InvocationAction.no_invocation)
                .debugMessages(Collections.singletonList("id5-user-id-fetch: " + msg))
                .build());
    }
}
