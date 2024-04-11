package org.prebid.server.vast;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
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
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

public class VastModifierTest {

    private static final String AUCTION_ID = "auctionId";
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
    @Mock
    private Metrics metrics;

    private VastModifier target;

    @Before
    public void setUp() {
        given(eventsService.vastUrlTracking(any(), any(), any(), any()))
                .willReturn(VAST_URL_TRACKING);

        given(bidderCatalog.isModifyingVastXmlAllowed(any())).willReturn(true);

        target = new VastModifier(bidderCatalog, eventsService, metrics);
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

        verify(eventsService).vastUrlTracking(any(), any(), any(), any());

        assertThat(result).isEqualTo(vastWithoutImpression);
    }

    @Test
    public void modifyVastXmlShouldModifyVastAndAppendUrl() {
        // given
        final JsonNode result = target.modifyVastXml(true, singleton(BIDDER), putObject(), ACCOUNT_ID,
                INTEGRATION);

        // then
        final String modifiedVast =
                """
                        <VAST version="3.0"><Ad><Wrapper><AdSystem>prebid.org wrapper</AdSystem>\
                        <VASTAdTagURI><![CDATA[adm2]]></VASTAdTagURI><Impression></Impression>\
                        <Impression><![CDATA[http://external-url/event]]></Impression><Creatives>\
                        </Creatives></Wrapper></Ad></VAST>\
                        """;

        assertThat(result).isEqualTo(new TextNode(modifiedVast));
    }

    @Test
    public void createBidVastXmlShouldNotModifyWhenBidderNotAllowed() {
        // given
        given(bidderCatalog.isModifyingVastXmlAllowed(any())).willReturn(false);

        // when
        final String result = target
                .createBidVastXml(BIDDER, adm(), BID_NURL, BID_ID, ACCOUNT_ID, eventsContext(), emptyList());

        // then
        assertThat(result).isEqualTo(adm());
    }

    @Test
    public void createBidVastXmlShouldInjectBidNurlWhenBidAdmIsNullAndEventsDisabledByAccount() {
        // when
        final String result = target
                .createBidVastXml(BIDDER, null, BID_NURL, BID_ID, ACCOUNT_ID, givenEventsContext(false), emptyList());

        // then
        assertThat(result).isEqualTo(modifiedAdm(BID_NURL));
    }

    @Test
    public void createBidVastXmlShouldInjectBidNurlWhenBidAdmIsEmptyAndEventsDisabledByAccount() {
        // when
        final String result = target
                .createBidVastXml(BIDDER, "", BID_NURL, BID_ID, ACCOUNT_ID, givenEventsContext(false), emptyList());

        // then
        assertThat(result).isEqualTo(modifiedAdm(BID_NURL));
    }

    @Test
    public void createBidVastXmlShouldReturnAdmWhenBidAdmIsPresentAndEventsDisabledByAccount() {
        // when
        final String result = target
                .createBidVastXml(BIDDER, adm(), BID_NURL, BID_ID, ACCOUNT_ID, givenEventsContext(false), emptyList());

        // then
        assertThat(result).isEqualTo(adm());
    }

    @Test
    public void createBidVastXmlShouldBeModifiedWithNewImpressionVastUrlWhenEventsEnabledAndNoEmptyTag() {
        // when
        final String bidAdm = "<Wrapper><Impression>http:/test.com</Impression></Wrapper>";
        final String result = target
                .createBidVastXml(BIDDER, bidAdm, BID_NURL, BID_ID, ACCOUNT_ID, eventsContext(), emptyList());

        // then
        verify(eventsService).vastUrlTracking(BID_ID, BIDDER, ACCOUNT_ID, eventsContext());

        assertThat(result).isEqualTo("<Wrapper><Impression>http:/test.com</Impression>"
                + "<Impression><![CDATA[" + VAST_URL_TRACKING + "]]></Impression></Wrapper>");
    }

    @Test
    public void createBidVastXmlShouldBeModifiedWithNewImpressionVastUrlWhenEventsEnabledAndNoEmptyTag2() {
        // when
        final String bidAdm = "<Wrapper><  impreSSion garbage >http:/test.com<  /ImPression  garbage ></Wrapper>";
        final String result = target
                .createBidVastXml(BIDDER, bidAdm, BID_NURL, BID_ID, ACCOUNT_ID, eventsContext(), emptyList());

        // then
        verify(eventsService).vastUrlTracking(BID_ID, BIDDER, ACCOUNT_ID, eventsContext());

        assertThat(result).isEqualTo("<Wrapper><  impreSSion garbage >http:/test.com<  /ImPression  garbage >"
                + "<Impression><![CDATA[" + VAST_URL_TRACKING + "]]></Impression></Wrapper>");
    }

