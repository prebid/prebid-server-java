package org.prebid.server.auction;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidAdservertargetingRule;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidAmp;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidSchain;

import java.util.HashMap;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidAdservertargetingRule.Source.bidrequest;
import static org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidAdservertargetingRule.Source.bidresponse;
import static org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidAdservertargetingRule.Source.xStatic;

public class TargetingKeywordsResolverTest extends VertxTest {

    @Test
    public void shouldResolveStaticKeyword() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .adservertargeting(singletonList(
                                ExtRequestPrebidAdservertargetingRule.of("keyword1", xStatic, "value1")))
                        .build()))
                .build();

        // when
        final Map<String, String> keywords = TargetingKeywordsResolver.create(bidRequest, jacksonMapper)
                .resolve(Bid.builder().build(), null);

        // then
        assertThat(keywords).containsOnly(entry("keyword1", "value1"));
    }

    @Test
    public void shouldTolerateDuplicateStaticKeys() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .adservertargeting(asList(
                                ExtRequestPrebidAdservertargetingRule.of("keyword1", xStatic, "value1duplicate"),
                                ExtRequestPrebidAdservertargetingRule.of("keyword1", xStatic, "value1")))
                        .build()))
                .build();

        // when
        final Map<String, String> keywords = TargetingKeywordsResolver.create(bidRequest, jacksonMapper)
                .resolve(Bid.builder().build(), null);

        // then
        assertThat(keywords).containsOnly(entry("keyword1", "value1"));
    }

    @Test
    public void shouldResolveRequestKeyword() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .adservertargeting(singletonList(
                                ExtRequestPrebidAdservertargetingRule.of(
                                        "keyword2", bidrequest, "ext.prebid.amp.data.attr1")))
                        .amp(ExtRequestPrebidAmp.of(singletonMap("attr1", "value2")))
                        .build()))
                .build();

        // when
        final Map<String, String> keywords = TargetingKeywordsResolver.create(bidRequest, jacksonMapper)
                .resolve(Bid.builder().build(), null);

        // then
        assertThat(keywords).containsOnly(entry("keyword2", "value2"));
    }

    @Test
    public void shouldTolerateDuplicateRequestKeys() {
        // given
        final Map<String, String> ampData = new HashMap<>();
        ampData.put("attr1", "value2duplicate");
        ampData.put("attr2", "value2");

        final BidRequest bidRequest = BidRequest.builder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .adservertargeting(asList(
                                ExtRequestPrebidAdservertargetingRule.of(
                                        "keyword2", bidrequest, "ext.prebid.amp.data.attr1"),
                                ExtRequestPrebidAdservertargetingRule.of(
                                        "keyword2", bidrequest, "ext.prebid.amp.data.attr2")))
                        .amp(ExtRequestPrebidAmp.of(ampData))
                        .build()))
                .build();

        // when
        final Map<String, String> keywords = TargetingKeywordsResolver.create(bidRequest, jacksonMapper)
                .resolve(Bid.builder().build(), null);

        // then
        assertThat(keywords).containsOnly(entry("keyword2", "value2"));
    }

    @Test
    public void shouldConvertRequestKeywordValueToString() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .adservertargeting(singletonList(
                                ExtRequestPrebidAdservertargetingRule.of("keyword2", bidrequest, "ext.prebid.debug")))
                        .debug(1)
                        .build()))
                .build();

        // when
        final Map<String, String> keywords = TargetingKeywordsResolver.create(bidRequest, jacksonMapper)
                .resolve(Bid.builder().build(), null);

        // then
        assertThat(keywords).containsOnly(entry("keyword2", "1"));
    }

    @Test
    public void shouldTolerateRequestPathThatIsNotValue() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .adservertargeting(singletonList(
                                ExtRequestPrebidAdservertargetingRule.of("keyword2", bidrequest, "ext.prebid.schains")))
                        .schains(singletonList(ExtRequestPrebidSchain.of(null, null)))
                        .build()))
                .build();

        // when
        final Map<String, String> keywords = TargetingKeywordsResolver.create(bidRequest, jacksonMapper)
                .resolve(Bid.builder().build(), null);

        // then
        assertThat(keywords).isEmpty();
    }

    @Test
    public void shouldResolveImpRequestKeyword() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .adservertargeting(singletonList(
                                ExtRequestPrebidAdservertargetingRule.of("keyword3", bidrequest, "imp.ext.attr1")))
                        .build()))
                .imp(singletonList(Imp.builder()
                        .id("impId")
                        .ext(mapper.valueToTree(singletonMap("attr1", "value3")))
                        .build()))
                .build();

        // when
        final Map<String, String> keywords = TargetingKeywordsResolver.create(bidRequest, jacksonMapper)
                .resolve(Bid.builder().impid("impId").build(), null);

        // then
        assertThat(keywords).containsOnly(entry("keyword3", "value3"));
    }

    @Test
    public void shouldTolerateDuplicateImpRequestKeys() {
        // given
        final Map<String, String> impExt = new HashMap<>();
        impExt.put("attr1", "value3duplicate");
        impExt.put("attr2", "value3");

        final BidRequest bidRequest = BidRequest.builder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .adservertargeting(asList(
                                ExtRequestPrebidAdservertargetingRule.of("keyword3", bidrequest, "imp.ext.attr1"),
                                ExtRequestPrebidAdservertargetingRule.of("keyword3", bidrequest, "imp.ext.attr2")))
                        .build()))
                .imp(singletonList(Imp.builder()
                        .id("impId")
                        .ext(mapper.valueToTree(impExt))
                        .build()))
                .build();

        // when
        final Map<String, String> keywords = TargetingKeywordsResolver.create(bidRequest, jacksonMapper)
                .resolve(Bid.builder().impid("impId").build(), null);

        // then
        assertThat(keywords).containsOnly(entry("keyword3", "value3"));
    }

    @Test
    public void shouldConvertImpRequestKeywordValueToString() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .adservertargeting(singletonList(
                                ExtRequestPrebidAdservertargetingRule.of("keyword3", bidrequest, "imp.ext.attr1")))
                        .build()))
                .imp(singletonList(Imp.builder()
                        .id("impId")
                        .ext(mapper.valueToTree(singletonMap("attr1", 3)))
                        .build()))
                .build();

        // when
        final Map<String, String> keywords = TargetingKeywordsResolver.create(bidRequest, jacksonMapper)
                .resolve(Bid.builder().impid("impId").build(), null);

        // then
        assertThat(keywords).containsOnly(entry("keyword3", "3"));
    }

    @Test
    public void shouldTolerateImpRequestPathThatIsNotValue() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .adservertargeting(singletonList(
                                ExtRequestPrebidAdservertargetingRule.of("keyword3", bidrequest, "imp.ext.attr1")))
                        .build()))
                .imp(singletonList(Imp.builder()
                        .id("impId")
                        .ext(mapper.valueToTree(singletonMap("attr1", singletonList("value3"))))
                        .build()))
                .build();

        // when
        final Map<String, String> keywords = TargetingKeywordsResolver.create(bidRequest, jacksonMapper)
                .resolve(Bid.builder().impid("impId").build(), null);

        // then
        assertThat(keywords).isEmpty();
    }

    @Test
    public void shouldTolerateMissingImp() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .adservertargeting(singletonList(
                                ExtRequestPrebidAdservertargetingRule.of("keyword3", bidrequest, "imp.ext.attr1")))
                        .build()))
                .imp(singletonList(Imp.builder().id("impId1").build()))
                .build();

        // when
        final Map<String, String> keywords = TargetingKeywordsResolver.create(bidRequest, jacksonMapper)
                .resolve(Bid.builder().impid("impId2").build(), null);

        // then
        assertThat(keywords).isEmpty();
    }

    @Test
    public void shouldResolveResponseKeyword() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .adservertargeting(singletonList(
                                ExtRequestPrebidAdservertargetingRule.of(
                                        "keyword4", bidresponse, "seatbid.bid.ext.attr1")))
                        .build()))
                .build();

        // when
        final Map<String, String> keywords = TargetingKeywordsResolver.create(bidRequest, jacksonMapper)
                .resolve(
                        Bid.builder()
                                .ext(mapper.valueToTree(singletonMap("attr1", "value4")))
                                .build(),
                        null);

        // then
        assertThat(keywords).containsOnly(entry("keyword4", "value4"));
    }

    @Test
    public void shouldTolerateDuplicateResponseKeys() {
        // given
        final Map<String, String> bidExt = new HashMap<>();
        bidExt.put("attr1", "value4duplicate");
        bidExt.put("attr2", "value4");

        final BidRequest bidRequest = BidRequest.builder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .adservertargeting(asList(
                                ExtRequestPrebidAdservertargetingRule.of(
                                        "keyword4", bidresponse, "seatbid.bid.ext.attr1"),
                                ExtRequestPrebidAdservertargetingRule.of(
                                        "keyword4", bidresponse, "seatbid.bid.ext.attr2")))
                        .build()))
                .build();

        // when
        final Map<String, String> keywords = TargetingKeywordsResolver.create(bidRequest, jacksonMapper)
                .resolve(
                        Bid.builder()
                                .ext(mapper.valueToTree(bidExt))
                                .build(),
                        null);

        // then
        assertThat(keywords).containsOnly(entry("keyword4", "value4"));
    }

    @Test
    public void shouldConvertResponseKeywordValueToString() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .adservertargeting(singletonList(
                                ExtRequestPrebidAdservertargetingRule.of(
                                        "keyword4", bidresponse, "seatbid.bid.ext.attr1")))
                        .build()))
                .build();

        // when
        final Map<String, String> keywords = TargetingKeywordsResolver.create(bidRequest, jacksonMapper)
                .resolve(
                        Bid.builder()
                                .ext(mapper.valueToTree(singletonMap("attr1", 4)))
                                .build(),
                        null);

        // then
        assertThat(keywords).containsOnly(entry("keyword4", "4"));
    }

    @Test
    public void shouldTolerateResponsePathThatIsNotValue() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .adservertargeting(singletonList(
                                ExtRequestPrebidAdservertargetingRule.of(
                                        "keyword4", bidresponse, "seatbid.bid.ext.attr1")))
                        .build()))
                .build();

        // when
        final Map<String, String> keywords = TargetingKeywordsResolver.create(bidRequest, jacksonMapper)
                .resolve(
                        Bid.builder()
                                .ext(mapper.valueToTree(singletonMap("attr1", singletonList("value4"))))
                                .build(),
                        null);

        // then
        assertThat(keywords).isEmpty();
    }

    @Test
    public void shouldResolveResponseKeywordWithBidderMacro() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .adservertargeting(singletonList(
                                ExtRequestPrebidAdservertargetingRule.of(
                                        "{{BIDDER}}_keyword5", bidresponse, "seatbid.bid.ext.attr1")))
                        .build()))
                .build();

        // when
        final Map<String, String> keywords = TargetingKeywordsResolver.create(bidRequest, jacksonMapper)
                .resolve(
                        Bid.builder()
                                .ext(mapper.valueToTree(singletonMap("attr1", "value5")))
                                .build(),
                        "bidder");

        // then
        assertThat(keywords).containsOnly(entry("bidder_keyword5", "value5"));
    }

    @Test
    public void shouldResolveAllKindsOfKeywords() {
        // given
        final Map<String, String> bidExt = new HashMap<>();
        bidExt.put("attr1", "value4");
        bidExt.put("attr2", "value5");

        final BidRequest bidRequest = BidRequest.builder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .adservertargeting(asList(
                                ExtRequestPrebidAdservertargetingRule.of("keyword1", xStatic, "value1"),
                                ExtRequestPrebidAdservertargetingRule.of(
                                        "keyword2", bidrequest, "ext.prebid.amp.data.attr1"),
                                ExtRequestPrebidAdservertargetingRule.of(
                                        "keyword3", bidrequest, "imp.ext.attr1"),
                                ExtRequestPrebidAdservertargetingRule.of(
                                        "keyword4", bidresponse, "seatbid.bid.ext.attr1"),
                                ExtRequestPrebidAdservertargetingRule.of(
                                        "{{BIDDER}}_keyword5", bidresponse, "seatbid.bid.ext.attr2")))
                        .amp(ExtRequestPrebidAmp.of(singletonMap("attr1", "value2")))
                        .build()))
                .imp(singletonList(Imp.builder()
                        .id("impId")
                        .ext(mapper.valueToTree(singletonMap("attr1", "value3")))
                        .build()))
                .build();

        // when
        final Map<String, String> keywords = TargetingKeywordsResolver.create(bidRequest, jacksonMapper)
                .resolve(
                        Bid.builder()
                                .impid("impId")
                                .ext(mapper.valueToTree(bidExt))
                                .build(),
                        "bidder");

        // then
        assertThat(keywords).containsOnly(
                entry("keyword1", "value1"),
                entry("keyword2", "value2"),
                entry("keyword3", "value3"),
                entry("keyword4", "value4"),
                entry("bidder_keyword5", "value5"));
    }
}
