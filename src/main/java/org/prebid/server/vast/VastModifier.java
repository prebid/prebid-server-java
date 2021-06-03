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
            final String vastUrlTracking = eventsService.vastUrlTracking(
                    putObject.getBidid(),
                    bidder,
                    accountId,
                    putObject.getTimestamp(),
                    integration);
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

        final Long auctionTimestamp = eventsContext.getAuctionTimestamp();
        final String integration = eventsContext.getIntegration();

        final String vastUrl = eventsService.vastUrlTracking(eventBidId, bidder,
                accountId, auctionTimestamp, integration);
        try {
            return appendTrackingUrlToVastXml(vastXml, vastUrl, bidder);
        } catch (PreBidException e) {
            debugWarnings.add(e.getMessage());
            metrics.updateAdapterRequestErrorMetric(bidder, MetricName.badserverresponse);
        }
        return vastXml;
    }

    private static String resolveVastXmlFrom(String bidAdm, String bidNurl) {
        return bidAdm == null && bidNurl != null
                ? "<VAST version=\"3.0\"><Ad><Wrapper>"
                + "<AdSystem>prebid.org wrapper</AdSystem>"
                + "<VASTAdTagURI><![CDATA[" + bidNurl + "]]></VASTAdTagURI>"
                + "<Impression></Impression><Creatives></Creatives>"
                + "</Wrapper></Ad></VAST>"
                : bidAdm;
    }

    private String appendTrackingUrlToVastXml(String vastXml, String vastUrlTracking, String bidder) {
        final int inLineTagIndex = StringUtils.indexOfIgnoreCase(vastXml, IN_LINE_TAG);
        final int wrapperTagIndex = StringUtils.indexOfIgnoreCase(vastXml, WRAPPER_TAG);

        if (inLineTagIndex != -1) {
            return appendTrackingUrlForInlineType(vastXml, vastUrlTracking);
        } else if (wrapperTagIndex != -1) {
            return appendTrackingUrl(vastXml, vastUrlTracking, false);
        }
        throw new PreBidException(
                String.format("VastXml does not contain neither InLine nor Wrapper for %s response", bidder));
    }

    private String appendTrackingUrlForInlineType(String vastXml, String vastUrlTracking) {
        final String closeTag = "</Impression>";
        final int closeTagIndex = vastXml.indexOf(closeTag);

        // no impression tag - pass it as it is
        if (closeTagIndex == -1) {
            return vastXml;
        }

        return appendTrackingUrl(vastXml, vastUrlTracking, true);
    }

    private String appendTrackingUrl(String vastXml, String vastUrlTracking, boolean inline) {
        final String closeTag = inline ? IN_LINE_CLOSE_TAG : WRAPPER_CLOSE_TAG;
        final String impressionTag = "<Impression><![CDATA[" + vastUrlTracking + "]]></Impression>";

        return vastXml.replace(closeTag, impressionTag + closeTag);
    }
}
