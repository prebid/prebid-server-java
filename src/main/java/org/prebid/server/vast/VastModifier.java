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
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VastModifier {

    private static final Pattern WRAPPER_OPEN_TAG_PATTERN =
            Pattern.compile("<\\s*wrapper(?:>|\\s.*?>)", Pattern.CASE_INSENSITIVE);
    private static final Pattern WRAPPER_CLOSE_TAG_PATTERN =
            Pattern.compile("<\\s*/\\s*wrapper(?:>|\\s.*?>)", Pattern.CASE_INSENSITIVE);
    private static final Pattern INLINE_OPEN_TAG_PATTERN =
            Pattern.compile("<\\s*inline(?:>|\\s.*?>)", Pattern.CASE_INSENSITIVE);
    private static final Pattern INLINE_CLOSE_TAG_PATTERN =
            Pattern.compile("<\\s*/\\s*inline(?:>|\\s.*?>)", Pattern.CASE_INSENSITIVE);
    private static final Pattern IMPRESSION_CLOSE_TAG_PATTERN =
            Pattern.compile("<\\s*/\\s*impression(?:>|\\s.*?>)", Pattern.CASE_INSENSITIVE);

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
                                   List<String> debugWarnings) {

        if (!bidderCatalog.isModifyingVastXmlAllowed(bidder)) {
            return bidAdm;
        }

        final String vastXml = resolveVastXmlFrom(bidAdm, bidNurl);
        if (!eventsContext.isEnabledForAccount()) {
            return vastXml;
        }

        final String vastUrl = eventsService.vastUrlTracking(eventBidId, bidder, accountId, eventsContext);
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

    private static String appendTrackingUrlToVastXml(String xml, String urlTracking, String bidder) {
        return appendTrackingUrl(xml, urlTracking, INLINE_OPEN_TAG_PATTERN, INLINE_CLOSE_TAG_PATTERN)
                .or(() -> appendTrackingUrl(xml, urlTracking, WRAPPER_OPEN_TAG_PATTERN, WRAPPER_CLOSE_TAG_PATTERN))
                .orElseThrow(() -> new PreBidException(
                        "VastXml does not contain neither InLine nor Wrapper for %s response".formatted(bidder)));
    }

    private static Optional<String> appendTrackingUrl(String vastXml,
                                                      String vastUrlTracking,
                                                      Pattern openTagPattern,
                                                      Pattern closeTagPattern) {

        final Matcher openTagMatcher = openTagPattern.matcher(vastXml);
        if (!openTagMatcher.find()) {
            return Optional.empty();
        }

        final Matcher impressionCloseTagMatcher = IMPRESSION_CLOSE_TAG_PATTERN.matcher(vastXml);
        if (impressionCloseTagMatcher.find(openTagMatcher.end())) {
            int replacementEnd = impressionCloseTagMatcher.end();
            while (impressionCloseTagMatcher.find(replacementEnd)) {
                replacementEnd = impressionCloseTagMatcher.end();
            }
            return Optional.of(insertUrlTracking(vastXml, replacementEnd, vastUrlTracking));
        }

        final Matcher closeTagMatcher = closeTagPattern.matcher(vastXml);
        if (!closeTagMatcher.find(openTagMatcher.end())) {
            return Optional.of(vastXml);
        }

        return Optional.of(insertUrlTracking(vastXml, closeTagMatcher.start(), vastUrlTracking));
    }

    private static String insertUrlTracking(String vastXml, int index, String vastUrlTracking) {
        final String impressionTag = "<Impression><![CDATA[" + vastUrlTracking + "]]></Impression>";
        return vastXml.substring(0, index) + impressionTag + vastXml.substring(index);
    }
}
