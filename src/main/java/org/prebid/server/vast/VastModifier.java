package org.prebid.server.vast;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.response.Bid;
import org.apache.commons.lang3.BooleanUtils;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.cache.proto.request.PutObject;
import org.prebid.server.events.EventsContext;
import org.prebid.server.events.EventsService;

import java.util.Objects;
import java.util.Set;

public class VastModifier {

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

    public String createBidVastXml(Bid bid, String bidder, String accountId, EventsContext eventsContext) {
        final String bidAdm = bid.getAdm();
        if (!bidderCatalog.isModifyingVastXmlAllowed(bidder)) {
            return bidAdm;
        }

        final String vastXml = resolveVastXmlFrom(bidAdm, bid.getNurl());
        if (!eventsContext.isEnabledForAccount()) {
            return vastXml;
        }

        final Long auctionTimestamp = eventsContext.getAuctionTimestamp();
        final String integration = eventsContext.getIntegration();

        final String bidId = bid.getId();
        final String vastUrl = eventsService.vastUrlTracking(bidId, bidder, accountId, auctionTimestamp, integration);
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
        final String closeTag = "</Impression>";
        final int closeTagIndex = vastXml.indexOf(closeTag);

        // no impression tag - pass it as it is
        if (closeTagIndex == -1) {
            return vastXml;
        }

        final String impressionUrl = "<![CDATA[" + vastUrlTracking + "]]>";
        final String openTag = "<Impression>";

        // empty impression tag - just insert the link
        if (closeTagIndex - vastXml.indexOf(openTag) == openTag.length()) {
            return vastXml.replaceFirst(openTag, openTag + impressionUrl);
        }

        return vastXml.replaceFirst(closeTag, closeTag + openTag + impressionUrl + closeTag);
    }
}
