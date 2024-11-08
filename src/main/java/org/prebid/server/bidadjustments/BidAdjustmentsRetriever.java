package org.prebid.server.bidadjustments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iab.openrtb.request.BidRequest;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.bidadjustments.model.BidAdjustments;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.JsonMerger;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestBidAdjustments;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.validation.ValidationException;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class BidAdjustmentsRetriever {

    private static final Logger logger = LoggerFactory.getLogger(BidAdjustmentsRetriever.class);
    private static final ConditionalLogger conditionalLogger = new ConditionalLogger(logger);

    private final ObjectMapper mapper;
    private final JsonMerger jsonMerger;
    private final double samplingRate;

    public BidAdjustmentsRetriever(JacksonMapper mapper,
                                   JsonMerger jsonMerger,
                                   double samplingRate) {
        this.mapper = Objects.requireNonNull(mapper).mapper();
        this.jsonMerger = Objects.requireNonNull(jsonMerger);
        this.samplingRate = samplingRate;
    }

    public BidAdjustments retrieve(AuctionContext auctionContext) {
        final List<String> debugWarnings = auctionContext.getDebugWarnings();
        final boolean debugEnabled = auctionContext.getDebugContext().isDebugEnabled();

        final JsonNode requestBidAdjustmentsNode = Optional.ofNullable(auctionContext.getBidRequest())
                .map(BidRequest::getExt)
                .map(ExtRequest::getPrebid)
                .map(ExtRequestPrebid::getBidadjustments)
                .orElseGet(mapper::createObjectNode);

        final JsonNode accountBidAdjustmentsNode = Optional.ofNullable(auctionContext.getAccount())
                .map(Account::getAuction)
                .map(AccountAuctionConfig::getBidAdjustments)
                .orElseGet(mapper::createObjectNode);

        final JsonNode mergedBidAdjustmentsNode = jsonMerger.merge(
                requestBidAdjustmentsNode,
                accountBidAdjustmentsNode);

        try {
            final ExtRequestBidAdjustments mergedBidAdjustments = mapper.convertValue(
                    mergedBidAdjustmentsNode,
                    ExtRequestBidAdjustments.class);

            BidAdjustmentRulesValidator.validate(mergedBidAdjustments);
            return BidAdjustments.of(mergedBidAdjustments);

        } catch (IllegalArgumentException | ValidationException e) {
            final String message = "bid adjustment from request was invalid: " + e.getMessage();
            if (debugEnabled) {
                debugWarnings.add(message);
            }
            conditionalLogger.error(message, samplingRate);
            return retrieveAccountAdjustmentsOnly(accountBidAdjustmentsNode, debugEnabled, debugWarnings);
        }
    }

    private BidAdjustments retrieveAccountAdjustmentsOnly(JsonNode accountBidAdjustmentsNode,
                                                          boolean debugEnabled,
                                                          List<String> debugWarnings) {

        try {
            final ExtRequestBidAdjustments accountBidAdjustments = mapper.convertValue(
                    accountBidAdjustmentsNode,
                    ExtRequestBidAdjustments.class);

            BidAdjustmentRulesValidator.validate(accountBidAdjustments);
            return BidAdjustments.of(accountBidAdjustments);
        } catch (IllegalArgumentException | ValidationException e) {
            final String message = "bid adjustment from account was invalid: " + e.getMessage();
            if (debugEnabled) {
                debugWarnings.add(message);
            }
            conditionalLogger.error(message, samplingRate);
            return BidAdjustments.of(Collections.emptyMap());
        }
    }
}