    @Test
    public void createBidVastXmlShouldBeModifiedWithNewImpressionAfterExistingImpressionTags() {
        // when
        final String bidAdm = "<InLine><Impression>http:/test.com</Impression>"
                + "<Impression>http:/test2.com</Impression><Creatives></Creatives></InLine>";
        final String result = target
                .createBidVastXml(BIDDER, bidAdm, BID_NURL, BID_ID, ACCOUNT_ID, eventsContext(), emptyList());

        // then
        verify(eventsService).vastUrlTracking(BID_ID, BIDDER, ACCOUNT_ID, eventsContext());

        assertThat(result).isEqualTo("<InLine><Impression>http:/test.com</Impression>"
                + "<Impression>http:/test2.com</Impression>"
                + "<Impression><![CDATA[" + VAST_URL_TRACKING + "]]></Impression><Creatives></Creatives></InLine>");
    }

    @Test
    public void createBidVastXmlShouldBeModifiedWithNewImpressionAfterExistingImpressionTags2() {
        // when
        final String bidAdm = "<InLine>< Impression  >http:/test.com<   /Impression  >"
                + "<ImprEssion garbage>http:/test2.com<  /ImPRession garbage><Creatives></Creatives></InLine>";
        final String result = target
                .createBidVastXml(BIDDER, bidAdm, BID_NURL, BID_ID, ACCOUNT_ID, eventsContext(), emptyList());

        // then
        verify(eventsService).vastUrlTracking(BID_ID, BIDDER, ACCOUNT_ID, eventsContext());

        assertThat(result).isEqualTo("<InLine>< Impression  >http:/test.com<   /Impression  >"
                + "<ImprEssion garbage>http:/test2.com<  /ImPRession garbage>"
                + "<Impression><![CDATA[" + VAST_URL_TRACKING + "]]></Impression><Creatives></Creatives></InLine>");
    }

    @Test
    public void createBidVastXmlShouldInsertImpressionTagForEmptyInLine() {
        // when
        final String bidAdm = "<InLine></InLine>";
        final String result = target
                .createBidVastXml(BIDDER, bidAdm, BID_NURL, BID_ID, ACCOUNT_ID, eventsContext(), emptyList());

        // then
        verify(eventsService).vastUrlTracking(BID_ID, BIDDER, ACCOUNT_ID, eventsContext());

        assertThat(result).isEqualTo("<InLine><Impression><![CDATA[" + VAST_URL_TRACKING + "]]></Impression></InLine>");
    }

    @Test
    public void createBidVastXmlShouldNotInsertImpressionTagForNoInLineCloseTag() {
        // when
        final String bidAdm = "<InLine></SomeTag>";
        final String result = target
                .createBidVastXml(BIDDER, bidAdm, BID_NURL, BID_ID, ACCOUNT_ID, eventsContext(), emptyList());

        // then
        verify(eventsService).vastUrlTracking(BID_ID, BIDDER, ACCOUNT_ID, eventsContext());

        assertThat(result).isEqualTo(bidAdm);
    }

    @Test
    public void createBidVastXmlShouldModifyWrapperTagInCaseInsensitiveMode() {
        // when
        final String bidAdm = "<wrapper><Impression>http:/test.com</Impression></wrapper>";
        final String result = target
                .createBidVastXml(BIDDER, bidAdm, BID_NURL, BID_ID, ACCOUNT_ID, eventsContext(), emptyList());

        // then
        verify(eventsService).vastUrlTracking(BID_ID, BIDDER, ACCOUNT_ID, eventsContext());

        assertThat(result).isEqualTo("<wrapper><Impression>http:/test.com</Impression>"
                + "<Impression><![CDATA[" + VAST_URL_TRACKING + "]]></Impression></wrapper>");
    }

    @Test
    public void createBidVastXmlShouldModifyWrapperTagInCaseInsensitiveMode2() {
        // when
        final String bidAdm = "<  wraPPer garbage><Impression>http:/test.com</Impression><  / wraPPer garbage>";
        final String result = target
                .createBidVastXml(BIDDER, bidAdm, BID_NURL, BID_ID, ACCOUNT_ID, eventsContext(), emptyList());

        // then
        verify(eventsService).vastUrlTracking(BID_ID, BIDDER, ACCOUNT_ID, eventsContext());

        assertThat(result).isEqualTo("<  wraPPer garbage><Impression>http:/test.com</Impression>"
                + "<Impression><![CDATA[" + VAST_URL_TRACKING + "]]></Impression><  / wraPPer garbage>");
    }

