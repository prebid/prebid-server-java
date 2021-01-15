package org.prebid.server.auction.model;

import com.iab.openrtb.response.Bid;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.identity.IdGenerator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

public class GeneratedBidIdsTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private IdGenerator idGenerator;

    private BidderResponse bidderResponse1;

    private BidderResponse bidderResponse2;

    @Before
    public void setup() {
        bidderResponse1 = givenBidderResponse("bidder1", asList(
                givenBidderBid("bidId1", "impId1"), givenBidderBid("bidId1", "impId2")));
        bidderResponse2 = givenBidderResponse("bidder2", asList(givenBidderBid("bidId1", "impId1"),
                givenBidderBid("bidId2", "impId2")));

        given(idGenerator.generateId())
                .willReturn("20dc17a4-4030-4c9f-a2b2-0a95a14af7e9", "a09fb35b-9406-4d1e-9ec3-10c5d33dd249",
                        "b90a06ad-cfd9-4e6e-bc44-4cf723ef5677", "8269d3fc-3304-4ecd-8221-4e0304bf11c2");
    }

    @Test
    public void shouldCreateGeneratedBidIdsTestFromBidderResponses() {
        // when
        final GeneratedBidIds generatedBidIds = GeneratedBidIds.of(asList(bidderResponse1, bidderResponse2),
                (ignored1, ignored2) -> idGenerator.generateId());

        // then
        final Map<String, Map<String, String>> expectedResult =
                doubleMap("bidder1",
                        doubleMap("bidId1-impId1", "20dc17a4-4030-4c9f-a2b2-0a95a14af7e9",
                                "bidId1-impId2", "a09fb35b-9406-4d1e-9ec3-10c5d33dd249"),
                        "bidder2",
                        doubleMap("bidId1-impId1", "b90a06ad-cfd9-4e6e-bc44-4cf723ef5677",
                                "bidId2-impId2", "8269d3fc-3304-4ecd-8221-4e0304bf11c2"));
        assertThat(generatedBidIds.getBidderToBidIds())
                .containsAllEntriesOf(expectedResult);
    }

    @Test
    public void getBidderForBidShouldReturnOptionalWithBidderIfExists() {
        // given
        final GeneratedBidIds generatedBidIds = GeneratedBidIds.of(asList(bidderResponse1, bidderResponse2),
                (ignored1, ignored2) -> idGenerator.generateId());

        // when
        final Optional<String> result = generatedBidIds.getBidderForBid("bidId1", "impId2");
        // then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo("bidder1");
    }

    @Test
    public void getBidderForBidShouldReturnEmptyOptionalIfBidderNotExists() {
        // given
        final GeneratedBidIds generatedBidIds = GeneratedBidIds.of(asList(bidderResponse1, bidderResponse2),
                (ignored1, ignored2) -> idGenerator.generateId());

        // when
        final Optional<String> result = generatedBidIds.getBidderForBid("bidId1", "impId3");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void getGeneratedIdShouldReturnGeneratedId() {
        // given
        final GeneratedBidIds generatedBidIds = GeneratedBidIds.of(asList(bidderResponse1, bidderResponse2),
                (ignored1, ignored2) -> idGenerator.generateId());

        // when
        final String result = generatedBidIds.getGeneratedId("bidder1", "bidId1", "impId1");

        // then
        assertThat(result).isEqualTo("20dc17a4-4030-4c9f-a2b2-0a95a14af7e9");
    }

    @Test
    public void getGeneratedIdShouldReturnNullIfBidWithBidIdAndImpIdPairWasNotFound() {
        // given
        final GeneratedBidIds generatedBidIds = GeneratedBidIds.of(asList(bidderResponse1, bidderResponse2),
                (ignored1, ignored2) -> idGenerator.generateId());

        // when
        final String result = generatedBidIds.getGeneratedId("bidder1", "bidId1", "impId3");

        // then
        assertThat(result).isNull();
    }

    @Test
    public void getGeneratedIdShouldReturnNullIfBidderNotFound() {
        // given
        final GeneratedBidIds generatedBidIds = GeneratedBidIds.of(asList(bidderResponse1, bidderResponse2),
                (ignored1, ignored2) -> idGenerator.generateId());

        // when
        final String result = generatedBidIds.getGeneratedId("bidder3", "bidId1", "impId1");

        // then
        assertThat(result).isNull();
    }

    @Test
    public void getGeneratedIdShouldReturnNullIfGeneratedIdCreatedFromEmptyMap() {
        // given
        final GeneratedBidIds generatedBidIds = GeneratedBidIds.empty();

        // when
        final String result = generatedBidIds.getGeneratedId("bidder3", "bidId1", "impId1");

        // then
        assertThat(result).isNull();
    }

    private static BidderBid givenBidderBid(String bidId, String impId) {
        return BidderBid.of(Bid.builder().id(bidId).impid(impId).build(), null, null);
    }

    private static BidderResponse givenBidderResponse(String bidder, List<BidderBid> bidderBids) {
        return BidderResponse.of(bidder, BidderSeatBid.of(bidderBids, null, null), 0);
    }

    private <K, V> Map<K, V> doubleMap(K key1, V value1, K key2, V value2) {
        final Map<K, V> result = new HashMap<>();
        result.put(key1, value1);
        result.put(key2, value2);
        return result;
    }

}
