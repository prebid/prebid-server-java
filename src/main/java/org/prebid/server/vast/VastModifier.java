package org.prebid.server.vast;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.cache.proto.request.PutObject;
import org.prebid.server.events.EventsContext;
import org.prebid.server.events.EventsService;

import java.util.Objects;
import java.util.Set;

public class VastModifier {

    private static final String IN_LINE_TAG = "<InLine>";
    private static final String WRAPPER_TAG = "<Wrapper>";
    private final BidderCatalog bidderCatalog;
    private final EventsService eventsService;

    public VastModifier(BidderCatalog bidderCatalog, EventsService eventsService) {
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.eventsService = Objects.requireNonNull(eventsService);
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

            final String vastXml = appendTrackingUrlToVastXml(value.asText(), vastUrlTracking);
            return new TextNode(vastXml);
        }

        return value;
    }

    public String createBidVastXml(String bidder,
                                   String bidAdm,
                                   String bidNurl,
                                   String eventBidId,
                                   String accountId,
                                   EventsContext eventsContext) {
        if (!bidderCatalog.isModifyingVastXmlAllowed(bidder)) {
            return bidAdm;
        }

        final String vastXml = resolveVastXmlFrom(bidAdm, bidNurl);
        if (!eventsContext.isEnabledForAccount()) {
            return vastXml;
        }

        final Long auctionTimestamp = eventsContext.getAuctionTimestamp();
        final String integration = eventsContext.getIntegration();

        final String vastUrl = eventsService.vastUrlTracking(eventBidId, bidder, accountId, auctionTimestamp,
                integration);
        return appendTrackingUrlToVastXml(vastXml, vastUrl);
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

    private String appendTrackingUrlToVastXml(String vastXml, String vastUrlTracking) {
        final int inLineTagIndex = StringUtils.indexOfIgnoreCase(vastXml, IN_LINE_TAG);
        final int wrapperTagIndex = StringUtils.indexOfIgnoreCase(vastXml, WRAPPER_TAG);

        if (inLineTagIndex != -1) {
            return appendTrackingUrlForInlineType(vastXml, vastUrlTracking);
        } else if (wrapperTagIndex != -1) {
            return appendTrackingUrlForWrapperType(vastXml, vastUrlTracking, wrapperTagIndex);
        }

        return vastXml;
    }

    private String appendTrackingUrlForInlineType(String vastXml, String vastUrlTracking) {
        final String closeTag = "</Impression>";
        final int closeTagIndex = vastXml.indexOf(closeTag);

        // no impression tag - pass it as it is
        if (closeTagIndex == -1) {
            return vastXml;
        }

        final String impressionTag = "<Impression><![CDATA[" + vastUrlTracking + "]]></Impression>";
        final String inlineCloseTag = IN_LINE_TAG.replace("<", "</");

        return vastXml.replace(inlineCloseTag, impressionTag + inlineCloseTag);
    }

    private String appendTrackingUrlForWrapperType(String vastXml, String vastUrlTracking, Integer wrapperTagIndex) {
        final String impressionTag = "<Impression><![CDATA[" + vastUrlTracking + "]]></Impression>";

        return vastXml.replaceFirst(WRAPPER_TAG, WRAPPER_TAG + impressionTag);
    }
}
