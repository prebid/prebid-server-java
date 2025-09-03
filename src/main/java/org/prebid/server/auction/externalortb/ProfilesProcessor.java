package org.prebid.server.auction.externalortb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import io.vertx.core.Future;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.exception.InvalidProfileException;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.execution.timeout.TimeoutFactory;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.JsonMerger;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountProfilesConfig;
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

    private static final ConditionalLogger conditionalLogger =
            new ConditionalLogger(LoggerFactory.getLogger(ProfilesProcessor.class));

    private final int maxProfiles;
    private final long defaultTimeoutMillis;
    private final boolean failOnUnknown;
    private final double logSamplingRate;
    private final ApplicationSettings applicationSettings;
    private final TimeoutFactory timeoutFactory;
    private final Metrics metrics;
    private final JacksonMapper mapper;
    private final JsonMerger jsonMerger;

    public ProfilesProcessor(int maxProfiles,
                             long defaultTimeoutMillis,
                             boolean failOnUnknown,
                             double logSamplingRate,
                             ApplicationSettings applicationSettings,
                             TimeoutFactory timeoutFactory,
                             Metrics metrics,
                             JacksonMapper mapper,
                             JsonMerger jsonMerger) {

        this.maxProfiles = maxProfiles;
        this.defaultTimeoutMillis = defaultTimeoutMillis;
        this.failOnUnknown = failOnUnknown;
        this.logSamplingRate = logSamplingRate;
        this.applicationSettings = Objects.requireNonNull(applicationSettings);
        this.timeoutFactory = Objects.requireNonNull(timeoutFactory);
        this.metrics = Objects.requireNonNull(metrics);
        this.mapper = Objects.requireNonNull(mapper);
        this.jsonMerger = Objects.requireNonNull(jsonMerger);
    }

    public Future<BidRequest> process(AuctionContext auctionContext, BidRequest bidRequest) {
        final String accountId = Optional.ofNullable(auctionContext.getAccount())
                .map(Account::getId)
                .orElse(StringUtils.EMPTY);

        final AllProfilesIds profilesIds = profilesIds(bidRequest, auctionContext, accountId);
        if (profilesIds.isEmpty()) {
            return Future.succeededFuture(bidRequest);
        }

        final boolean failOnUnknown = isFailOnUnknown(auctionContext.getAccount());

        return fetchProfiles(accountId, profilesIds, timeoutMillis(bidRequest))
                .compose(profiles -> emitMetrics(accountId, profiles, auctionContext, failOnUnknown))
                .map(profiles -> mergeResults(
                        applyRequestProfiles(
                                profilesIds.request(),
                                profiles.getStoredIdToRequest(),
                                bidRequest,
                                failOnUnknown),
                        applyImpsProfiles(
                                profilesIds.imps(),
                                profiles.getStoredIdToImp(),
                                bidRequest.getImp(),
                                failOnUnknown)))
                .recover(error -> Future.failedFuture(
                        new InvalidRequestException("Error during processing profiles: " + error.getMessage())));
    }

    private AllProfilesIds profilesIds(BidRequest bidRequest, AuctionContext auctionContext, String accountId) {
        final AllProfilesIds initialProfilesIds = new AllProfilesIds(
                requestProfilesIds(bidRequest),
                bidRequest.getImp().stream().map(this::impProfilesIds).toList());

        final AllProfilesIds profilesIds = truncate(
                initialProfilesIds,
                Optional.ofNullable(auctionContext.getAccount())
                        .map(Account::getAuction)
                        .map(AccountAuctionConfig::getProfiles)
                        .map(AccountProfilesConfig::getLimit)
                        .orElse(maxProfiles));

        if (auctionContext.getDebugContext().isDebugEnabled() && !profilesIds.equals(initialProfilesIds)) {
            auctionContext.getDebugWarnings().add("Profiles exceeded the limit.");
            metrics.updateAccountProfileMetric(accountId, MetricName.limit_exceeded);
        }

        return profilesIds;
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
            throw new InvalidRequestException(e.getMessage());
        }
    }

    private static AllProfilesIds truncate(AllProfilesIds profilesIds, int maxProfiles) {
        final List<String> requestProfiles = profilesIds.request();
        final int impProfilesLimit = Math.max(0, maxProfiles - requestProfiles.size());

        return new AllProfilesIds(
                truncate(requestProfiles, maxProfiles),
                profilesIds.imps().stream()
                        .map(impProfiles -> truncate(impProfiles, impProfilesLimit))
                        .toList());
    }

    private static <T> List<T> truncate(List<T> list, int maxSize) {
        return list.size() > maxSize ? list.subList(0, maxSize) : list;
    }

    private long timeoutMillis(BidRequest bidRequest) {
        final Long tmax = bidRequest.getTmax();
        return tmax != null && tmax > 0 ? tmax : defaultTimeoutMillis;
    }

    private boolean isFailOnUnknown(Account account) {
        return Optional.ofNullable(account)
                .map(Account::getAuction)
                .map(AccountAuctionConfig::getProfiles)
                .map(AccountProfilesConfig::getFailOnUnknown)
                .orElse(failOnUnknown);
    }

    private Future<StoredDataResult<Profile>> fetchProfiles(String accountId,
                                                            AllProfilesIds allProfilesIds,
                                                            long timeoutMillis) {

        final Set<String> requestProfilesIds = new HashSet<>(allProfilesIds.request());
        final Set<String> impProfilesIds = allProfilesIds.imps().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
        final Timeout timeout = timeoutFactory.create(timeoutMillis);

        return applicationSettings.getProfiles(accountId, requestProfilesIds, impProfilesIds, timeout);
    }

    private Future<StoredDataResult<Profile>> emitMetrics(String accountId,
                                                          StoredDataResult<Profile> fetchResult,
                                                          AuctionContext auctionContext,
                                                          boolean failOnUnknown) {

        final List<String> errors = fetchResult.getErrors();
        if (!errors.isEmpty()) {
            metrics.updateProfileMetric(MetricName.missing);

            if (auctionContext.getDebugContext().isDebugEnabled()) {
                metrics.updateAccountProfileMetric(accountId, MetricName.missing);
                auctionContext.getDebugWarnings().addAll(errors);
            }

            if (failOnUnknown) {
                return Future.failedFuture(new InvalidProfileException(errors));
            }
        }

        return Future.succeededFuture(fetchResult);
    }

    private BidRequest applyRequestProfiles(List<String> profilesIds,
                                            Map<String, Profile> idToRequestProfile,
                                            BidRequest bidRequest,
                                            boolean failOnUnknown) {

        return !idToRequestProfile.isEmpty()
                ? applyProfiles(profilesIds, idToRequestProfile, bidRequest, failOnUnknown)
                : bidRequest;
    }

    private <T> T applyProfiles(List<String> profilesIds,
                                Map<String, Profile> idToProfile,
                                T original,
                                boolean failOnUnknown) {

        if (profilesIds.isEmpty()) {
            return original;
        }

        ObjectNode result = mapper.mapper().valueToTree(original);
        for (String profileId : profilesIds) {
            try {
                final Profile profile = idToProfile.get(profileId);
                result = profile != null ? mergeProfile(result, profile) : result;
            } catch (InvalidRequestException e) {
                final String message = "Can't merge with profile %s: %s".formatted(profileId, e.getMessage());

                metrics.updateProfileMetric(MetricName.invalid);
                conditionalLogger.error(message, logSamplingRate);
                if (failOnUnknown) {
                    throw new InvalidProfileException(message);
                }
            }
        }

        try {
            return mapper.mapper().treeToValue(result, (Class<T>) original.getClass());
        } catch (JsonProcessingException e) {
            throw new InvalidProfileException(e.getMessage());
        }
    }

    private ObjectNode mergeProfile(ObjectNode original, Profile profile) {
        return switch (profile.getMergePrecedence()) {
            case REQUEST -> merge(original, profile.getBody());
            case PROFILE -> merge(profile.getBody(), original);
        };
    }

    private ObjectNode merge(JsonNode takePrecedence, JsonNode other) {
        if (!takePrecedence.isObject() || !other.isObject()) {
            throw new InvalidRequestException("One of the merge arguments is not an object.");
        }

        return (ObjectNode) jsonMerger.merge(takePrecedence, other);
    }

    private List<Imp> applyImpsProfiles(List<List<String>> profilesIds,
                                        Map<String, Profile> idToImpProfile,
                                        List<Imp> imps,
                                        boolean failOnUnknown) {

        if (idToImpProfile.isEmpty()) {
            return imps;
        }

        final List<Imp> updatedImps = new ArrayList<>(imps);
        for (int i = 0; i < profilesIds.size(); i++) {
            updatedImps.set(i, applyProfiles(
                    profilesIds.get(i),
                    idToImpProfile,
                    imps.get(i),
                    failOnUnknown));
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
