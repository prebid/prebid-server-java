package org.prebid.server.bidadjustments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.JsonMerger;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.bidadjustments.model.BidAdjustments;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.validation.ValidationException;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class BidAdjustmentsEnricher {

    private static final Logger logger = LoggerFactory.getLogger(BidAdjustmentsEnricher.class);
    private static final ConditionalLogger conditionalLogger = new ConditionalLogger(logger);

    private final ObjectMapper mapper;
    private final JacksonMapper jacksonMapper;
    private final JsonMerger jsonMerger;
    private final double samplingRate;

    public BidAdjustmentsEnricher(JacksonMapper mapper, JsonMerger jsonMerger, double samplingRate) {
        this.jacksonMapper = Objects.requireNonNull(mapper);
        this.mapper = mapper.mapper();
        this.jsonMerger = Objects.requireNonNull(jsonMerger);
        this.samplingRate = samplingRate;
    }

    public BidRequest enrichBidRequest(AuctionContext auctionContext) {
        final BidRequest bidRequest = auctionContext.getBidRequest();
        final List<String> debugWarnings = auctionContext.getDebugWarnings();
        final boolean debugEnabled = auctionContext.getDebugContext().isDebugEnabled();

        final JsonNode requestNode = Optional.ofNullable(bidRequest.getExt())
                .map(ExtRequest::getPrebid)
                .map(ExtRequestPrebid::getBidadjustments)
                .orElseGet(mapper::createObjectNode);

        final JsonNode accountNode = Optional.ofNullable(auctionContext.getAccount())
                .map(Account::getAuction)
                .map(AccountAuctionConfig::getBidAdjustments)
                .orElseGet(mapper::createObjectNode);

        final JsonNode mergedNode = jsonMerger.merge(requestNode, accountNode);

        final List<String> resolvedWarnings = debugEnabled ? debugWarnings : null;
        final JsonNode resolvedBidAdjustments = convertAndValidate(mergedNode, resolvedWarnings, "request")
                .or(() -> convertAndValidate(accountNode, resolvedWarnings, "account"))
                .orElse(null);

        return bidRequest.toBuilder()
                .ext(updateExtRequestWithBidAdjustments(bidRequest, resolvedBidAdjustments))
                .build();
    }

    private Optional<JsonNode> convertAndValidate(JsonNode bidAdjustmentsNode,
                                                  List<String> debugWarnings,
                                                  String errorLocation) {

        if (bidAdjustmentsNode.isEmpty()) {
            return Optional.empty();
        }

        try {
            final BidAdjustments bidAdjustments = mapper.convertValue(bidAdjustmentsNode, BidAdjustments.class);

            BidAdjustmentRulesValidator.validate(bidAdjustments);
            return Optional.of(bidAdjustmentsNode);
        } catch (IllegalArgumentException | ValidationException e) {
            final String message = "bid adjustment from " + errorLocation + " was invalid: " + e.getMessage();
            if (debugWarnings != null) {
                debugWarnings.add(message);
            }
            conditionalLogger.error(message, samplingRate);
            return Optional.empty();
        }
    }

    private ExtRequest updateExtRequestWithBidAdjustments(BidRequest bidRequest, JsonNode bidAdjustments) {
        final ExtRequest extRequest = bidRequest.getExt();
        final ExtRequestPrebid prebid = extRequest != null ? extRequest.getPrebid() : null;
        final ExtRequestPrebid updatedPrebid = (prebid != null ? prebid.toBuilder() : ExtRequestPrebid.builder())
                .bidadjustments((ObjectNode) bidAdjustments)
                .build();

        final ExtRequest updatedExtRequest = ExtRequest.of(updatedPrebid);
        return extRequest == null
                ? updatedExtRequest
                : jacksonMapper.fillExtension(updatedExtRequest, extRequest.getProperties());
    }
}
