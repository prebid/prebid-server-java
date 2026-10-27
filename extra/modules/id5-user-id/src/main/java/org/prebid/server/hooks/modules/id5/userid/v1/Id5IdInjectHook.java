package org.prebid.server.hooks.modules.id5.userid.v1;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.User;
import io.vertx.core.Future;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.hooks.execution.v1.InvocationResultImpl;
import org.prebid.server.hooks.execution.v1.bidder.BidderRequestPayloadImpl;
import org.prebid.server.hooks.modules.id5.userid.v1.filter.FilterResult;
import org.prebid.server.hooks.modules.id5.userid.v1.filter.InjectFilter;
import org.prebid.server.hooks.modules.id5.userid.v1.model.Id5UserId;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.bidder.BidderInvocationContext;
import org.prebid.server.hooks.v1.bidder.BidderRequestHook;
import org.prebid.server.hooks.v1.bidder.BidderRequestPayload;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.util.ListUtil;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Id5IdInjectHook implements BidderRequestHook {

    private static final Logger logger = LoggerFactory.getLogger(Id5IdInjectHook.class);

    public static final String CODE = "id5-user-id-inject-bidder-request-hook";

    private final String inserter;
    private final List<InjectFilter> filters;

    public Id5IdInjectHook(String inserter, List<InjectFilter> filters) {
        this.inserter = inserter;
        this.filters = Objects.requireNonNull(filters);
    }

    @Override
    public Future<InvocationResult<BidderRequestPayload>> call(BidderRequestPayload payload,
                                                               BidderInvocationContext invocationContext) {

        if (BidRequestUtils.isId5IdPresent(payload.bidRequest())) {
            return noInvocation("id5id already present in bidRequest", invocationContext);
        }

        final FilterResult filterResult = shouldInvoke(payload, invocationContext);
        if (!filterResult.isAccepted()) {
            return noInvocation(filterResult.reason(), invocationContext);
        }

        final String bidder = invocationContext.bidder();
        final Future<Id5UserId> userIdFuture = Id5IdModuleContext.from(invocationContext).getId5UserIdFuture();
        return userIdFuture.map(id5UserId -> {
            logger.debug("id5-user-id-inject: resolved userId for bidder {}", bidder);
            final List<Eid> eids = id5UserId == null ? List.of() : id5UserId.eids();
            if (CollectionUtils.isEmpty(eids)) {
                return resultBuilder(invocationContext)
                        .status(InvocationStatus.success)
                        .action(InvocationAction.no_action)
                        .debugMessages(Collections.singletonList("id5-user-id-inject: no ids to inject"))
                        .build();
            }
            logger.debug("id5-user-id-inject: user updated with {} eid(s)", eids.size());
            return resultBuilder(invocationContext)
                    .status(InvocationStatus.success)
                    .action(InvocationAction.update)
                    .payloadUpdate(existing -> BidderRequestPayloadImpl.of(
                            updateBidRequest(existing.bidRequest(), eids)))
                    .debugMessages(Collections.singletonList("id5-user-id-inject: updated user with id5 eids"))
                    .build();
        });
    }

    @Override
    public String code() {
        return CODE;
    }

    private FilterResult shouldInvoke(BidderRequestPayload payload,
                                      BidderInvocationContext invocationContext) {

        for (InjectFilter filter : filters) {
            final FilterResult result = filter.shouldInvoke(payload, invocationContext);
            if (!result.isAccepted()) {
                return result;
            }
        }
        return FilterResult.accepted();
    }

    private BidRequest updateBidRequest(BidRequest bidRequest, List<Eid> eidsToAdd) {
        final User originalUser = bidRequest.getUser();
        final List<Eid> enrichedEids = eidsToAdd.stream()
                .map(eid -> eid.toBuilder().inserter(inserter).build())
                .toList();

        final User updatedUser = originalUser == null
                ? User.builder().eids(enrichedEids).build()
                : originalUser.toBuilder().eids(mergeEids(originalUser, enrichedEids)).build();

        return bidRequest.toBuilder().user(updatedUser).build();
    }

    private static InvocationResultImpl.InvocationResultImplBuilder<BidderRequestPayload> resultBuilder(
            BidderInvocationContext bidderInvocationContext) {

        return InvocationResultImpl.<BidderRequestPayload>builder()
                // propagate moduleContext for another bidder requests hook calls
                .moduleContext(bidderInvocationContext.moduleContext());
    }

    private static Future<InvocationResult<BidderRequestPayload>> noInvocation(
            String reason, BidderInvocationContext invocationContext) {

        logger.debug("id5-user-id-inject: skipped, {}", reason);
        return Future.succeededFuture(resultBuilder(invocationContext)
                .status(InvocationStatus.success)
                .action(InvocationAction.no_invocation)
                .debugMessages(Collections.singletonList("id5-user-id-inject: " + reason))
                .build());
    }

    private static List<Eid> mergeEids(User user, List<Eid> newEids) {
        return CollectionUtils.isEmpty(user.getEids())
                ? newEids
                : ListUtil.union(user.getEids(), newEids);
    }
}
