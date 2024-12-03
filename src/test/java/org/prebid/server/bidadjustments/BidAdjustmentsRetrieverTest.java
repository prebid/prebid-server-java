package org.prebid.server.bidadjustments;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.debug.DebugContext;
import org.prebid.server.bidadjustments.model.BidAdjustments;
import org.prebid.server.json.JsonMerger;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestBidAdjustmentsRule;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.prebid.server.bidadjustments.model.BidAdjustmentType.CPM;
import static org.prebid.server.bidadjustments.model.BidAdjustmentType.STATIC;

public class BidAdjustmentsRetrieverTest extends VertxTest {

    private BidAdjustmentsRetriever target;

    @BeforeEach
    public void before() {
        target = new BidAdjustmentsRetriever(jacksonMapper, new JsonMerger(jacksonMapper), 0.0d);
    }

    @Test
    public void retrieveShouldReturnEmptyBidAdjustmentsWhenRequestAndAccountAdjustmentsAreAbsent() {
        // given
        final List<String> debugMessages = new ArrayList<>();

        // when
        final BidAdjustments actual = target.retrieve(givenAuctionContext(
                null, null, debugMessages, true));

        // then
        assertThat(actual).isEqualTo(BidAdjustments.of(Collections.emptyMap()));
        assertThat(debugMessages).isEmpty();
    }

    @Test
    public void retrieveShouldReturnEmptyBidAdjustmentsWhenRequestIsInvalidAndAccountAdjustmentsAreAbsent()
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
                            "value": 0.1,
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
        final BidAdjustments actual = target.retrieve(givenAuctionContext(
                givenRequestAdjustments, null, debugMessages, true));

        // then
        assertThat(actual).isEqualTo(BidAdjustments.of(Collections.emptyMap()));
        assertThat(debugMessages)
                .containsOnly("bid adjustment from request was invalid: the found rule "
                        + "[adjtype=UNKNOWN, value=0.1, currency=USD] in banner.invalid.invalid is invalid");
    }

    @Test
    public void retrieveShouldReturnRequestBidAdjustmentsWhenAccountAdjustmentsAreAbsent()
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
                            "value": 0.1,
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
        final BidAdjustments actual = target.retrieve(givenAuctionContext(
                givenRequestAdjustments, null, debugMessages, true));

        // then
        final BidAdjustments expected = BidAdjustments.of(Map.of(
                "banner|*|*",
                List.of(ExtRequestBidAdjustmentsRule.builder()
                        .adjType(CPM)
                        .currency("USD")
                        .value(new BigDecimal("0.1"))
                        .build())));

        assertThat(actual).isEqualTo(expected);
        assertThat(debugMessages).isEmpty();
    }

    @Test
    public void retrieveShouldReturnAccountBidAdjustmentsWhenRequestAdjustmentsAreAbsent()
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
                            "value": 0.1,
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
                            "value": 0.1,
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
        final BidAdjustments actual = target.retrieve(givenAuctionContext(
                givenRequestAdjustments, givenAccountAdjustments, debugMessages, true));

        // then
        final BidAdjustments expected = BidAdjustments.of(Map.of(
                "audio|bidder|*",
                List.of(ExtRequestBidAdjustmentsRule.builder()
                        .adjType(STATIC)
                        .currency("USD")
                        .value(new BigDecimal("0.1"))
                        .build())));

        assertThat(actual).isEqualTo(expected);
        assertThat(debugMessages)
                .containsOnly("bid adjustment from request was invalid: the found rule "
                        + "[adjtype=UNKNOWN, value=0.1, currency=USD] in banner.*.* is invalid");
    }

    @Test
    public void retrieveShouldReturnEmptyBidAdjustmentsWhenAccountAndRequestAdjustmentsAreInvalid()
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
                            "value": 0.1,
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
                            "value": 0.1,
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
        final BidAdjustments actual = target.retrieve(givenAuctionContext(
                givenRequestAdjustments, givenAccountAdjustments, debugMessages, true));

        // then
        assertThat(actual).isEqualTo(BidAdjustments.of(Collections.emptyMap()));
        assertThat(debugMessages).containsExactlyInAnyOrder(
                        "bid adjustment from request was invalid: the found rule "
                                + "[adjtype=UNKNOWN, value=0.1, currency=USD] in audio.bidder.* is invalid",
                        "bid adjustment from account was invalid: the found rule "
                                + "[adjtype=UNKNOWN, value=0.1, currency=USD] in audio.bidder.* is invalid");
    }

    @Test
    public void retrieveShouldSkipAddingDebugMessagesWhenDebugIsDisabled() throws JsonProcessingException {
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
                            "value": 0.1,
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
                            "value": 0.1,
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
        final BidAdjustments actual = target.retrieve(givenAuctionContext(
                givenRequestAdjustments, givenAccountAdjustments, debugMessages, false));

        // then
        assertThat(actual).isEqualTo(BidAdjustments.of(Collections.emptyMap()));
        assertThat(debugMessages).isEmpty();
    }

    @Test
    public void retrieveShouldReturnMergedAccountIntoRequestAdjustments() throws JsonProcessingException {
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
                            "value": 0.1,
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
                            "value": 0.3,
                            "currency": "USD"
                          }
                        ],
                        "*": [
                          {
                            "adjtype": "static",
                            "value": 0.2,
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
        final BidAdjustments actual = target.retrieve(givenAuctionContext(
                givenRequestAdjustments, givenAccountAdjustments, debugMessages, true));

        // then
        final BidAdjustments expected = BidAdjustments.of(Map.of(
                "banner|*|dealId",
                List.of(ExtRequestBidAdjustmentsRule.builder()
                        .adjType(CPM)
                        .currency("USD")
                        .value(new BigDecimal("0.3"))
                        .build()),
                "banner|*|*",
                List.of(ExtRequestBidAdjustmentsRule.builder()
                        .adjType(CPM)
                        .currency("USD")
                        .value(new BigDecimal("0.1"))
                        .build())));

        assertThat(actual).isEqualTo(expected);
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

}
