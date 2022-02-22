package org.prebid.server.hooks.modules.ortb2.blocking.core;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import org.prebid.server.hooks.modules.ortb2.blocking.core.exception.InvalidAccountConfigurationException;
import org.prebid.server.hooks.modules.ortb2.blocking.core.model.BlockedAttributes;
import org.prebid.server.hooks.modules.ortb2.blocking.core.model.ExecutionResult;
import org.prebid.server.hooks.modules.ortb2.blocking.core.model.Result;

import java.util.Objects;

public class BlockedAttributesResolver {

    private final BidRequest bidRequest;
    private final String bidder;
    private final ObjectNode accountConfig;
    private final boolean debugEnabled;

    private BlockedAttributesResolver(
            BidRequest bidRequest,
            String bidder,
            ObjectNode accountConfig,
            boolean debugEnabled) {

        this.bidRequest = bidRequest;
        this.bidder = bidder;
        this.accountConfig = accountConfig;
        this.debugEnabled = debugEnabled;
    }

    public static BlockedAttributesResolver create(
            BidRequest bidRequest,
            String bidder,
            ObjectNode accountConfig,
            boolean debugEnabled) {

        return new BlockedAttributesResolver(
                Objects.requireNonNull(bidRequest),
                Objects.requireNonNull(bidder),
                accountConfig,
                debugEnabled);
    }

    public ExecutionResult<BlockedAttributes> resolve() {
        final AccountConfigReader accountConfigReader = AccountConfigReader.create(accountConfig, bidder, debugEnabled);

        try {
            final Result<BlockedAttributes> blockedAttributesResult =
                    accountConfigReader.blockedAttributesFor(bidRequest);

            return ExecutionResult.<BlockedAttributes>builder()
                    .value(blockedAttributesResult.getValue())
                    .warnings(blockedAttributesResult.getMessages())
                    .build();
        } catch (InvalidAccountConfigurationException e) {
            return debugEnabled ? ExecutionResult.withError(e.getMessage()) : ExecutionResult.empty();
        }
    }
}
