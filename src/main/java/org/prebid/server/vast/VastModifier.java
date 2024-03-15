package org.prebid.server.vast;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.cache.proto.request.PutObject;
import org.prebid.server.events.EventsContext;
import org.prebid.server.events.EventsService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VastModifier {

    private static final Pattern WRAPPER_OPEN_TAG_PATTERN =
            Pattern.compile("<\\s*wrapper(>|\\s+.*?>)", Pattern.CASE_INSENSITIVE);
    private static final Pattern WRAPPER_CLOSE_TAG_PATTERN =
            Pattern.compile("<\\s*/\\s*wrapper(>|\\s+.*?>)", Pattern.CASE_INSENSITIVE);
    private static final Pattern INLINE_OPEN_TAG_PATTERN =
            Pattern.compile("<\\s*inline(>|\\s+.*?>)", Pattern.CASE_INSENSITIVE);
    private static final Pattern INLINE_CLOSE_TAG_PATTERN =
            Pattern.compile("<\\s*/\\s*inline(>|\\s+.*?>)", Pattern.CASE_INSENSITIVE);
    private static final Pattern IMPRESSION_CLOSE_TAG_PATTERN =
            Pattern.compile("<\\s*/\\s*impression(>|\\s+.*?>)", Pattern.CASE_INSENSITIVE);

    private final BidderCatalog bidderCatalog;
    private final EventsService eventsService;
    private final Metrics metrics;

    public VastModifier(BidderCatalog bidderCatalog, EventsService eventsService, Metrics metrics) {
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.eventsService = Objects.requireNonNull(eventsService);
        this.metrics = Objects.requireNonNull(metrics);
    }

    public JsonNode modifyVastXml(Boolean isEventsEnabled,
                                  Set<String> allowedBidders,
                                  PutObject putObject,
                                  String accountId,
                                  String integration) {
        final JsonNode value = putObject.getValue();
        final String bidder = putObject.getBidder();
        final boolean isValueValid = value != null && !value.isNull();
        if (BooleanUtils.isTrue(isEventsEnabled) && allowedBidders.contains(bidder) && isValueValid) {
            final EventsContext eventsContext = EventsContext.builder()
                    .auctionId(putObject.getAid())
                    .auctionTimestamp(putObject.getTimestamp())
                    .integration(integration)
                    .build();
            final String vastUrlTracking = eventsService.vastUrlTracking(
                    putObject.getBidid(),
                    bidder,
                    accountId,
                    null,
                    eventsContext);
            try {
                return new TextNode(appendTrackingUrlToVastXml(value.asText(), vastUrlTracking, bidder));
            } catch (PreBidException e) {
                metrics.updateAdapterRequestErrorMetric(bidder, MetricName.badserverresponse);
            }
        }

        return value;
    }

    public String createBidVastXml(String bidder,
                                   String bidAdm,
                                   String bidNurl,
                                   String eventBidId,
                                   String accountId,
                                   EventsContext eventsContext,
                                   List<String> debugWarnings,
                                   String lineItemId) {
        if (!bidderCatalog.isModifyingVastXmlAllowed(bidder)) {
            return bidAdm;
        }

        final String vastXml = resolveVastXmlFrom(bidAdm, bidNurl);
        if (!eventsContext.isEnabledForAccount()) {
            return vastXml;
        }

        final String vastUrl = eventsService.vastUrlTracking(eventBidId, bidder,
                accountId, lineItemId, eventsContext);
        try {
            return appendTrackingUrlToVastXml(vastXml, vastUrl, bidder);
        } catch (PreBidException e) {
            debugWarnings.add(e.getMessage());
            metrics.updateAdapterRequestErrorMetric(bidder, MetricName.badserverresponse);
        }
        return vastXml;
    }

    private static String resolveVastXmlFrom(String bidAdm, String bidNurl) {
        return StringUtils.isEmpty(bidAdm) && bidNurl != null
                ? """
                <VAST version="3.0"><Ad><Wrapper>\
                <AdSystem>prebid.org wrapper</AdSystem>\
                <VASTAdTagURI><![CDATA[%s]]></VASTAdTagURI>\
                <Creatives></Creatives>\
                </Wrapper></Ad></VAST>""".formatted(bidNurl)
                : bidAdm;
    }

    private static String appendTrackingUrlToVastXml(String vastXml, String vastUrlTracking, String bidder) {
        if (INLINE_OPEN_TAG_PATTERN.matcher(vastXml).find()) {
            final Matcher inlineMatcher = INLINE_CLOSE_TAG_PATTERN.matcher(vastXml);
            return appendTrackingUrl(vastXml, vastUrlTracking, inlineMatcher);
        } else if (WRAPPER_OPEN_TAG_PATTERN.matcher(vastXml).find()) {
            final Matcher wrapperMatcher = WRAPPER_CLOSE_TAG_PATTERN.matcher(vastXml);
            return appendTrackingUrl(vastXml, vastUrlTracking, wrapperMatcher);
        }
        throw new PreBidException("VastXml does not contain neither InLine nor Wrapper for %s response"
                .formatted(bidder));
    }

    private static String appendTrackingUrl(String vastXml, String vastUrlTracking, Matcher closeTagMatcher) {
        final Matcher impressionMatcher = IMPRESSION_CLOSE_TAG_PATTERN.matcher(vastXml);
        if (impressionMatcher.find()) {
            return insertAfterExistingImpressionTag(vastXml, vastUrlTracking, impressionMatcher);
        }
        return insertBeforeElementCloseTag(vastXml, vastUrlTracking, closeTagMatcher);
    }

    private static String insertAfterExistingImpressionTag(String vastXml,
                                                           String vastUrlTracking,
                                                           Matcher impressionMatcher) {

        final String impressionTag = "<Impression><![CDATA[" + vastUrlTracking + "]]></Impression>";

        String group = impressionMatcher.group();
        while (impressionMatcher.find()) {
            group = impressionMatcher.group();
        }
        final int replacementStart = vastXml.lastIndexOf(group);

        return vastXml.substring(0, replacementStart + group.length()) + impressionTag
                + vastXml.substring(replacementStart + group.length());
    }

    private static String insertBeforeElementCloseTag(String vastXml,
                                                      String vastUrlTracking,
                                                      Matcher closeTagMatcher) {

        if (!closeTagMatcher.find()) {
            return vastXml;
        }

        final String group = closeTagMatcher.group();
        final int indexOfCloseTag = vastXml.indexOf(group);

        final String caseSpecificCloseTag =
                vastXml.substring(indexOfCloseTag, indexOfCloseTag + group.length());
        final String impressionTag = "<Impression><![CDATA[" + vastUrlTracking + "]]></Impression>";

        return vastXml.replace(caseSpecificCloseTag, impressionTag + caseSpecificCloseTag);
    }
}
