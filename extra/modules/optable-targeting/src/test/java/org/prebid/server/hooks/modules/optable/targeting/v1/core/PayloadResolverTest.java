package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.BidResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.Audience;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.AudienceId;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.TargetingResult;
import org.prebid.server.hooks.modules.optable.targeting.v1.BaseOptableTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class PayloadResolverTest extends BaseOptableTest {

    private ObjectMapper mapper = new ObjectMapper();

    private PayloadResolver target;

    @BeforeEach
    public void setUp() {
        target = new PayloadResolver(mapper);
    }

    @Test
    public void shouldReturnOriginBidRequestWhenNoTargetingResults() {
        // given
        final BidRequest bidRequest = givenBidRequest();

        // when
        final BidRequest result = target.enrichBidRequest(bidRequest, null);

        // then
        assertThat(result).isNotNull();
        final User user = result.getUser();
        assertThat(user).isNotNull();
        assertThat(user.getEids()).isNull();
        assertThat(user.getData()).isNull();
    }

    @Test
    public void shouldNotFailIfBidRequestIsNull() {
        // given
        final BidRequest bidRequest = null;
        final TargetingResult targetingResult = givenTargetingResult();

        // when
        final BidRequest result = target.enrichBidRequest(bidRequest, targetingResult);

        // then
        assertThat(result).isNull();
    }

    @Test
    public void shouldReturnEnrichedBidRequestWhenTargetingResultsIsPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest();
        final TargetingResult targetingResult = givenTargetingResult();

        // when
        final BidRequest result = target.enrichBidRequest(bidRequest, targetingResult);

        // then
        assertThat(result).isNotNull();
        final User user = result.getUser();
        assertThat(user).isNotNull();
        assertThat(user.getEids().getFirst().getUids().getFirst().getId()).isEqualTo("id");
        assertThat(user.getData().getFirst().getSegment().getFirst().getId()).isEqualTo("id");
    }

    @Test
    public void shouldReturnOriginBidRequestWhenTargetingResultsIsEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest();
        final TargetingResult targetingResult = givenEmptyTargetingResult();

        // when
        final BidRequest result = target.enrichBidRequest(bidRequest, targetingResult);

        // then
        assertThat(result).isNotNull();
        final User user = result.getUser();
        assertThat(user).isNotNull();
        assertThat(user.getEids()).isNull();
        assertThat(user.getData()).isNull();
    }

    @Test
    public void shouldEnrichBidResponseByTargetingKeywords() {
        // given
        final BidResponse bidResponse = givenBidResponse();

        // when
        final BidResponse result = target.enrichBidResponse(bidResponse, givenTargeting());
        final ObjectNode targeting = (ObjectNode) bidResponse.getSeatbid()
                .getFirst()
                .getBid()
                .getFirst()
                .getExt()
                .get("prebid")
                .get("targeting");

        // then
        assertThat(result).isNotNull();
        assertThat(targeting.get("keyspace").asText()).isEqualTo("audienceId,audienceId2");
    }

    @Test
    public void shouldReturnOriginBidResponseWhenNoTargetingKeywords() {
        // given
        final BidResponse bidResponse = givenBidResponse();

        // when
        final BidResponse result = target.enrichBidResponse(bidResponse, null);
        final ObjectNode targeting = (ObjectNode) bidResponse.getSeatbid()
                .getFirst()
                .getBid()
                .getFirst()
                .getExt()
                .get("prebid")
                .get("targeting");

        // then
        assertThat(result).isNotNull();
        assertThat(targeting.get("keyspace")).isNull();
    }

    @Test
    public void shouldNotFailWhenResponseIsNull() {
        // given
        final BidResponse bidResponse = givenBidResponse();

        // when
        final BidResponse result = target.enrichBidResponse(null, givenTargeting());
        final ObjectNode targeting = (ObjectNode) bidResponse.getSeatbid()
                .getFirst()
                .getBid()
                .getFirst()
                .getExt()
                .get("prebid")
                .get("targeting");

        // then
        assertThat(result).isNull();
    }

    private List<Audience> givenTargeting() {
        return List.of(new Audience("provider",
                List.of(new AudienceId("audienceId"), new AudienceId("audienceId2")), "keyspace", 1));
    }
}