    @Test
    public void createBidVastXmlShouldInsertImpressionTagForEmptyWrapper() {
        // when
        final String bidAdm = "<wrapper></wrapper>";
        final String result = target.createBidVastXml(
                BIDDER, bidAdm, BID_NURL, BID_ID, ACCOUNT_ID, eventsContext(), emptyList());

        // then
        verify(eventsService).vastUrlTracking(BID_ID, BIDDER, ACCOUNT_ID, eventsContext());

        assertThat(result)
                .isEqualTo("<wrapper><Impression><![CDATA[" + VAST_URL_TRACKING + "]]></Impression></wrapper>");
    }

    @Test
    public void createBidVastXmlShouldInsertImpressionTagForEmptyWrapper2() {
        // when
        final String bidAdm = "<  wraPPer garbage>< / wrapPer  garbage>";
        final String result = target
                .createBidVastXml(BIDDER, bidAdm, BID_NURL,
                        BID_ID, ACCOUNT_ID, eventsContext(), emptyList());

        // then
        verify(eventsService).vastUrlTracking(BID_ID, BIDDER, ACCOUNT_ID, eventsContext());

        assertThat(result)
                .isEqualTo("<  wraPPer garbage><Impression><![CDATA["
                        + VAST_URL_TRACKING + "]]></Impression>< / wrapPer  garbage>");
    }

    @Test
    public void createBidVastXmlShouldNotInsertImpressionTagForNoWrapperCloseTag() {
        // when
        final String bidAdm = "<wrapper><someTag>";
        final String result = target
                .createBidVastXml(BIDDER, bidAdm, BID_NURL, BID_ID, ACCOUNT_ID, eventsContext(), emptyList());

        // then
        verify(eventsService).vastUrlTracking(BID_ID, BIDDER, ACCOUNT_ID, eventsContext());

        assertThat(result).isEqualTo(bidAdm);
    }

    @Test
    public void createBidVastXmlShouldModifyInlineTagInCaseInsensitiveMode() {
        // when
        final String bidAdm = "<Inline><Impression>http:/test.com</Impression></Inline>";
        final String result = target
                .createBidVastXml(BIDDER, bidAdm, BID_NURL, BID_ID, ACCOUNT_ID, eventsContext(), emptyList());

        // then
        verify(eventsService).vastUrlTracking(BID_ID, BIDDER, ACCOUNT_ID, eventsContext());

        assertThat(result).isEqualTo("<Inline><Impression>http:/test.com</Impression>"
                + "<Impression><![CDATA[" + VAST_URL_TRACKING + "]]></Impression></Inline>");
    }

    @Test
    public void createBidVastXmlShouldModifyInlineTagInCaseInsensitiveMode2() {
        // when
        final String bidAdm = "<  InLIne garbage ><Impression>http:/test.com</Impression></  Inline garbage >";
        final String result = target
                .createBidVastXml(BIDDER, bidAdm, BID_NURL, BID_ID, ACCOUNT_ID, eventsContext(), emptyList());

        // then
        verify(eventsService).vastUrlTracking(BID_ID, BIDDER, ACCOUNT_ID, eventsContext());

        assertThat(result).isEqualTo("<  InLIne garbage ><Impression>http:/test.com</Impression>"
                + "<Impression><![CDATA[" + VAST_URL_TRACKING + "]]></Impression></  Inline garbage >");
    }

    @Test
    public void createBidVastXmlShouldBeModifiedIfInLineHasNoImpressionTags() {
        // when
        final String bidAdm = "<InLine></InLine>";
        final String result = target
                .createBidVastXml(BIDDER, bidAdm, BID_NURL, BID_ID, ACCOUNT_ID, eventsContext(), emptyList());

        // then
        verify(eventsService).vastUrlTracking(BID_ID, BIDDER, ACCOUNT_ID, eventsContext());

        assertThat(result).isEqualTo("<InLine><Impression><![CDATA[" + VAST_URL_TRACKING + "]]></Impression></InLine>");
    }

    @Test
    public void createBidVastXmlShouldBeModifiedIfInLineHasNoImpressionTags2() {
        // when
        final String bidAdm = "<  InLIne garbage >< / InLIne garbage >";
        final String result = target
                .createBidVastXml(BIDDER, bidAdm, BID_NURL, BID_ID, ACCOUNT_ID, eventsContext(), emptyList());

        // then
        verify(eventsService).vastUrlTracking(BID_ID, BIDDER, ACCOUNT_ID, eventsContext());

        assertThat(result).isEqualTo("<  InLIne garbage ><Impression><![CDATA["
                + VAST_URL_TRACKING + "]]></Impression>< / InLIne garbage >");
    }

