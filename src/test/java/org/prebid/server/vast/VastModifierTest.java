package org.prebid.server.vast;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.response.Bid;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.cache.proto.request.PutObject;
import org.prebid.server.events.EventsContext;
import org.prebid.server.events.EventsService;

import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

public class VastModifierTest {

    private static final String ACCOUNT_ID = "accountId";
    private static final String BIDDER = "bidder";
    private static final String INTEGRATION = "integration";
    private static final String VAST_URL_TRACKING = "http://external-url/event";
    private static final String BID_ID = "bidId";
    private static final String BID_NURL = "nurl1";
    private static final long AUCTION_TIMESTAMP = 1000L;

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private BidderCatalog bidderCatalog;
    @Mock

    private EventsService eventsService;

    private VastModifier target;

    @Before
    public void setUp() {
        given(eventsService.vastUrlTracking(any(), any(), any(), any(), anyString()))
                .willReturn(VAST_URL_TRACKING);

        given(bidderCatalog.isModifyingVastXmlAllowed(any())).willReturn(true);

        target = new VastModifier(bidderCatalog, eventsService);
    }

    @Test
    public void modifyVastXmlShouldReturnReceivedValueWhenEventsAreNotAllowed() {
        // when
        final JsonNode result = target.modifyVastXml(false, singleton(BIDDER), putObject(), ACCOUNT_ID, INTEGRATION);

        // then
        assertThat(result).isEqualTo(nodeAdm());
    }

    @Test
    public void modifyVastXmlShouldReturnReceivedValueWhenBidderIsNotAllowed() {
        // when
        final JsonNode result = target.modifyVastXml(true, emptySet(), putObject(), ACCOUNT_ID, INTEGRATION);

        // then
        assertThat(result).isEqualTo(nodeAdm());
    }

    @Test
    public void modifyVastXmlShouldReturnReceivedValueWhenValueIsNull() {
        // when
        final JsonNode result = target.modifyVastXml(true, singleton(BIDDER), givenPutObject(null), ACCOUNT_ID,
                INTEGRATION);

        // then
        assertThat(result).isNull();
    }

    @Test
    public void modifyVastXmlShouldNotModifyVastAndAppendUrlWhenValueWithoutImpression() {
        // given
        final TextNode vastWithoutImpression = new TextNode("vast");

        // when
        final JsonNode result = target.modifyVastXml(true, singleton(BIDDER), givenPutObject(vastWithoutImpression),
                ACCOUNT_ID, INTEGRATION);

        verify(eventsService).vastUrlTracking(any(), any(), any(), any(), anyString());

        assertThat(result).isEqualTo(vastWithoutImpression);
    }

    @Test
    public void modifyVastXmlShouldModifyVastAndAppendUrl() {
        // given
        final JsonNode result = target.modifyVastXml(true, singleton(BIDDER), putObject(), ACCOUNT_ID,
                INTEGRATION);

        // then
        final String modifiedVast = "<VAST version=\"3.0\"><Ad><Wrapper><AdSystem>"
                + "prebid.org wrapper</AdSystem><VASTAdTagURI><![CDATA[adm2]]></VASTAdTagURI>"
                + "<Impression><!"
                + "[CDATA[http://external-url/event]]>"
                + "</Impression><Creatives></Creatives></Wrapper></Ad></VAST>";

        assertThat(result).isEqualTo(new TextNode(modifiedVast));
    }

    @Test
    public void createBidVastXmlShouldNotModifyWhenBidderNotAllowed() {
        // given
        given(bidderCatalog.isModifyingVastXmlAllowed(any())).willReturn(false);

        // when
        final String result = target.createBidVastXml(bid(), BIDDER, ACCOUNT_ID, eventsContext());

        // then
        assertThat(result).isEqualTo(adm());
    }

    @Test
    public void createBidVastXmlShouldInjectBidNurlWhenBidAdmIsEmptyAndEventsDisabledByAccount() {
        // when
        final String result = target.createBidVastXml(givenBid(null), BIDDER, ACCOUNT_ID, givenEventsContext(false));

        // then
        assertThat(result).isEqualTo(modifiedAdm(BID_NURL));
    }

    @Test
    public void createBidVastXmlShouldNotModifyWhenBidAdmIsEmptyAndNurlIsNullAndEventsDisabledByAccount() {
        // when
        final String result = target.createBidVastXml(givenBid(null, null), BIDDER, ACCOUNT_ID,
                givenEventsContext(false));

        // then
        assertThat(result).isNull();
    }

