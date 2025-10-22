package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Data;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.Segment;
import com.iab.openrtb.request.Uid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.modules.optable.targeting.model.config.OptableTargetingProperties;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.Ortb2;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.TargetingResult;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.User;
import org.prebid.server.hooks.v1.PayloadUpdate;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BidRequestEnricher implements PayloadUpdate<AuctionRequestPayload> {

    private static final String OPTABLE_CO_INSERTER = "optable.co";

    private final TargetingResult targetingResult;
    private final OptableTargetingProperties targetingProperties;

    private BidRequestEnricher(TargetingResult targetingResult, OptableTargetingProperties targetingProperties) {
        this.targetingResult = targetingResult;
        this.targetingProperties = targetingProperties;
    }

    public static BidRequestEnricher of(TargetingResult targetingResult, OptableTargetingProperties properties) {
        return new BidRequestEnricher(targetingResult, properties);
    }

    @Override
    public AuctionRequestPayload apply(AuctionRequestPayload payload) {
        return AuctionRequestPayloadImpl.of(enrichBidRequest(payload.bidRequest()));
    }

    private BidRequest enrichBidRequest(BidRequest bidRequest) {
        if (bidRequest == null || targetingResult == null) {
            return bidRequest;
        }

        final User optableUser = Optional.of(targetingResult)
                .map(TargetingResult::getOrtb2)
                .map(Ortb2::getUser)
                .orElse(null);

        if (optableUser == null) {
            return bidRequest;
        }

        final com.iab.openrtb.request.User bidRequestUser = Optional.ofNullable(bidRequest.getUser())
                .orElseGet(() -> com.iab.openrtb.request.User.builder().build());

        return bidRequest.toBuilder()
                .user(mergeUserData(bidRequestUser, optableUser))
                .build();
    }

    private com.iab.openrtb.request.User mergeUserData(com.iab.openrtb.request.User user, User optableUser) {
        return user.toBuilder()
                .eids(filterOptableEids(mergeEids(user.getEids(), optableUser.getEids())))
                .data(mergeData(user.getData(), optableUser.getData()))
                .build();
    }

    private List<Eid> mergeEids(List<Eid> destination, List<Eid> source) {
        if (CollectionUtils.isEmpty(destination)) {
            return source;
        }

        if (CollectionUtils.isEmpty(source)) {
            return destination;
        }

        final Map<String, Eid> idToSourceEid = source.stream().collect(Collectors.toMap(
                BidRequestEnricher::eidIdExtractor,
                Function.identity(),
                (a, b) -> b,
                HashMap::new));

        final Set<String> sourceToReplace = targetingProperties.getOptableInserterEidsReplace();
        final Set<String> sourceToMerge = targetingProperties.getOptableInserterEidsMerge()
                .stream()
                .filter(it -> !sourceToReplace.contains(it)).collect(Collectors.toSet());

        final List<Eid> mergedEid = destination.stream()
                .map(destinationEid -> idToSourceEid.containsKey(eidIdExtractor(destinationEid))
                        && OPTABLE_CO_INSERTER.equals(destinationEid.getInserter())
                        ? resolveEidConflict(
                                destinationEid,
                                idToSourceEid.get(eidIdExtractor(destinationEid)),
                                sourceToMerge,
                                sourceToReplace)
                        : destinationEid)
                .toList();

        return merge(mergedEid, source, BidRequestEnricher::eidIdExtractor);
    }

    private List<Eid> filterOptableEids(List<Eid> eids) {
        if (CollectionUtils.isEmpty(eids)) {
            return eids;
        }

        final Set<String> optableIdsToIgnore = targetingProperties.getOptableInserterEidsIgnore();
        if (CollectionUtils.isEmpty(optableIdsToIgnore)) {
            return eids;
        }

        return eids.stream()
                .filter(eid -> !OPTABLE_CO_INSERTER.equals(eid.getInserter())
                        || !optableIdsToIgnore.contains(eid.getSource()))
                .toList();
    }

    private static Eid resolveEidConflict(Eid destinationEid,
                                          Eid sourceEid,
                                          Set<String> sourceToMerge,
                                          Set<String> sourceToReplace) {

        final String eidSource = sourceEid.getSource();

        if (sourceToReplace.contains(eidSource)) {
            return sourceEid;
        }
        if (sourceToMerge.contains(eidSource)) {
            return mergeEid(destinationEid, sourceEid);
        }

        return destinationEid;
    }

    private static Eid mergeEid(Eid destinationEid, Eid sourceEid) {
        return destinationEid.toBuilder()
                .uids(merge(destinationEid.getUids(), sourceEid.getUids(), Uid::getId))
                .build();
    }

    private static String eidIdExtractor(Eid eid) {
        return "%s_%s".formatted(StringUtils.defaultString(eid.getInserter()), eid.getSource());
    }

    private static List<Data> mergeData(List<Data> destination, List<Data> source) {
        if (CollectionUtils.isEmpty(destination)) {
            return source;
        }

        if (CollectionUtils.isEmpty(source)) {
            return destination;
        }

        final Map<String, Data> idToSourceData = source.stream()
                .collect(Collectors.toMap(Data::getId, Function.identity(), (a, b) -> b, HashMap::new));

        final List<Data> mergedData = destination.stream()
                .map(destinationData -> idToSourceData.containsKey(destinationData.getId())
                        ? mergeData(destinationData, idToSourceData.get(destinationData.getId()))
                        : destinationData)
                .toList();

        return merge(mergedData, source, Data::getId);
    }

    private static Data mergeData(Data destinationData, Data sourceData) {
        return destinationData.toBuilder()
                .segment(merge(destinationData.getSegment(), sourceData.getSegment(), Segment::getId))
                .build();
    }

    private static <T, ID> List<T> merge(List<T> destination,
                                         List<T> source,
                                         Function<T, ID> idExtractor) {

        if (CollectionUtils.isEmpty(source)) {
            return destination;
        }

        if (CollectionUtils.isEmpty(destination)) {
            return source;
        }

        final Set<ID> existingIds = destination.stream()
                .map(idExtractor)
                .collect(Collectors.toSet());

        return Stream.concat(
                        destination.stream(),
                        source.stream()
                                .filter(entry -> !existingIds.contains(idExtractor.apply(entry))))
                .toList();
    }
}
