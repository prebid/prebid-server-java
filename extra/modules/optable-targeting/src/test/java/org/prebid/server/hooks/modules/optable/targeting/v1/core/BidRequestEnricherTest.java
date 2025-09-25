package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Data;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.Segment;
import com.iab.openrtb.request.Uid;
import com.iab.openrtb.request.User;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.modules.optable.targeting.model.config.OptableTargetingProperties;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.TargetingResult;
import org.prebid.server.hooks.modules.optable.targeting.v1.BaseOptableTest;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;

public class BidRequestEnricherTest extends BaseOptableTest {

    private final OptableTargetingProperties targetingProperties = new OptableTargetingProperties();

    @Test
    public void shouldReturnOriginBidRequestWhenNoTargetingResults() {
        // given
        final AuctionRequestPayload auctionRequestPayload = AuctionRequestPayloadImpl.of(givenBidRequest());

        // when
        final AuctionRequestPayload result = BidRequestEnricher.of(null, targetingProperties)
                .apply(auctionRequestPayload);

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
        final AuctionRequestPayload result = BidRequestEnricher.of(targetingResult, targetingProperties)
                .apply(auctionRequestPayload);

        // then
        assertThat(result.bidRequest()).isNull();
    }

    @Test
    public void shouldReturnEnrichedBidRequestWhenTargetingResultsIsPresent() {
        // given
        final AuctionRequestPayload auctionRequestPayload = AuctionRequestPayloadImpl.of(givenBidRequest());
        final TargetingResult targetingResult = givenTargetingResult();

        // when
        final AuctionRequestPayload result = BidRequestEnricher.of(targetingResult, targetingProperties)
                .apply(auctionRequestPayload);

        // then
        assertThat(result.bidRequest()).isNotNull();
        final User user = result.bidRequest().getUser();
        assertThat(user).isNotNull();
        assertThat(user.getEids().getFirst().getUids().getFirst().getId()).isEqualTo("id");
        assertThat(user.getData().getFirst().getSegment().getFirst().getId()).isEqualTo("id");
    }

    @Test
    public void shouldNotAddEidWhenSourceAlreadyPresent() {
        // given
        final TargetingResult targetingResult = givenTargetingResultWithEids(List.of(
                givenEid("inserter", "source", List.of(givenUid("id2", 3, null)), null)));

        final BidRequest bidRequest = givenBidRequestWithUserEids(List.of(
                givenEid("inserter", "source", List.of(givenUid("id", null, null)), null),
                givenEid("inserter", "source1", List.of(givenUid("id", null, null)), null)));
        final AuctionRequestPayload auctionRequestPayload = AuctionRequestPayloadImpl.of(bidRequest);

        // when
        final AuctionRequestPayload result = BidRequestEnricher.of(targetingResult, targetingProperties)
                .apply(auctionRequestPayload);

        // then
        assertThat(result.bidRequest()).isNotNull();
        final List<Eid> eids = result.bidRequest().getUser().getEids();
        assertThat(eids.size()).isEqualTo(2);
        assertThat(eids).filteredOn(it -> it.getSource().equals("source")).hasSize(1);
    }

    @Test
    public void shouldAddEidWhenSourceIsNotAlreadyPresent() {
        // given
        final TargetingResult targetingResult = givenTargetingResultWithEids(List.of(
                givenEid("inserter", "source3", List.of(givenUid("id2", 3, null)), null)));

        final BidRequest bidRequest = givenBidRequestWithUserEids(List.of(
                givenEid("inserter", "source1", List.of(givenUid("id", null, null)), null),
                givenEid("inserter", "source2", List.of(givenUid("id", null, null)), null)));
        final AuctionRequestPayload auctionRequestPayload = AuctionRequestPayloadImpl.of(bidRequest);

        // when
        final AuctionRequestPayload result = BidRequestEnricher.of(targetingResult, targetingProperties)
                .apply(auctionRequestPayload);

        // then
        assertThat(result.bidRequest()).isNotNull();
        final List<Eid> eids = result.bidRequest().getUser().getEids();
        assertThat(eids.size()).isEqualTo(3);
        assertThat(eids.stream()).extracting(Eid::getSource).containsExactly("source1", "source2", "source3");
    }

