package org.prebid.server.bidadjustments;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidadjustments.model.BidAdjustmentsRule;
import org.prebid.server.bidadjustments.model.BidAdjustmentsRules;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ImpMediaType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.prebid.server.bidadjustments.model.BidAdjustmentType.CPM;
import static org.prebid.server.bidadjustments.model.BidAdjustmentType.MULTIPLIER;
import static org.prebid.server.bidadjustments.model.BidAdjustmentType.STATIC;

public class BidAdjustmentsRulesResolverTest extends VertxTest {

    private final BidAdjustmentsRulesResolver target = new BidAdjustmentsRulesResolver(jacksonMapper);

    @Test
    public void resolveShouldPickAndApplyRulesBySpecificMediaType() throws JsonProcessingException {
        // given
        final String givenAdjustments = """
                {
                   "mediatype": {
                     "banner": {
                       "*": {
                         "*": [
                           {
                             "adjtype": "static",
                             "value": "15",
                             "currency": "EUR"
                           }
                         ]
                       }
                     },
                     "*": {
                       "*": {
                         "*": [
                           {
                             "adjtype": "static",
                             "value": "25",
                             "currency": "UAH"
                           }
                         ]
                       }
                     }
                   }
                 }
                """;

        // when
        final List<BidAdjustmentsRule> actual = target.resolve(
                givenBidRequest(givenAdjustments),
                ImpMediaType.banner,
                "bidderName",
                "dealId");

        // then
        assertThat(actual).containsExactly(expectedStatic("15", "EUR"));
    }

    @Test
    public void resolveShouldPickAndApplyRulesByWildcardMediaType() throws JsonProcessingException {
        // given
        final String givenAdjustments = """
                {
                  "mediatype": {
                    "banner": {
                      "*": {
                        "*": [
                          {
                            "adjtype": "cpm",
                            "value": "15",
                            "currency": "EUR"
                          }
                        ]
                      }
                    },
                    "*": {
                      "*": {
                        "*": [
                          {
                            "adjtype": "cpm",
                            "value": "25",
                            "currency": "UAH"
                          }
                        ]
                      }
                    }
                  }
                }
                """;

        // when
        final List<BidAdjustmentsRule> actual = target.resolve(
                givenBidRequest(givenAdjustments),
                ImpMediaType.video_outstream,
                "bidderName",
                "dealId");

        // then
        assertThat(actual).containsExactly(expectedCpm("25", "UAH"));
    }

    @Test
    public void resolveShouldPickAndApplyRulesBySpecificBidder() throws JsonProcessingException {
        // given
        final BidAdjustmentsRules givenBidAdjustments = BidAdjustmentsRules.of(Map.of(
                "*|bidderName|*", List.of(expectedMultiplier("15")),
                "*|*|*", List.of(expectedMultiplier("25"))));

        final String givenAdjustments = """
                {
                  "mediatype": {
                    "*": {
                      "bidderName": {
                        "*": [
                          {
                            "adjtype": "multiplier",
                            "value": "15"
                          }
                        ]
                      },
                      "*": {
                        "*": [
                          {
                            "adjtype": "multiplier",
                            "value": "25"
                          }
                        ]
                      }
                    }
                  }
                }
                """;

        // when
        final List<BidAdjustmentsRule> actual = target.resolve(
                givenBidRequest(givenAdjustments),
                ImpMediaType.banner,
                "bidderName",
                "dealId");

        // then
        assertThat(actual).containsExactly(expectedMultiplier("15"));
    }

    @Test
    public void resolveShouldPickAndApplyRulesByWildcardBidder() throws JsonProcessingException {
        // given
        final String givenAdjustments = """
                {
                  "mediatype": {
                    "*": {
                      "bidderName": {
                        "*": [
                          {
                            "adjtype": "static",
                            "value": "15",
                            "currency": "EUR"
                          },
                          {
                            "adjtype": "multiplier",
                            "value": "15"
                          }
                        ]
                      },
                      "*": {
                        "*": [
                          {
                            "adjtype": "static",
                            "value": "25",
                            "currency": "UAH"
                          },
                          {
                            "adjtype": "multiplier",
                            "value": "25"
                          }
                        ]
                      }
                    }
                  }
                }
                """;

        // when
        final List<BidAdjustmentsRule> actual = target.resolve(
                givenBidRequest(givenAdjustments),
                ImpMediaType.banner,
                "anotherBidderName",
                "dealId");

        // then
        assertThat(actual).containsExactly(expectedStatic("25", "UAH"), expectedMultiplier("25"));
    }