    @Test
    public void createBidVastXmlShouldNotBeModifiedIfNoParentTagsPresent() {
        // when
        final String adm = "<Impression>http:/test.com</Impression>";
        final List<String> warnings = new ArrayList<>();
        final String result = target
                .createBidVastXml(BIDDER, adm, BID_NURL, BID_ID, ACCOUNT_ID, eventsContext(), warnings);

        // then
        verify(eventsService).vastUrlTracking(BID_ID, BIDDER, ACCOUNT_ID, eventsContext());
        assertThat(result).isEqualTo(adm);
        assertThat(warnings).containsExactly("VastXml does not contain neither InLine nor Wrapper for bidder response");
        verify(metrics).updateAdapterRequestErrorMetric(BIDDER, MetricName.badserverresponse);
    }

    @Test
    public void createBidVastXmlShouldNotBeModifiedIfWrapperTagIsInvalid() {
        // when
        final String adm = "<wrappergarbage></wrapper>";
        final List<String> warnings = new ArrayList<>();
        final String result = target
                .createBidVastXml(BIDDER, adm, BID_NURL, BID_ID, ACCOUNT_ID, eventsContext(), warnings);

        // then
        verify(eventsService).vastUrlTracking(BID_ID, BIDDER, ACCOUNT_ID, eventsContext());
        assertThat(result).isEqualTo(adm);
        assertThat(warnings).containsExactly("VastXml does not contain neither InLine nor Wrapper for bidder response");
        verify(metrics).updateAdapterRequestErrorMetric(BIDDER, MetricName.badserverresponse);
    }

    @Test
    public void createBidVastXmlShouldNotBeModifiedIfInlineTagIsInvalid() {
        // when
        final String adm = "<inlinegarbage></inline>";
        final List<String> warnings = new ArrayList<>();
        final String result = target
                .createBidVastXml(BIDDER, adm, BID_NURL, BID_ID, ACCOUNT_ID, eventsContext(), warnings);

        // then
        verify(eventsService).vastUrlTracking(BID_ID, BIDDER, ACCOUNT_ID, eventsContext());
        assertThat(result).isEqualTo(adm);
        assertThat(warnings).containsExactly("VastXml does not contain neither InLine nor Wrapper for bidder response");
        verify(metrics).updateAdapterRequestErrorMetric(BIDDER, MetricName.badserverresponse);
    }

    @Test
    public void createBidVastXmlShouldNotModifyWhenEventsEnabledAndAdmHaveNoImpression() {
        // when
        final String admWithNoImpression = "no impression";
        final String result = target.createBidVastXml(
                BIDDER, admWithNoImpression, BID_NURL, BID_ID, ACCOUNT_ID, eventsContext(), new ArrayList<>());

        // then
        verify(eventsService).vastUrlTracking(BID_ID, BIDDER, ACCOUNT_ID, eventsContext());

        assertThat(result).isEqualTo(admWithNoImpression);
    }

    private static PutObject givenPutObject(TextNode adm) {
        return PutObject.builder()
                .type("xml")
                .bidid("bidId2")
                .bidder(BIDDER)
                .timestamp(1L)
                .value(adm)
                .build();
    }

    private static PutObject putObject() {
        return givenPutObject(nodeAdm());
    }

    private static TextNode nodeAdm() {
        return new TextNode(adm());
    }

    public static String adm() {
        return """
                <VAST version="3.0"><Ad><Wrapper>\
                <AdSystem>prebid.org wrapper</AdSystem><VASTAdTagURI>\
                <![CDATA[adm2]]></VASTAdTagURI><Impression>\
                </Impression><Creatives></Creatives>\
                </Wrapper></Ad></VAST>\
                """;
    }

    private static String modifiedAdm(String bidNurl) {
        return """
                <VAST version="3.0"><Ad><Wrapper>\
                <AdSystem>prebid.org wrapper</AdSystem>\
                <VASTAdTagURI><![CDATA[%s]]></VASTAdTagURI>\
                <Creatives></Creatives>\
                </Wrapper></Ad></VAST>\
                """.formatted(bidNurl);
    }

    private static EventsContext givenEventsContext(boolean accountEnabled) {
        return EventsContext.builder()
                .enabledForAccount(accountEnabled)
                .auctionId(AUCTION_ID)
                .auctionTimestamp(AUCTION_TIMESTAMP)
                .integration(INTEGRATION)
                .build();
    }

    private static EventsContext eventsContext() {
        return givenEventsContext(true);
    }
}