    @Test
    public void shouldSkipEidWhenOptableSourceIsAlreadyPresent() {
        // given
        final TargetingResult targetingResult = givenTargetingResultWithEids(List.of(
                givenEid("optable.co", "source2", List.of(givenUid("id2", 3, null)), null)));

        final BidRequest bidRequest = givenBidRequestWithUserEids(List.of(
                givenEid("optable.co", "source1", List.of(givenUid("id", null, null)), null),
                givenEid("optable.co", "source2", List.of(givenUid("id", null, null)), null)));
        final AuctionRequestPayload auctionRequestPayload = AuctionRequestPayloadImpl.of(bidRequest);

        // when
        final AuctionRequestPayload result = BidRequestEnricher.of(targetingResult, targetingProperties)
                .apply(auctionRequestPayload);

        // then
        assertThat(result.bidRequest()).isNotNull();
        final List<Eid> eids = result.bidRequest().getUser().getEids();
        assertThat(eids.size()).isEqualTo(2);
        assertThat(eids.stream()).extracting(Eid::getSource).containsExactly("source1", "source2");
        assertThat(eids)
                .filteredOn(eid -> "source2".equals(eid.getSource()))
                .singleElement()
                .extracting(Eid::getUids, as(InstanceOfAssertFactories.list(Uid.class)))
                .extracting(Uid::getId)
                .containsExactly("id");
    }

    @Test
    public void shouldMergeEidWhenOptableSourceIsAlreadyPresent() {
        // given
        final TargetingResult targetingResult = givenTargetingResultWithEids(List.of(
                givenEid("optable.co", "source2", List.of(givenUid("id2", 3, null)), null)));

        final BidRequest bidRequest = givenBidRequestWithUserEids(List.of(
                givenEid("optable.co", "source1", List.of(givenUid("id", null, null)), null),
                givenEid("optable.co", "source2", List.of(givenUid("id", null, null)), null)));
        final AuctionRequestPayload auctionRequestPayload = AuctionRequestPayloadImpl.of(bidRequest);
        final OptableTargetingProperties properties = new OptableTargetingProperties();
        properties.setOptableInserterEidsMerge(List.of("source2"));

        // when
        final AuctionRequestPayload result = BidRequestEnricher.of(targetingResult, properties)
                .apply(auctionRequestPayload);

        // then
        assertThat(result.bidRequest()).isNotNull();
        final List<Eid> eids = result.bidRequest().getUser().getEids();
        assertThat(eids.size()).isEqualTo(2);
        assertThat(eids.stream()).extracting(Eid::getSource).containsExactly("source1", "source2");
        assertThat(eids)
                .filteredOn(eid -> "source2".equals(eid.getSource()))
                .singleElement()
                .extracting(Eid::getUids, as(InstanceOfAssertFactories.list(Uid.class)))
                .extracting(Uid::getId)
                .contains("id", "id2");
    }

    @Test
    public void shouldReplaceEidWhenOptableSourceIsAlreadyPresent() {
        // given
        final TargetingResult targetingResult = givenTargetingResultWithEids(List.of(
                givenEid("optable.co", "source2", List.of(givenUid("id2", 3, null)), null)));

        final BidRequest bidRequest = givenBidRequestWithUserEids(List.of(
                givenEid("optable.co", "source1", List.of(givenUid("id", null, null)), null),
                givenEid("optable.co", "source2", List.of(givenUid("id", null, null)), null)));
        final AuctionRequestPayload auctionRequestPayload = AuctionRequestPayloadImpl.of(bidRequest);
        final OptableTargetingProperties properties = new OptableTargetingProperties();
        properties.setOptableInserterEidsReplace(List.of("source2"));

        // when
        final AuctionRequestPayload result = BidRequestEnricher.of(targetingResult, properties)
                .apply(auctionRequestPayload);

        // then
        assertThat(result.bidRequest()).isNotNull();
        final List<Eid> eids = result.bidRequest().getUser().getEids();
        assertThat(eids.size()).isEqualTo(2);
        assertThat(eids.stream()).extracting(Eid::getSource).containsExactly("source1", "source2");
        assertThat(eids)
                .filteredOn(eid -> "source2".equals(eid.getSource()))
                .singleElement()
                .extracting(Eid::getUids, as(InstanceOfAssertFactories.list(Uid.class)))
                .extracting(Uid::getId)
                .containsExactly("id2");
    }