    @Test
    public void resolveShouldPickAndApplyRulesBySpecificDealId() throws JsonProcessingException {
        // given
        final String givenAdjustments = """
                {
                  "mediatype": {
                    "*": {
                      "*": {
                        "dealId": [
                          {
                            "adjtype": "cpm",
                            "value": "15",
                            "currency": "JPY"
                          },
                          {
                            "adjtype": "static",
                            "value": "15",
                            "currency": "EUR"
                          }
                        ],
                        "*": [
                          {
                            "adjtype": "cpm",
                            "value": "25",
                            "currency": "JPY"
                          },
                          {
                            "adjtype": "static",
                            "value": "25",
                            "currency": "UAH"
                          }
                        ]
                      }
                    }
                  }
                }
                """;

        // when
        final List<BidAdjustmentsRule> actual = target.resolve(
                givenBidRequest(givenAdjustments),
                ImpMediaType.banner,
                "bidderName",
                "dealId");

        // then
        assertThat(actual).containsExactly(expectedCpm("15", "JPY"), expectedStatic("15", "EUR"));
    }

    @Test
    public void resolveShouldPickAndApplyRulesByWildcardDealId() throws JsonProcessingException {
        // given
        final String givenAdjustments = """
                {
                  "mediatype": {
                    "*": {
                      "*": {
                        "dealId": [
                          {
                            "adjtype": "multiplier",
                            "value": "15"
                          },
                          {
                            "adjtype": "cpm",
                            "value": "15",
                            "currency": "EUR"
                          }
                        ],
                        "*": [
                          {
                            "adjtype": "multiplier",
                            "value": "25"
                          },
                          {
                            "adjtype": "cpm",
                            "value": "25",
                            "currency": "UAH"
                          }
                        ]
                      }
                    }
                  }
                }
                """;

        // when
        final List<BidAdjustmentsRule> actual = target.resolve(
                givenBidRequest(givenAdjustments),
                ImpMediaType.banner,
                "bidderName",
                "anotherDealId");

        // then
        assertThat(actual).containsExactly(expectedMultiplier("25"), expectedCpm("25", "UAH"));
    }

    @Test
    public void resolveShouldPickAndApplyRulesByWildcardDealIdWhenDealIdIsNull() throws JsonProcessingException {
        // given
        final String givenAdjustments = """
                {
                  "mediatype": {
                    "*": {
                      "*": {
                        "dealId": [
                          {
                            "adjtype": "cpm",
                            "value": "15",
                            "currency": "JPY"
                          },
                          {
                            "adjtype": "static",
                            "value": "15",
                            "currency": "EUR"
                          }
                        ],
                        "*": [
                          {
                            "adjtype": "cpm",
                            "value": "25",
                            "currency": "JPY"
                          },
                          {
                            "adjtype": "static",
                            "value": "25",
                            "currency": "UAH"
                          }
                        ]
                      }
                    }
                  }
                }
                """;

        // when
        final List<BidAdjustmentsRule> actual = target.resolve(
                givenBidRequest(givenAdjustments),
                ImpMediaType.banner,
                "bidderName",
                null);

        // then
        assertThat(actual).containsExactly(expectedCpm("25", "JPY"), expectedStatic("25", "UAH"));
    }

    @Test
    public void resolveShouldReturnEmptyListWhenNoMatchFound() throws JsonProcessingException {
        // given
        final String givenAdjustments = """
                {
                  "mediatype": {
                    "*": {
                      "*": {
                        "dealId": [
                          {
                            "adjtype": "static",
                            "value": "15",
                            "currency": "EUR"
                          }
                        ]
                      }
                    }
                  }
                }
                """;

        // when
        final List<BidAdjustmentsRule> actual = target.resolve(
                givenBidRequest(givenAdjustments),
                ImpMediaType.banner,
                "bidderName",
                null);

        // then
        assertThat(actual).isEmpty();
    }

    private static BidRequest givenBidRequest(String adjustmentsString) throws JsonProcessingException {
        final ObjectNode adjustmetsNode = (ObjectNode) mapper.readTree(adjustmentsString);
        return BidRequest.builder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder().bidadjustments(adjustmetsNode).build()))
                .build();
    }

    private static BidAdjustmentsRule expectedStatic(String value, String currency) {
        return BidAdjustmentsRule.builder()
                .adjType(STATIC)
                .currency(currency)
                .value(new BigDecimal(value))
                .build();
    }

    private static BidAdjustmentsRule expectedCpm(String value, String currency) {
        return BidAdjustmentsRule.builder()
                .adjType(CPM)
                .currency(currency)
                .value(new BigDecimal(value))
                .build();
    }

    private static BidAdjustmentsRule expectedMultiplier(String value) {
        return BidAdjustmentsRule.builder()
                .adjType(MULTIPLIER)
                .value(new BigDecimal(value))
                .build();
    }
}
