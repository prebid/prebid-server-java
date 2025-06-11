package org.prebid.server.bidadjustments;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.debug.DebugContext;
import org.prebid.server.json.JsonMerger;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class BidAdjustmentsEnricherTest extends VertxTest {

    private BidAdjustmentsEnricher target;

    @BeforeEach
    public void before() {
        target = new BidAdjustmentsEnricher(jacksonMapper, new JsonMerger(jacksonMapper), 0.0d);
    }

    @Test
    public void enrichBidRequestShouldReturnEmptyAdjustmentsWhenRequestAndAccountAdjustmentsAreAbsent() {
        // given
        final List<String> debugMessages = new ArrayList<>();

        // when
        final BidRequest actual = target.enrichBidRequest(givenAuctionContext(
                null, null, debugMessages, true));

        // then
        assertThat(actual)
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getBidadjustments)
                .isNull();
        assertThat(debugMessages).isEmpty();
    }

    @Test
    public void enrichBidRequestShouldReturnEmptyBidAdjustmentsWhenRequestIsInvalidAndAccountRequestAreAbsent()
            throws JsonProcessingException {

        // given
        final List<String> debugMessages = new ArrayList<>();
        final String requestAdjustments = """
                {
                  "mediatype": {
                    "banner": {
                      "invalid": {
                        "invalid": [
                          {
                            "adjtype": "invalid",
                            "value": "0.1",
                            "currency": "USD"
                          }
                        ]
                      }
                    }
                  }
                }
                """;

        final ObjectNode givenRequestAdjustments = (ObjectNode) mapper.readTree(requestAdjustments);

        // when
        final BidRequest actual = target.enrichBidRequest(givenAuctionContext(
                givenRequestAdjustments, null, debugMessages, true));

        // then
        assertThat(actual)
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getBidadjustments)
                .isNull();
        assertThat(debugMessages)
                .containsOnly("bid adjustment from request was invalid: the found rule "
                        + "[adjtype=UNKNOWN, value=0.1, currency=USD] in banner.invalid.invalid is invalid");
    }

    @Test
    public void enrichBidRequestShouldReturnRequestBidAdjustmentsWhenAccountRequestAreAbsent()
            throws JsonProcessingException {

        // given
        final List<String> debugMessages = new ArrayList<>();
        final String requestAdjustments = """
                {
                  "mediatype": {
                    "banner": {
                      "*": {
                        "*": [
                          {
                            "adjtype": "cpm",
                            "value": "0.1",
                            "currency": "USD"
                          }
                        ]
                      }
                    }
                  }
                }
                """;

        final ObjectNode givenRequestAdjustments = (ObjectNode) mapper.readTree(requestAdjustments);

        // when
        final BidRequest actual = target.enrichBidRequest(givenAuctionContext(
                givenRequestAdjustments, null, debugMessages, true));

        // then
        final JsonNode expected = givenRule("banner", "*", "*", "cpm", "0.1", "USD");

        assertThat(actual)
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getBidadjustments)
                .isEqualTo(expected);
        assertThat(debugMessages).isEmpty();
    }

    @Test
    public void enrichBidRequestShouldReturnAccountBidAdjustmentsWhenRequestRequestAreAbsent()
            throws JsonProcessingException {

        // given
        final List<String> debugMessages = new ArrayList<>();
        final String requestAdjustments = """
                {
                  "mediatype": {
                    "banner": {
                      "*": {
                        "*": [
                          {
                            "adjtype": "invalid",
                            "value": "0.1",
                            "currency": "USD"
                          }
                        ]
                      }
                    }
                  }
                }
                """;

        final String accountAdjustments = """
                {
                  "mediatype": {
                    "audio": {
                      "bidder": {
                        "*": [
                          {
                            "adjtype": "static",
                            "value": "0.1",
                            "currency": "USD"
                          }
                        ]
                      }
                    }
                  }
                }
                """;

        final ObjectNode givenRequestAdjustments = (ObjectNode) mapper.readTree(requestAdjustments);
        final ObjectNode givenAccountAdjustments = (ObjectNode) mapper.readTree(accountAdjustments);

        // when
        final BidRequest actual = target.enrichBidRequest(givenAuctionContext(
                givenRequestAdjustments, givenAccountAdjustments, debugMessages, true));

        // then
        final JsonNode expected = givenRule("audio", "bidder", "*", "static", "0.1", "USD");

        assertThat(actual)
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getBidadjustments)
                .isEqualTo(expected);
        assertThat(debugMessages)
                .containsOnly("bid adjustment from request was invalid: the found rule "
                        + "[adjtype=UNKNOWN, value=0.1, currency=USD] in banner.*.* is invalid");
    }

    @Test
    public void enrichBidRequestShouldReturnEmptyBidAdjustmentsWhenAccountAndRequestRequestAreInvalid()
            throws JsonProcessingException {

        // given
        final List<String> debugMessages = new ArrayList<>();
        final String requestAdjustments = """
                {
                  "mediatype": {
                    "banner": {
                      "*": {
                        "*": [
                          {
                            "adjtype": "invalid",
                            "value": "0.1",
                            "currency": "USD"
                          }
                        ]
                      }
                    }
                  }
                }
                """;

        final String accountAdjustments = """
                {
                  "mediatype": {
                    "audio": {
                      "bidder": {
                        "*": [
                          {
                            "adjtype": "invalid",
                            "value": "0.1",
                            "currency": "USD"
                          }
                        ]
                      }
                    }
                  }
                }
                """;

        final ObjectNode givenRequestAdjustments = (ObjectNode) mapper.readTree(requestAdjustments);
        final ObjectNode givenAccountAdjustments = (ObjectNode) mapper.readTree(accountAdjustments);

        // when
        final BidRequest actual = target.enrichBidRequest(givenAuctionContext(
                givenRequestAdjustments, givenAccountAdjustments, debugMessages, true));

        // then
        assertThat(actual)
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getBidadjustments)
                .isNull();
        assertThat(debugMessages).containsExactlyInAnyOrder(
                "bid adjustment from request was invalid: the found rule "
                        + "[adjtype=UNKNOWN, value=0.1, currency=USD] in audio.bidder.* is invalid",
                "bid adjustment from account was invalid: the found rule "
                        + "[adjtype=UNKNOWN, value=0.1, currency=USD] in audio.bidder.* is invalid");
    }

    @Test
    public void enrichBidRequestShouldSkipAddingDebugMessagesWhenDebugIsDisabled() throws JsonProcessingException {
        // given
        final List<String> debugMessages = new ArrayList<>();
        final String requestAdjustments = """
                {
                  "mediatype": {
                    "banner": {
                      "*": {
                        "*": [
                          {
                            "adjtype": "invalid",
                            "value": "0.1",
                            "currency": "USD"
                          }
                        ]
                      }
                    }
                  }
                }
                """;

        final String accountAdjustments = """
                {
                  "mediatype": {
                    "audio": {
                      "bidder": {
                        "*": [
                          {
                            "adjtype": "invalid",
                            "value": "0.1",
                            "currency": "USD"
                          }
                        ]
                      }
                    }
                  }
                }
                """;

        final ObjectNode givenRequestAdjustments = (ObjectNode) mapper.readTree(requestAdjustments);
        final ObjectNode givenAccountAdjustments = (ObjectNode) mapper.readTree(accountAdjustments);

        // when
        final BidRequest actual = target.enrichBidRequest(givenAuctionContext(
                givenRequestAdjustments, givenAccountAdjustments, debugMessages, false));

        // then
        assertThat(actual)
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getBidadjustments)
                .isNull();
        assertThat(debugMessages).isEmpty();
    }

    @Test
    public void enrichBidRequestShouldReturnMergedAccountIntoRequestRequest() throws JsonProcessingException {
        // given
        final List<String> debugMessages = new ArrayList<>();
        final String requestAdjustments = """
                {
                  "mediatype": {
                    "banner": {
                      "*": {
                        "*": [
                          {
                            "adjtype": "cpm",
                            "value": "0.1",
                            "currency": "USD"
                          }
                        ]
                      }
                    }
                  }
                }
                """;

        final String accountAdjustments = """
                {
                  "mediatype": {
                    "banner": {
                      "*": {
                        "dealId": [
                          {
                            "adjtype": "cpm",
                            "value": "0.3",
                            "currency": "USD"
                          }
                        ],
                        "*": [
                          {
                            "adjtype": "static",
                            "value": "0.2",
                            "currency": "USD"
                          }
                        ]
                      }
                    }
                  }
                }
                """;

        final ObjectNode givenRequestAdjustments = (ObjectNode) mapper.readTree(requestAdjustments);
        final ObjectNode givenAccountAdjustments = (ObjectNode) mapper.readTree(accountAdjustments);

        // when
        final BidRequest actual = target.enrichBidRequest(givenAuctionContext(
                givenRequestAdjustments, givenAccountAdjustments, debugMessages, true));

        // then
        final JsonNode expected = mapper.valueToTree(
                Map.of("mediatype", Map.of("banner", Map.of("*", Map.of(
                        "*", mapper.createArrayNode().add(mapper.createObjectNode()
                                .put("adjtype", "cpm").put("value", "0.1").put("currency", "USD")),
                        "dealId", mapper.createArrayNode().add(mapper.createObjectNode()
                                .put("adjtype", "cpm").put("value", "0.3").put("currency", "USD")))))));

        assertThat(actual)
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getBidadjustments)
                .isEqualTo(expected);
        assertThat(debugMessages).isEmpty();
    }

    private static AuctionContext givenAuctionContext(ObjectNode requestBidAdjustments,
                                                      ObjectNode accountBidAdjustments,
                                                      List<String> debugWarnings,
                                                      boolean debugEnabled) {

        return AuctionContext.builder()
                .debugContext(DebugContext.of(debugEnabled, false, null))
                .bidRequest(BidRequest.builder()
                        .ext(ExtRequest.of(ExtRequestPrebid.builder().bidadjustments(requestBidAdjustments).build()))
                        .build())
                .account(Account.builder()
                        .auction(AccountAuctionConfig.builder().bidAdjustments(accountBidAdjustments).build())
                        .build())
                .debugWarnings(debugWarnings)
                .build();
    }

    private static JsonNode givenRule(String mediaType,
                                      String bidder,
                                      String dealId,
                                      String adjtype,
                                      String value,
                                      String currency) {

        return mapper.valueToTree(
                Map.of("mediatype", Map.of(mediaType, Map.of(bidder, Map.of(dealId, mapper.createArrayNode()
                        .add(mapper.createObjectNode()
                                .put("adjtype", adjtype)
                                .put("value", value)
                                .put("currency", currency)))))));
    }

}
