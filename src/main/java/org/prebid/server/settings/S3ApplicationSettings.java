package org.prebid.server.settings;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.prebid.server.auction.model.Tuple2;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.settings.model.StoredResponseDataResult;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementation of {@link ApplicationSettings}.
 * <p>
 * Reads an application settings from JSON file in an s3 bucket, stores and serves them in and from the memory.
 * <p>
 * Immediately loads stored request data from local files. These are stored in memory for low-latency reads.
 * This expects each file in the directory to be named "{config_id}.json".
 */
public class S3ApplicationSettings implements ApplicationSettings {

    private static final String JSON_SUFFIX = ".json";

    final S3AsyncClient asyncClient;
    final String bucket;
    final String accountsDirectory;
    final String storedImpressionsDirectory;
    final String storedRequestsDirectory;
    final String storedResponsesDirectory;
    final JacksonMapper jacksonMapper;
    final Vertx vertx;

    public S3ApplicationSettings(
            S3AsyncClient asyncClient,
            String bucket,
            String accountsDirectory,
            String storedImpressionsDirectory,
            String storedRequestsDirectory,
            String storedResponsesDirectory,
            JacksonMapper jacksonMapper,
            Vertx vertx) {

        this.asyncClient = Objects.requireNonNull(asyncClient);
        this.bucket = Objects.requireNonNull(bucket);
        this.accountsDirectory = Objects.requireNonNull(accountsDirectory);
        this.storedImpressionsDirectory = Objects.requireNonNull(storedImpressionsDirectory);
        this.storedRequestsDirectory = Objects.requireNonNull(storedRequestsDirectory);
        this.storedResponsesDirectory = Objects.requireNonNull(storedResponsesDirectory);
        this.jacksonMapper = Objects.requireNonNull(jacksonMapper);
        this.vertx = Objects.requireNonNull(vertx);
    }

    @Override
    public Future<Account> getAccountById(String accountId, Timeout timeout) {
        return downloadFile(accountsDirectory + "/" + accountId + JSON_SUFFIX)
                .map(fileContentOpt ->
                        fileContentOpt.map(fileContent -> jacksonMapper.decodeValue(fileContent, Account.class)))
                .compose(accountOpt -> {
                    if (accountOpt.isPresent()) {
                        return Future.succeededFuture(accountOpt.get());
                    } else {
                        return Future
                                .failedFuture(new PreBidException("Account with id %s not found".formatted(accountId)));
                    }
                })
                .recover(ex -> {
                    if (ex instanceof DecodeException) {
                        return Future
                                .failedFuture(
                                        new PreBidException(
                                                "Invalid json for account with id %s".formatted(accountId)));
                    }
                    return Future
                            .failedFuture(new PreBidException("Account with id %s not found".formatted(accountId)));
                });
    }

    @Override
    public Future<StoredDataResult> getStoredData(
            String accountId,
            Set<String> requestIds,
            Set<String> impIds,
            Timeout timeout) {

        return getFileContents(storedRequestsDirectory, requestIds)
                .compose(storedIdToRequest -> getFileContents(storedImpressionsDirectory, impIds)
                     .map(storedIdToImp -> buildStoredDataResult(storedIdToRequest, storedIdToImp, requestIds, impIds))
                );
    }

    private StoredDataResult buildStoredDataResult(
            Map<String, String> storedIdToRequest,
            Map<String, String> storedIdToImp,
            Set<String> requestIds,
            Set<String> impIds
    ) {
        final List<String> missingStoredRequestIds =
                getMissingStoredDataIds(storedIdToRequest, requestIds).stream()
                        .map("No stored request found for id: %s"::formatted).toList();
        final List<String> missingStoredImpressionIds =
                getMissingStoredDataIds(storedIdToImp, impIds).stream()
                        .map("No stored impression found for id: %s"::formatted).toList();

        return StoredDataResult.of(
                storedIdToRequest,
                storedIdToImp,
                Stream.concat(
                        missingStoredImpressionIds.stream(),
                        missingStoredRequestIds.stream()).toList());
    }

    private List<String> getMissingStoredDataIds(Map<String, String> fileContents, Set<String> responseIds) {
        final List<String> missingStoredDataIds = new ArrayList<>(responseIds);
        missingStoredDataIds.removeAll(fileContents.keySet());

        return missingStoredDataIds;
    }

    @Override
    public Future<StoredDataResult> getAmpStoredData(
            String accountId,
            Set<String> requestIds,
            Set<String> impIds,
            Timeout timeout) {

        return getStoredData(accountId, requestIds, Collections.emptySet(), timeout);
    }

    @Override
    public Future<StoredDataResult> getVideoStoredData(
            String accountId,
            Set<String> requestIds,
            Set<String> impIds,
            Timeout timeout) {

        return getStoredData(accountId, requestIds, impIds, timeout);
    }

    @Override
    public Future<StoredResponseDataResult> getStoredResponses(Set<String> responseIds, Timeout timeout) {
        return getFileContents(storedResponsesDirectory, responseIds).map(storedIdToResponse -> {
            final List<String> missingStoredResponseIds =
                    getMissingStoredDataIds(storedIdToResponse, responseIds).stream()
                            .map("No stored response found for id: %s"::formatted).toList();

            return StoredResponseDataResult.of(storedIdToResponse, missingStoredResponseIds);
        });
    }

    @Override
    public Future<Map<String, String>> getCategories(String primaryAdServer, String publisher, Timeout timeout) {
        return Future.succeededFuture(Collections.emptyMap());
    }

    private Future<Map<String, String>> getFileContents(String directory, Set<String> ids) {
        final List<Future<Tuple2<String, Optional<String>>>> futureListContents = ids.stream()
                .map(impressionId ->
                        downloadFile(directory + withInitialSlash(impressionId) + JSON_SUFFIX)
                                .map(fileContent -> Tuple2.of(impressionId, fileContent)))
                .collect(Collectors.toCollection(ArrayList::new));

        final Future<List<Tuple2<String, Optional<String>>>> composedFutures = CompositeFuture
                .all(new ArrayList<>(futureListContents))
                .map(CompositeFuture::list);

        // filter out IDs that had no stored request present and return a map from ids to stored request content
        return composedFutures.map(one -> one.stream().flatMap(idContentTuple ->
                idContentTuple.getRight().stream().map(content -> Tuple2.of(idContentTuple.getLeft(), content))
        )).map(one -> one.collect(Collectors.toMap(Tuple2::getLeft, Tuple2::getRight)));
    }

    /**
     * When the impression id is the ad unit path it may already start with a slash and there's no need to add
     * another one.
     *
     * @param impressionId from the bid request
     * @return impression id with only a single slash at the beginning
     */
    private static String withInitialSlash(String impressionId) {
        if (impressionId.startsWith("/")) {
            return impressionId;
        }
        return "/" + impressionId;
    }

    private Future<Optional<String>> downloadFile(String key) {
        final GetObjectRequest request = GetObjectRequest.builder().bucket(bucket).key(key).build();

        return Future.fromCompletionStage(
                        asyncClient.getObject(request, AsyncResponseTransformer.toBytes()),
                        vertx.getOrCreateContext())
                .map(test -> Optional.of(test.asUtf8String())).recover(ex -> Future.succeededFuture(Optional.empty()));
    }

}
