package org.prebid.server.hooks.modules.ortb2.blocking.core;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Value;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.hooks.modules.ortb2.blocking.core.exception.InvalidAccountConfigurationException;
import org.prebid.server.hooks.modules.ortb2.blocking.core.model.AnalyticsResult;
import org.prebid.server.hooks.modules.ortb2.blocking.core.model.BidAttributeBlockingConfig;
import org.prebid.server.hooks.modules.ortb2.blocking.core.model.BlockedAttributes;
import org.prebid.server.hooks.modules.ortb2.blocking.core.model.BlockedBids;
import org.prebid.server.hooks.modules.ortb2.blocking.core.model.ExecutionResult;
import org.prebid.server.hooks.modules.ortb2.blocking.core.model.ResponseBlockingConfig;
import org.prebid.server.hooks.modules.ortb2.blocking.core.model.Result;
import org.prebid.server.hooks.modules.ortb2.blocking.core.util.MergeUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class BidsBlocker {

    private static final String SUCCESS_BLOCKED_STATUS = "success-blocked";
    private static final String SUCCESS_ALLOW_STATUS = "success-allow";
    private static final String ATTRIBUTES_FIELD = "attributes";
    private static final String ADOMAIN_FIELD = "adomain";
    private static final String BCAT_FIELD = "bcat";
    private static final String BUNDLE_FIELD = "bundle";
    private static final String ATTR_FIELD = "attr";

    private final List<BidderBid> bids;
    private final String bidder;
    private final ObjectNode accountConfig;
    private final BlockedAttributes blockedAttributes;
    private final boolean debugEnabled;

    private BidsBlocker(
        List<BidderBid> bids,
        String bidder,
        ObjectNode accountConfig,
        BlockedAttributes blockedAttributes,
        boolean debugEnabled) {

        this.bids = bids;
        this.bidder = bidder;
        this.accountConfig = accountConfig;
        this.blockedAttributes = blockedAttributes;
        this.debugEnabled = debugEnabled;
    }

    public static BidsBlocker create(
        List<BidderBid> bids,
        String bidder,
        ObjectNode accountConfig,
        BlockedAttributes blockedAttributes,
        boolean debugEnabled) {

        return new BidsBlocker(
            Objects.requireNonNull(bids),
            Objects.requireNonNull(bidder),
            accountConfig,
            blockedAttributes,
            debugEnabled);
    }

    public ExecutionResult<BlockedBids> block() {
        final AccountConfigReader accountConfigReader = AccountConfigReader.create(accountConfig, bidder, debugEnabled);

        try {
            final List<Result<BlockingResult>> blockedBidResults = bids.stream()
                .sequential()
                .map(bid -> isBlocked(bid, accountConfigReader))
                .collect(Collectors.toList());

            final Set<Integer> blockedBidIndexes = IntStream.range(0, bids.size())
                .filter(index -> blockedBidResults.get(index).getValue().isBlocked())
                .boxed()
                .collect(Collectors.toSet());

            final BlockedBids blockedBids = !blockedBidIndexes.isEmpty() ? BlockedBids.of(blockedBidIndexes) : null;
            final List<String> warnings = MergeUtils.mergeMessages(blockedBidResults);

            return ExecutionResult.<BlockedBids>builder()
                .value(blockedBids)
                .debugMessages(blockedBids != null ? debugMessages(blockedBidIndexes, blockedBidResults) : null)
                .warnings(warnings)
                .analyticsResults(toAnalyticsResults(blockedBidResults))
                .build();
        } catch (InvalidAccountConfigurationException e) {
            return debugEnabled ? ExecutionResult.withError(e.getMessage()) : ExecutionResult.empty();
        }
    }

    private Result<BlockingResult> isBlocked(BidderBid bidderBid, AccountConfigReader accountConfigReader) {
        final Result<ResponseBlockingConfig> blockingConfigResult =
            accountConfigReader.responseBlockingConfigFor(bidderBid);
        final ResponseBlockingConfig blockingConfig = blockingConfigResult.getValue();

        final BlockingResult blockingResult = BlockingResult.of(
            bidderBid.getBid().getImpid(),
            checkBadv(bidderBid, blockingConfig),
            checkBcat(bidderBid, blockingConfig),
            checkBapp(bidderBid, blockingConfig),
            checkBattr(bidderBid, blockingConfig));

        return Result.of(blockingResult, blockingConfigResult.getMessages());
    }

    private AttributeCheckResult<String> checkBadv(BidderBid bidderBid, ResponseBlockingConfig blockingConfig) {
        return checkAttribute(
            bidderBid.getBid().getAdomain(),
            blockingConfig.getBadv(),
            blockedAttributeValues(BlockedAttributes::getBadv));
    }

    private AttributeCheckResult<String> checkBcat(BidderBid bidderBid, ResponseBlockingConfig blockingConfig) {
        return checkAttribute(
            bidderBid.getBid().getCat(),
            blockingConfig.getBcat(),
            blockedAttributeValues(BlockedAttributes::getBcat));
    }

    private AttributeCheckResult<String> checkBapp(BidderBid bidderBid, ResponseBlockingConfig blockingConfig) {
        return checkAttribute(
            bidderBid.getBid().getBundle(),
            blockingConfig.getBapp(),
            blockedAttributeValues(BlockedAttributes::getBapp));
    }

    private AttributeCheckResult<Integer> checkBattr(
        BidderBid bidderBid, ResponseBlockingConfig blockingConfig) {

        return checkAttribute(
            bidderBid.getBid().getAttr(),
            blockingConfig.getBattr(),
            blockedAttributeValues(BlockedAttributes::getBattr, bidderBid.getBid().getImpid()));
    }

    private <T> AttributeCheckResult<T> checkAttribute(
        List<T> attribute, BidAttributeBlockingConfig<T> blockingConfig, List<T> blockedAttributeValues) {

        if (blockingConfig == null || !blockingConfig.isEnforceBlocks()) {
            return AttributeCheckResult.succeeded();
        }

        if (CollectionUtils.isEmpty(attribute)) {
            return blockingConfig.isBlockUnknownValues()
                ? AttributeCheckResult.failed()
                : AttributeCheckResult.succeeded();
        }

        if (CollectionUtils.isNotEmpty(blockedAttributeValues)) {
            final List<T> blockedBidValues = attribute.stream()
                .filter(blockedAttributeValues::contains)
                .filter(blockedBidValue -> !blockingConfig.getAllowedValues().contains(blockedBidValue))
                .collect(Collectors.toList());

            return CollectionUtils.isEmpty(blockedBidValues)
                ? AttributeCheckResult.succeeded()
                : AttributeCheckResult.failed(blockedBidValues);
        }

        return AttributeCheckResult.succeeded();
    }

    private AttributeCheckResult<String> checkAttribute(
        String attribute, BidAttributeBlockingConfig<String> blockingConfig, List<String> blockedAttributeValues) {

        if (blockingConfig == null
            || !blockingConfig.isEnforceBlocks()
            || StringUtils.isEmpty(attribute)
            || CollectionUtils.isEmpty(blockedAttributeValues)) {

            return AttributeCheckResult.succeeded();
        }

        final boolean blocked =
            blockedAttributeValues.contains(attribute) && !blockingConfig.getAllowedValues().contains(attribute);

        return blocked
            ? AttributeCheckResult.failed(Collections.singletonList(attribute))
            : AttributeCheckResult.succeeded();
    }

    private <T> T blockedAttributeValues(Function<BlockedAttributes, T> getter) {
        return blockedAttributes != null ? getter.apply(blockedAttributes) : null;
    }

    private <T> T blockedAttributeValues(Function<BlockedAttributes, Map<String, T>> getter, String impId) {
        final Map<String, T> blockedAttributeValues = blockedAttributeValues(getter);

        return blockedAttributeValues != null ? blockedAttributeValues.get(impId) : null;
    }

    private List<String> debugMessages(
        Set<Integer> blockedBidIndexes,
        List<Result<BlockingResult>> blockedBidResults) {

        if (!debugEnabled) {
            return null;
        }

        return blockedBidIndexes.stream()
            .map(index -> debugEntryFor(index, blockedBidResults.get(index).getValue()))
            .collect(Collectors.toList());
    }

    private String debugEntryFor(int index, BlockingResult blockingResult) {
        return String.format(
            "Bid %d from bidder %s has been rejected, failed checks: %s",
            index,
            bidder,
            blockingResult.getFailedChecks());
    }

    private List<AnalyticsResult> toAnalyticsResults(List<Result<BlockingResult>> blockedBidResults) {
        return blockedBidResults.stream()
            .map(Result::getValue)
            .map(blockingResult -> AnalyticsResult.of(
                blockingResult.isBlocked() ? SUCCESS_BLOCKED_STATUS : SUCCESS_ALLOW_STATUS,
                blockingResult.isBlocked() ? toAnalyticsResultValues(blockingResult) : null,
                bidder,
                blockingResult.getImpId()))
            .collect(Collectors.toList());
    }

    private Map<String, Object> toAnalyticsResultValues(BlockingResult blockingResult) {
        final Map<String, Object> values = new HashMap<>();

        values.put(ATTRIBUTES_FIELD, blockingResult.getFailedChecks());

        final AttributeCheckResult<String> badvResult = blockingResult.getBadvCheckResult();
        if (badvResult.isFailed()) {
            values.put(ADOMAIN_FIELD, badvResult.getFailedValues());
        }
        final AttributeCheckResult<String> bcatResult = blockingResult.getBcatCheckResult();
        if (bcatResult.isFailed()) {
            values.put(BCAT_FIELD, bcatResult.getFailedValues());
        }
        final AttributeCheckResult<String> bappResult = blockingResult.getBappCheckResult();
        if (bappResult.isFailed()) {
            values.put(BUNDLE_FIELD, bappResult.getFailedValues().get(0));
        }
        final AttributeCheckResult<Integer> battrResult = blockingResult.getBattrCheckResult();
        if (battrResult.isFailed()) {
            values.put(ATTR_FIELD, battrResult.getFailedValues());
        }

        return values;
    }

    @Value(staticConstructor = "of")
    private static class BlockingResult {

        private static final String BADV_ATTRIBUTE = "badv";
        private static final String BCAT_ATTRIBUTE = "bcat";
        private static final String BAPP_ATTRIBUTE = "bapp";
        private static final String BATTR_ATTRIBUTE = "battr";

        String impId;

        boolean blocked;

        AttributeCheckResult<String> badvCheckResult;

        AttributeCheckResult<String> bcatCheckResult;

        AttributeCheckResult<String> bappCheckResult;

        AttributeCheckResult<Integer> battrCheckResult;

        public static BlockingResult of(
            String impId,
            AttributeCheckResult<String> badvCheckResult,
            AttributeCheckResult<String> bcatCheckResult,
            AttributeCheckResult<String> bappCheckResult,
            AttributeCheckResult<Integer> battrCheckResult) {

            final boolean blocked =
                badvCheckResult.isFailed()
                    || bcatCheckResult.isFailed()
                    || bappCheckResult.isFailed()
                    || battrCheckResult.isFailed();

            return of(
                impId,
                blocked,
                badvCheckResult,
                bcatCheckResult,
                bappCheckResult,
                battrCheckResult);
        }

        public List<String> getFailedChecks() {
            if (!blocked) {
                return null;
            }

            final List<String> failedChecks = new ArrayList<>();
            if (badvCheckResult.isFailed()) {
                failedChecks.add(BADV_ATTRIBUTE);
            }
            if (bcatCheckResult.isFailed()) {
                failedChecks.add(BCAT_ATTRIBUTE);
            }
            if (bappCheckResult.isFailed()) {
                failedChecks.add(BAPP_ATTRIBUTE);
            }
            if (battrCheckResult.isFailed()) {
                failedChecks.add(BATTR_ATTRIBUTE);
            }

            return failedChecks;
        }
    }

    @Value(staticConstructor = "of")
    private static class AttributeCheckResult<T> {

        private static final AttributeCheckResult<?> SUCCEEDED = AttributeCheckResult.of(false, null);
        private static final AttributeCheckResult<?> FAILED = AttributeCheckResult.of(true, Collections.emptyList());

        boolean failed;

        List<T> failedValues;

        @SuppressWarnings("unchecked")
        public static <T> AttributeCheckResult<T> succeeded() {
            return (AttributeCheckResult<T>) SUCCEEDED;
        }

        @SuppressWarnings("unchecked")
        public static <T> AttributeCheckResult<T> failed() {
            return (AttributeCheckResult<T>) FAILED;
        }

        public static <T> AttributeCheckResult<T> failed(List<T> failedValues) {
            return AttributeCheckResult.of(true, failedValues);
        }
    }
}