    @Test
    public void createBidVastXmlShouldReturnAdmWhenBidAdmIsPresentAndEventsDisabledByAccount() {
        // when
        final String result = target.createBidVastXml(bid(), BIDDER, ACCOUNT_ID, givenEventsContext(false));

        // then
        assertThat(result).isEqualTo(adm());
    }

    @Test
    public void createBidVastXmlShouldBeModifiedWithNewImpressionVastUrlWhenEventsEnabledAndNoEmptyTag() {
        // when
        final Bid bidWithImpression = givenBid("<Impression>http:/test.com</Impression>");
        final String result = target.createBidVastXml(bidWithImpression, BIDDER, ACCOUNT_ID, eventsContext());

        // then
        verify(eventsService).vastUrlTracking(BID_ID, BIDDER, ACCOUNT_ID, AUCTION_TIMESTAMP, INTEGRATION);

        assertThat(result).isEqualTo("<Impression>http:/test.com</Impression>"
                + "<Impression><![CDATA[" + VAST_URL_TRACKING + "]]></Impression>");
    }

    @Test
    public void createBidVastXmlShouldBeInjectedWithImpressionVastUrlWhenEventsEnabledAndAdmEmptyTagPresent() {
        // when
        final Bid bidWithImpression = givenBid("<Impression></Impression>");
        final String result = target.createBidVastXml(bidWithImpression, BIDDER, ACCOUNT_ID, eventsContext());

        // then
        verify(eventsService).vastUrlTracking(BID_ID, BIDDER, ACCOUNT_ID, AUCTION_TIMESTAMP, INTEGRATION);

        assertThat(result).isEqualTo("<Impression><![CDATA[" + VAST_URL_TRACKING + "]]></Impression>");
    }

    @Test
    public void createBidVastXmlShouldNotModifyWhenEventsEnabledAndAdmHaveNoImpression() {
        // when
        final String admWithNoImpression = "no impression";
        final Bid bidWithImpression = givenBid(admWithNoImpression);
        final String result = target.createBidVastXml(bidWithImpression, BIDDER, ACCOUNT_ID, eventsContext());

        // then
        verify(eventsService).vastUrlTracking(BID_ID, BIDDER, ACCOUNT_ID, AUCTION_TIMESTAMP, INTEGRATION);

        assertThat(result).isEqualTo(admWithNoImpression);
    }

    public static PutObject givenPutObject(TextNode adm) {
        return PutObject.builder()
                .type("xml")
                .bidid("bidId2")
                .bidder(BIDDER)
                .timestamp(1L)
                .value(adm)
                .build();
    }

    public static PutObject putObject() {
        return givenPutObject(nodeAdm());
    }

    public static TextNode nodeAdm() {
        return new TextNode(adm());
    }

    public static String adm() {
        return "<VAST version=\"3.0\"><Ad><Wrapper>"
                + "<AdSystem>prebid.org wrapper</AdSystem>"
                + "<VASTAdTagURI><![CDATA[adm2]]></VASTAdTagURI>"
                + "<Impression></Impression><Creatives></Creatives>"
                + "</Wrapper></Ad></VAST>";
    }

    public static String modifiedAdm(String bidNurl) {
        return "<VAST version=\"3.0\"><Ad><Wrapper>"
                + "<AdSystem>prebid.org wrapper</AdSystem>"
                + "<VASTAdTagURI><![CDATA[" + bidNurl + "]]></VASTAdTagURI>"
                + "<Impression></Impression><Creatives></Creatives>"
                + "</Wrapper></Ad></VAST>";
    }

    public static Bid givenBid(String adm, String nurl) {
        return Bid.builder()
                .id(BID_ID)
                .adm(adm)
                .nurl(nurl)
                .build();
    }

    public static Bid givenBid(String adm) {
        return givenBid(adm, BID_NURL);
    }

    public static Bid bid() {
        return givenBid(adm());
    }

    public static EventsContext givenEventsContext(boolean accountEnabled) {
        return EventsContext.builder()
                .enabledForAccount(accountEnabled)
                .auctionTimestamp(AUCTION_TIMESTAMP)
                .integration(INTEGRATION)
                .build();
    }

    public static EventsContext eventsContext() {
        return givenEventsContext(true);
    }
}
