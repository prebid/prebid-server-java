package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.iab.openrtb.request.User;
import org.junit.jupiter.api.Test;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.TargetingResult;
import org.prebid.server.hooks.modules.optable.targeting.v1.BaseOptableTest;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;

import static org.assertj.core.api.Assertions.assertThat;

public class BidRequestEnricherTest extends BaseOptableTest {

    @Test
    public void shouldReturnOriginBidRequestWhenNoTargetingResults() {
        // given
        final AuctionRequestPayload auctionRequestPayload = AuctionRequestPayloadImpl.of(givenBidRequest());

        // when
        final AuctionRequestPayload result = BidRequestEnricher.of(null).apply(auctionRequestPayload);

        // then
        assertThat(result).isNotNull();
        final User user = result.bidRequest().getUser();
        assertThat(user).isNotNull();
        assertThat(user.getEids()).isNull();
        assertThat(user.getData()).isNull();
    }

    @Test
    public void shouldNotFailIfBidRequestIsNull() {
        // given
        final AuctionRequestPayload auctionRequestPayload = AuctionRequestPayloadImpl.of(null);
        final TargetingResult targetingResult = givenTargetingResult();

        // when
        final AuctionRequestPayload result = BidRequestEnricher.of(targetingResult).apply(auctionRequestPayload);

        // then
        assertThat(result.bidRequest()).isNull();
    }

    @Test
    public void shouldReturnEnrichedBidRequestWhenTargetingResultsIsPresent() {
        // given
        final AuctionRequestPayload auctionRequestPayload = AuctionRequestPayloadImpl.of(givenBidRequest());
        final TargetingResult targetingResult = givenTargetingResult();

        // when
        final AuctionRequestPayload result = BidRequestEnricher.of(targetingResult).apply(auctionRequestPayload);

        // then
        assertThat(result.bidRequest()).isNotNull();
        final User user = result.bidRequest().getUser();
        assertThat(user).isNotNull();
        assertThat(user.getEids().getFirst().getUids().getFirst().getId()).isEqualTo("id");
        assertThat(user.getData().getFirst().getSegment().getFirst().getId()).isEqualTo("id");
    }

    @Test
    public void shouldReturnOriginBidRequestWhenTargetingResultsIsEmpty() {
        // given
        final AuctionRequestPayload auctionRequestPayload = AuctionRequestPayloadImpl.of(givenBidRequest());
        final TargetingResult targetingResult = givenEmptyTargetingResult();

        // when
        final AuctionRequestPayload result = BidRequestEnricher.of(targetingResult).apply(auctionRequestPayload);

        // then
        assertThat(result.bidRequest()).isNotNull();
        final User user = result.bidRequest().getUser();
        assertThat(user).isNotNull();
        assertThat(user.getEids()).isNull();
        assertThat(user.getData()).isNull();
    }
}
