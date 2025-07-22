package org.prebid.server.auction.externalortb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import io.vertx.core.Future;
import org.prebid.server.exception.InvalidProfileException;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.execution.timeout.TimeoutFactory;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.JsonMerger;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.Profile;
import org.prebid.server.settings.model.StoredDataResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class ProfilesProcessor {

    private final int maxProfiles;
    private final long defaultTimeoutMillis;
    private final ApplicationSettings applicationSettings;
    private final TimeoutFactory timeoutFactory;
    private final Metrics metrics;
    private final JacksonMapper mapper;
    private final JsonMerger jsonMerger;

    public ProfilesProcessor(int maxProfiles,
                             long defaultTimeoutMillis,
                             ApplicationSettings applicationSettings,
                             TimeoutFactory timeoutFactory,
                             Metrics metrics,
                             JacksonMapper mapper,
                             JsonMerger jsonMerger) {

        this.maxProfiles = maxProfiles;
        this.defaultTimeoutMillis = defaultTimeoutMillis;
        this.applicationSettings = Objects.requireNonNull(applicationSettings);
        this.timeoutFactory = Objects.requireNonNull(timeoutFactory);
        this.metrics = Objects.requireNonNull(metrics);
        this.mapper = Objects.requireNonNull(mapper);
        this.jsonMerger = Objects.requireNonNull(jsonMerger);
    }

    public Future<BidRequest> process(Account account, BidRequest bidRequest) {
        final List<Imp> imps = bidRequest.getImp();

        final AllProfilesIds profilesIds = truncate(new AllProfilesIds(
                requestProfilesIds(bidRequest),
                imps.stream()
                        .map(this::impProfilesIds)
                        .toList()));

        if (profilesIds.isEmpty()) {
            return Future.succeededFuture(bidRequest);
        }

        return fetchProfiles(account.getId(), profilesIds, timeoutMillis(bidRequest))
                .map(profiles -> mergeResults(
                        applyRequestProfiles(profilesIds.request(), profiles.getStoredIdToRequest(), bidRequest),
                        applyImpsProfiles(profilesIds.imps(), profiles.getStoredIdToImp(), imps)))
                .recover(e -> Future.failedFuture(
                        new InvalidRequestException("Error during processing profiles: " + e.getMessage())));
    }

    private static List<String> requestProfilesIds(BidRequest bidRequest) {
        return Optional.ofNullable(bidRequest)
                .map(BidRequest::getExt)
                .map(ExtRequest::getPrebid)
                .map(ExtRequestPrebid::getProfiles)
                .orElse(Collections.emptyList());
    }

    private List<String> impProfilesIds(Imp imp) {
        return Optional.ofNullable(imp.getExt())
                .map(ext -> ext.get("prebid"))
                .map(this::parseImpExt)
                .map(ExtImpPrebid::getProfiles)
                .orElse(Collections.emptyList());
    }

    private ExtImpPrebid parseImpExt(JsonNode jsonNode) {
        try {
            return mapper.mapper().treeToValue(jsonNode, ExtImpPrebid.class);
        } catch (JsonProcessingException e) {
            throw new InvalidProfileException(e.getMessage());
        }
    }

    private AllProfilesIds truncate(AllProfilesIds profilesIds) {
        // TODO:
        // 1. How to limit for multiple imps (each contains profiles)?
        // 2. Which of these approaches is correct?
        //      - limit -> fetch
        //      - fetch -> limit (don't count invalid profiles)
        //      - fetch -> limit (count invalid profiles)
        return profilesIds;
    }

    private long timeoutMillis(BidRequest bidRequest) {
        final Long tmax = bidRequest.getTmax();
        return tmax != null && tmax > 0 ? tmax : defaultTimeoutMillis;
    }

    private Future<StoredDataResult<Profile>> fetchProfiles(String accountId,
                                                            AllProfilesIds allProfilesIds,
                                                            long timeoutMillis) {

        final Set<String> requestProfilesIds = new HashSet<>(allProfilesIds.request());
        final Set<String> impProfilesIds = allProfilesIds.imps().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
        final Timeout timeout = timeoutFactory.create(timeoutMillis);

        return applicationSettings.getProfiles(accountId, requestProfilesIds, impProfilesIds, timeout)
                .compose(profiles -> profiles.getErrors().isEmpty()
                        ? Future.succeededFuture(profiles)
                        : Future.failedFuture(new InvalidProfileException(profiles.getErrors())));
    }

    private BidRequest applyRequestProfiles(List<String> profilesIds,
                                            Map<String, Profile> idToRequestProfile,
                                            BidRequest bidRequest) {

        return !idToRequestProfile.isEmpty()
                ? applyProfiles(profilesIds, idToRequestProfile, bidRequest, BidRequest.class)
                : bidRequest;
    }

    private <T> T applyProfiles(List<String> profilesIds,
                                Map<String, Profile> idToProfile,
                                T original,
                                Class<T> tClass) {

        if (profilesIds.isEmpty()) {
            return original;
        }

        ObjectNode result = mapper.mapper().valueToTree(original);
        for (String profileId : profilesIds) {
            final Profile profile = idToProfile.get(profileId);
            result = mergeProfile(result, profile, profileId);
        }

        try {
            return mapper.mapper().treeToValue(result, tClass);
        } catch (JsonProcessingException e) {
            throw new InvalidProfileException(e.getMessage());
        }
    }

    private ObjectNode mergeProfile(ObjectNode original, Profile profile, String profileId) {
        return switch (profile.getMergePrecedence()) {
            case REQUEST -> merge(original, parse(profile.getBody()), profileId);
            case PROFILE -> merge(parse(profile.getBody()), original, profileId);
        };
    }

    private ObjectNode parse(String body) {
        try {
            return mapper.decodeValue(body, ObjectNode.class);
        } catch (DecodeException e) {
            throw new InvalidProfileException("Can't parse profile: " + e.getMessage());
        }
    }

    private ObjectNode merge(ObjectNode takePrecedence, ObjectNode other, String profileId) {
        try {
            return (ObjectNode) jsonMerger.merge(takePrecedence, other);
        } catch (InvalidRequestException e) {
            throw new InvalidProfileException("Can't merge with profile %s: %s".formatted(profileId, e.getMessage()));
        }
    }

    private List<Imp> applyImpsProfiles(List<List<String>> profilesIds,
                                        Map<String, Profile> idToImpProfile,
                                        List<Imp> imps) {

        if (idToImpProfile.isEmpty()) {
            return imps;
        }

        final List<Imp> updatedImps = new ArrayList<>(imps);
        for (int i = 0; i < profilesIds.size(); i++) {
            updatedImps.set(i, applyProfiles(profilesIds.get(i), idToImpProfile, imps.get(i), Imp.class));
        }

        return Collections.unmodifiableList(updatedImps);
    }

    private static BidRequest mergeResults(BidRequest bidRequest, List<Imp> imps) {
        return bidRequest.toBuilder().imp(imps).build();
    }

    private record AllProfilesIds(List<String> request, List<List<String>> imps) {

        public boolean isEmpty() {
            return request.isEmpty() && imps.stream().allMatch(List::isEmpty);
        }
    }
}
