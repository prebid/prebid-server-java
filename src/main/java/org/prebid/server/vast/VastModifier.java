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

public class VastModifier {

    private static final String IN_LINE_TAG = "<InLine>";
    private static final String IN_LINE_CLOSE_TAG = "</InLine>";
    private static final String WRAPPER_TAG = "<Wrapper>";
    private static final String WRAPPER_CLOSE_TAG = "</Wrapper>";
    private static final String IMPRESSION_CLOSE_TAG = "</Impression>";
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
                ? "<VAST version=\"3.0\"><Ad><Wrapper>"
                + "<AdSystem>prebid.org wrapper</AdSystem>"
                + "<VASTAdTagURI><![CDATA[" + bidNurl + "]]></VASTAdTagURI>"
                + "<Creatives></Creatives>"
                + "</Wrapper></Ad></VAST>"
                : bidAdm;
    }

    private String appendTrackingUrlToVastXml(String vastXml, String vastUrlTracking, String bidder) {
        final int inLineTagIndex = StringUtils.indexOfIgnoreCase(vastXml, IN_LINE_TAG);
        final int wrapperTagIndex = StringUtils.indexOfIgnoreCase(vastXml, WRAPPER_TAG);

        if (inLineTagIndex != -1) {
            return appendTrackingUrl(vastXml, vastUrlTracking, IN_LINE_CLOSE_TAG);
        } else if (wrapperTagIndex != -1) {
            return appendTrackingUrl(vastXml, vastUrlTracking, WRAPPER_CLOSE_TAG);
        }
        throw new PreBidException(
                String.format("VastXml does not contain neither InLine nor Wrapper for %s response", bidder));
    }

    private static String appendTrackingUrl(String vastXml, String vastUrlTracking, String elementCloseTag) {
        if (vastXml.contains(IMPRESSION_CLOSE_TAG)) {
            return insertAfterExistingImpressionTag(vastXml, vastUrlTracking);
        }
        return insertBeforeElementCloseTag(vastXml, vastUrlTracking, elementCloseTag);
    }

    private static String insertAfterExistingImpressionTag(String vastXml, String vastUrlTracking) {
        final String impressionTag = "<Impression><![CDATA[" + vastUrlTracking + "]]></Impression>";
        final int replacementStart = vastXml.lastIndexOf(IMPRESSION_CLOSE_TAG);

        return new StringBuilder().append(vastXml, 0, replacementStart)
                .append(IMPRESSION_CLOSE_TAG)
                .append(impressionTag)
                .append(vastXml.substring(replacementStart + IMPRESSION_CLOSE_TAG.length()))
                .toString();
    }

    private static String insertBeforeElementCloseTag(String vastXml, String vastUrlTracking, String elementCloseTag) {
        final int indexOfCloseTag = StringUtils.indexOfIgnoreCase(vastXml, elementCloseTag);

        if (indexOfCloseTag == -1) {
            return vastXml;
        }

        final String caseSpecificCloseTag =
                vastXml.substring(indexOfCloseTag, indexOfCloseTag + elementCloseTag.length());
        final String impressionTag = "<Impression><![CDATA[" + vastUrlTracking + "]]></Impression>";

        return vastXml.replace(caseSpecificCloseTag, impressionTag + caseSpecificCloseTag);
    }
}