    @Test
    public void shouldReplaceEidWhenOptableSourceIsPresentInBothMergeAndReplaceLists() {
        // given
        final TargetingResult targetingResult = givenTargetingResultWithEids(List.of(
                givenEid("optable.co", "source2", List.of(givenUid("id2", 3, null)), null)));

        final BidRequest bidRequest = givenBidRequestWithUserEids(List.of(
                givenEid("optable.co", "source1", List.of(givenUid("id", null, null)), null),
                givenEid("optable.co", "source2", List.of(givenUid("id", null, null)), null)));
        final AuctionRequestPayload auctionRequestPayload = AuctionRequestPayloadImpl.of(bidRequest);
        final OptableTargetingProperties properties = new OptableTargetingProperties();
        properties.setOptableInserterEidsReplace(List.of("source2"));
        properties.setOptableInserterEidsMerge(List.of("source2"));

        // when
        final AuctionRequestPayload result = BidRequestEnricher.of(targetingResult, properties)
                .apply(auctionRequestPayload);

        // then
        assertThat(result.bidRequest()).isNotNull();
        final List<Eid> eids = result.bidRequest().getUser().getEids();
        assertThat(eids.size()).isEqualTo(2);
        assertThat(eids.stream()).extracting(Eid::getSource).containsExactly("source1", "source2");
        assertThat(eids)
                .filteredOn(eid -> "source2".equals(eid.getSource()))
                .singleElement()
                .extracting(Eid::getUids, as(InstanceOfAssertFactories.list(Uid.class)))
                .extracting(Uid::getId)
                .containsExactly("id2");
    }

    @Test
    public void shouldNotMergeOriginEidsWithTheSameSource() {
        // given
        final TargetingResult targetingResult = givenTargetingResultWithEids(List.of(
                givenEid("inserter", "source3", List.of(givenUid("id2", 3, null)), null)));

        final BidRequest bidRequest = givenBidRequestWithUserEids(List.of(
                givenEid("inserter", "source", List.of(givenUid("id", null, null)), null),
                givenEid("inserter", "source", List.of(givenUid("id", null, null)), null)));
        final AuctionRequestPayload auctionRequestPayload = AuctionRequestPayloadImpl.of(bidRequest);

        // when
        final AuctionRequestPayload result = BidRequestEnricher.of(targetingResult, targetingProperties)
                .apply(auctionRequestPayload);

        // then
        assertThat(result.bidRequest()).isNotNull();
        final List<Eid> eids = result.bidRequest().getUser().getEids();
        assertThat(eids.size()).isEqualTo(3);
        assertThat(eids.stream()).extracting(Eid::getSource).containsExactly("source", "source", "source3");
    }

    @Test
    public void shouldApplyOriginEidsWhenTargetingIsEmpty() {
        // given
        final TargetingResult targetingResult = givenTargetingResultWithEids(List.of(
                givenEid("inserter", "source3", List.of(givenUid("id2", 3, null)), null)));

        final BidRequest bidRequest = givenBidRequestWithUserEids(Collections.emptyList());
        final AuctionRequestPayload auctionRequestPayload = AuctionRequestPayloadImpl.of(bidRequest);

        // when
        final AuctionRequestPayload result = BidRequestEnricher.of(targetingResult, targetingProperties)
                .apply(auctionRequestPayload);

        // then
        assertThat(result.bidRequest()).isNotNull();
        final List<Eid> eids = result.bidRequest().getUser().getEids();
        assertThat(eids.size()).isEqualTo(1);
        assertThat(eids).extracting(Eid::getSource).containsExactly("source3");
    }

    @Test
    public void shouldApplyTargetingEidsWhenOriginListIsEmpty() {
        // given
        final TargetingResult targetingResult = givenTargetingResultWithEids(Collections.emptyList());

        final BidRequest bidRequest = givenBidRequestWithUserEids(List.of(
                givenEid("inserter", "source", List.of(givenUid("id", null, null)), null),
                givenEid("inserter", "source1", List.of(givenUid("id", null, null)), null)));
        final AuctionRequestPayload auctionRequestPayload = AuctionRequestPayloadImpl.of(bidRequest);

        // when
        final AuctionRequestPayload result = BidRequestEnricher.of(targetingResult, targetingProperties)
                .apply(auctionRequestPayload);

        // then
        assertThat(result.bidRequest()).isNotNull();
        final List<Eid> eids = result.bidRequest().getUser().getEids();
        assertThat(eids.size()).isEqualTo(2);
        assertThat(eids).extracting(Eid::getSource).containsExactly("source", "source1");
    }

