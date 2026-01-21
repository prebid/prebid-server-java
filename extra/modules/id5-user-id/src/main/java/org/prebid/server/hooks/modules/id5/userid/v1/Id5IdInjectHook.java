package org.prebid.server.hooks.modules.id5.userid.v1;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.User;
import io.vertx.core.Future;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.hooks.execution.v1.InvocationResultImpl;
import org.prebid.server.hooks.execution.v1.bidder.BidderRequestPayloadImpl;
import org.prebid.server.hooks.modules.id5.userid.v1.model.Id5UserId;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.bidder.BidderInvocationContext;
import org.prebid.server.hooks.v1.bidder.BidderRequestHook;
import org.prebid.server.hooks.v1.bidder.BidderRequestPayload;
import org.prebid.server.util.ListUtil;
import org.prebid.server.hooks.modules.id5.userid.v1.filter.InjectActionFilter;
import org.prebid.server.hooks.modules.id5.userid.v1.filter.FilterResult;

import java.util.List;
import java.util.Optional;

@Slf4j
public class Id5IdInjectHook implements BidderRequestHook {

    public static final String CODE = "id5-user-id-inject-hook";
    private final String inserter;
    private final List<InjectActionFilter> filters;

    public Id5IdInjectHook(String inserter) {
        this(inserter, java.util.List.of());
    }

    public Id5IdInjectHook(String inserter, List<InjectActionFilter> filters) {
        this.inserter = inserter;
        this.filters = List.copyOf(filters);
    }

    @Override
    public Future<InvocationResult<BidderRequestPayload>> call(BidderRequestPayload payload,
                                                               BidderInvocationContext invocationContext) {
        try {
            if (BidRequestUtils.isId5IdPresent(payload.bidRequest())) {
                return noInvocation("id5id already present in bidRequest", invocationContext);
            }

            // evaluate inject filters
            final FilterResult filterResult = shouldInvoke(payload, invocationContext);
            if (!filterResult.isAccepted()) {
                return noInvocation(filterResult.reason(), invocationContext);
            }

            final long remainingMs = invocationContext.timeout().remaining();
            if (remainingMs <= 0) {
                return noInvocation("no time left to resolve id5Id", invocationContext);
            }

            final String bidder = invocationContext.bidder();
            log.debug("id5-user-id-inject: remaining time: {}ms for bidder {}", remainingMs, bidder);
            final Future<Id5UserId> userIdFuture = Id5IdModuleContext.from(invocationContext).getId5UserIdFuture();
            return userIdFuture.map(id5UserId -> {
                log.debug("id5-user-id-inject: resolved userId for bidder {}", bidder);
                if (id5UserId == null || CollectionUtils.isEmpty(id5UserId.toEIDs())) {
                    return resultBuilder(invocationContext)
                            .status(InvocationStatus.success)
                            .action(InvocationAction.no_action)
                            .debugMessages(List.of("id5-user-id-inject: no ids to inject"))
                            .build();
                }
                final User originalUser = payload.bidRequest().getUser();
                final List<Eid> eIDs = id5UserId.toEIDs().stream()
                        .map(eid -> eid.toBuilder().inserter(inserter).build())
                        .toList();

                final User updatedUser = Optional.ofNullable(originalUser)
                        .map(user -> user.toBuilder().eids(ListUtil.union(user.getEids(), eIDs)))
                        .orElseGet(() -> User.builder().eids(eIDs))
                        .build();
                final BidRequest updatedBidRequest = payload.bidRequest().toBuilder()
                        .user(updatedUser)
                        .build();
                log.debug("id5-user-id-inject: user updated with {} eid(s)", eIDs.size());
                return resultBuilder(invocationContext)
                        .status(InvocationStatus.success)
                        .action(InvocationAction.update)
                        .payloadUpdate(initial -> BidderRequestPayloadImpl.of(updatedBidRequest))
                        .debugMessages(List.of(
                                "id5-user-id-inject: updated user with id5 eids"))
                        .build();
            });
        } catch (Exception e) {
            log.error("id5-user-id-inject: failed to inject id5id", e);
            return Future.succeededFuture(resultBuilder(invocationContext)
                    .status(InvocationStatus.failure)
                    .action(InvocationAction.no_invocation)
                    .errors(List.of(e.getMessage()))
                    .build());
        }
    }

    @Override
    public String code() {
        return CODE;
    }

    private FilterResult shouldInvoke(BidderRequestPayload payload,
                                      BidderInvocationContext invocationContext) {
        if (filters == null || filters.isEmpty()) {
            return FilterResult.accepted();
        }
        for (InjectActionFilter filter : filters) {
            final FilterResult result = filter.shouldInvoke(payload, invocationContext);
            if (!result.isAccepted()) {
                return result;
            }
        }
        return FilterResult.accepted();
    }

    private static InvocationResultImpl.InvocationResultImplBuilder<BidderRequestPayload> resultBuilder(
            BidderInvocationContext bidderInvocationContext) {
        return InvocationResultImpl.<BidderRequestPayload>builder()
                // propagate moduleContext for another bidder requests hook calls
                .moduleContext(bidderInvocationContext.moduleContext());
    }

    private static Future<InvocationResult<BidderRequestPayload>> noInvocation(
            String reason, BidderInvocationContext invocationContext) {
        log.debug("id5-user-id-inject: skipped, {}", reason);
        return Future.succeededFuture(resultBuilder(invocationContext)
                .status(InvocationStatus.success)
                .action(InvocationAction.no_invocation)
                .debugMessages(List.of("id5-user-id-inject: " + reason))
                .build());
    }
}