    @Test
    public void shouldNotApplyEidsWhenOriginAndTargetingEidsAreEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequestWithUserEids(Collections.emptyList());
        final AuctionRequestPayload auctionRequestPayload = AuctionRequestPayloadImpl.of(bidRequest);
        final TargetingResult targetingResult = givenTargetingResultWithEids(Collections.emptyList());

        // when
        final AuctionRequestPayload result = BidRequestEnricher.of(targetingResult, targetingProperties)
                .apply(auctionRequestPayload);

        // then
        final List<Eid> eids = result.bidRequest().getUser().getEids();
        assertThat(eids).isEmpty();
    }

    @Test
    public void shouldMergeDataWithTheSameId() {
        // given
        final BidRequest bidRequest = givenBidRequestWithUserData(List.of(
                givenData("id", List.of(givenSegment("id1", "value1"))),
                givenData("id", List.of(givenSegment("id2", "value2")))));
        final AuctionRequestPayload auctionRequestPayload = AuctionRequestPayloadImpl.of(bidRequest);
        final TargetingResult targetingResult = givenTargetingResultWithData(List.of(
                givenData("id", List.of(givenSegment("id3", "value3")))));

        // when
        final AuctionRequestPayload result = BidRequestEnricher.of(targetingResult, targetingProperties)
                .apply(auctionRequestPayload);

        // then
        final List<Data> data = result.bidRequest().getUser().getData();
        assertThat(data.size()).isEqualTo(2);
        assertThat(data).extracting(Data::getId).containsExactly("id", "id");
        assertThat(data).extracting(Data::getSegment).satisfies(segments -> {
            assertThat(segments.getFirst()).extracting(Segment::getId).containsExactly("id1", "id3");
            assertThat(segments.getLast()).extracting(Segment::getId).containsExactly("id2", "id3");
        });
    }

    @Test
    public void shouldMergeDistinctSegmentsWithinTheSameData() {
        // given
        final BidRequest bidRequest = givenBidRequestWithUserData(List.of(
                givenData("id", List.of(givenSegment("id1", "value1"))),
                givenData("id1", List.of(givenSegment("id2", "value2")))));
        final AuctionRequestPayload auctionRequestPayload = AuctionRequestPayloadImpl.of(bidRequest);
        final TargetingResult targetingResult = givenTargetingResultWithData(List.of(
                givenData("id", List.of(givenSegment("id1", "value3"))),
                givenData("id", List.of(givenSegment("id4", "value4")))));

        // when
        final AuctionRequestPayload result = BidRequestEnricher.of(targetingResult, targetingProperties)
                .apply(auctionRequestPayload);

        // then
        final List<Data> data = result.bidRequest().getUser().getData();
        assertThat(data.size()).isEqualTo(2);
        assertThat(data).extracting(Data::getId).containsExactly("id", "id1");
        assertThat(data).extracting(Data::getSegment).satisfies(segments -> {
            assertThat(segments.getFirst()).extracting(Segment::getId).containsExactly("id1", "id4");
            assertThat(segments.getFirst()).filteredOn(it -> it.getId().equals("id1"))
                    .extracting(Segment::getValue).containsExactly("value1");
            assertThat(segments.getLast()).extracting(Segment::getId).containsExactly("id2");
        });
    }

    @Test
    public void shouldAppendDataWithNewId() {
        // given
        final BidRequest bidRequest = givenBidRequestWithUserData(List.of(
                givenData("id", List.of(givenSegment("id1", "value1"))),
                givenData("id", List.of(givenSegment("id2", "value2")))));
        final AuctionRequestPayload auctionRequestPayload = AuctionRequestPayloadImpl.of(bidRequest);
        final TargetingResult targetingResult = givenTargetingResultWithData(List.of(
                givenData("id1", List.of(givenSegment("id3", "value3")))));

        // when
        final AuctionRequestPayload result = BidRequestEnricher.of(targetingResult, targetingProperties)
                .apply(auctionRequestPayload);

        // then
        final List<Data> data = result.bidRequest().getUser().getData();
        assertThat(data.size()).isEqualTo(3);
        assertThat(data).extracting(Data::getId).containsExactly("id", "id", "id1");
        assertThat(data).extracting(Data::getSegment).satisfies(segments -> {
            assertThat(segments.getFirst()).extracting(Segment::getId).containsExactly("id1");
            assertThat(segments.get(1)).extracting(Segment::getId).containsExactly("id2");
            assertThat(segments.getLast()).extracting(Segment::getId).containsExactly("id3");
        });
    }

    @Test
    public void shouldApplyOriginDataWhenTargetingIsEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequestWithUserData(List.of(
                givenData("id", List.of(givenSegment("id1", "value1"))),
                givenData("id", List.of(givenSegment("id2", "value2")))));
        final AuctionRequestPayload auctionRequestPayload = AuctionRequestPayloadImpl.of(bidRequest);
        final TargetingResult targetingResult = givenTargetingResultWithData(Collections.emptyList());

        // when
        final AuctionRequestPayload result = BidRequestEnricher.of(targetingResult, targetingProperties)
                .apply(auctionRequestPayload);

        // then
        final List<Data> data = result.bidRequest().getUser().getData();
        assertThat(data.size()).isEqualTo(2);
        assertThat(data).extracting(Data::getSegment).satisfies(segments -> {
            assertThat(segments.getFirst()).extracting(Segment::getId).containsExactly("id1");
            assertThat(segments.get(1)).extracting(Segment::getId).containsExactly("id2");
        });
    }

    @Test
    public void shouldApplyTargetingDataWhenOriginIsEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequestWithUserData(Collections.emptyList());
        final AuctionRequestPayload auctionRequestPayload = AuctionRequestPayloadImpl.of(bidRequest);
        final TargetingResult targetingResult = givenTargetingResultWithData(List.of(
                givenData("id", List.of(givenSegment("id1", "value1")))));

        // when
        final AuctionRequestPayload result = BidRequestEnricher.of(targetingResult, targetingProperties)
                .apply(auctionRequestPayload);

        // then
        final List<Data> data = result.bidRequest().getUser().getData();
        assertThat(data.size()).isEqualTo(1);
        assertThat(data).flatMap(Data::getSegment).extracting(Segment::getId).containsExactly("id1");
    }

    @Test
    public void shouldApplyNothingWhenOriginAndTargetingDataAreEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequestWithUserData(Collections.emptyList());
        final AuctionRequestPayload auctionRequestPayload = AuctionRequestPayloadImpl.of(bidRequest);
        final TargetingResult targetingResult = givenTargetingResultWithData(Collections.emptyList());

        // when
        final AuctionRequestPayload result = BidRequestEnricher.of(targetingResult, targetingProperties)
                .apply(auctionRequestPayload);

        // then
        final List<Data> data = result.bidRequest().getUser().getData();
        assertThat(data).isEmpty();
    }

    @Test
    public void shouldReturnOriginBidRequestWhenTargetingResultsIsEmpty() {
        // given
        final AuctionRequestPayload auctionRequestPayload = AuctionRequestPayloadImpl.of(givenBidRequest());
        final TargetingResult targetingResult = givenEmptyTargetingResult();

        // when
        final AuctionRequestPayload result = BidRequestEnricher.of(targetingResult, targetingProperties)
                .apply(auctionRequestPayload);

        // then
        assertThat(result.bidRequest()).isNotNull();
        final User user = result.bidRequest().getUser();
        assertThat(user).isNotNull();
        assertThat(user.getEids()).isNull();
        assertThat(user.getData()).isNull();
    }

    private Eid givenEid(String inserter, String source, List<Uid> uids, ObjectNode ext) {
        return Eid.builder()
                .inserter(inserter)
                .source(source)
                .uids(uids)
                .ext(ext)
                .build();
    }

    private Uid givenUid(String id, Integer atype, ObjectNode ext) {
        return Uid.builder()
                .id(id)
                .atype(atype)
                .ext(ext)
                .build();
    }

    private Data givenData(String id, List<Segment> segments) {
        return Data.builder()
                .id(id)
                .segment(segments)
                .build();
    }

    private Segment givenSegment(String id, String value) {
        return Segment.builder()
                .id(id)
                .value(value)
                .build();
    }
}
