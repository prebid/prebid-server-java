package org.prebid.server.settings;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.Tuple2;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.settings.model.StoredProfileResult;
import org.prebid.server.settings.model.StoredResponseDataResult;
import software.amazon.awssdk.core.BytesWrapper;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementation of {@link ApplicationSettings}.
 * <p>
 * Reads an application settings from JSON file in a s3 bucket, stores and serves them in and from the memory.
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

    public S3ApplicationSettings(S3AsyncClient asyncClient,
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
        return withTimeout(() -> downloadFile(accountsDirectory + "/" + accountId + JSON_SUFFIX), timeout)
                .map(fileContent -> decodeAccount(fileContent, accountId));
    }

    private Account decodeAccount(String fileContent, String requestedAccountId) {
        if (fileContent == null) {
            throw new PreBidException("Account with id %s not found".formatted(requestedAccountId));
        }

        final Account account;
        try {
            account = jacksonMapper.decodeValue(fileContent, Account.class);
        } catch (DecodeException e) {
            throw new PreBidException("Invalid json for account with id %s".formatted(requestedAccountId));
        }

        validateAccount(account, requestedAccountId);
        return account;
    }

    private static void validateAccount(Account account, String requestedAccountId) {
        final String receivedAccountId = account != null ? account.getId() : null;
        if (!StringUtils.equals(receivedAccountId, requestedAccountId)) {
            throw new PreBidException(
                    "Account with id %s does not match id %s in file".formatted(requestedAccountId, receivedAccountId));
        }
    }

    @Override
    public Future<StoredDataResult> getStoredData(String accountId,
                                                  Set<String> requestIds,
                                                  Set<String> impIds,
                                                  Timeout timeout) {

        return withTimeout(
                () -> Future.all(
                        getFileContents(storedRequestsDirectory, requestIds),
                        getFileContents(storedImpressionsDirectory, impIds)),
                timeout)
                .map(results -> buildStoredDataResult(
                        results.resultAt(0),
                        results.resultAt(1),
                        requestIds,
                        impIds));
    }

    private StoredDataResult buildStoredDataResult(Map<String, String> storedIdToRequest,
                                                   Map<String, String> storedIdToImp,
                                                   Set<String> requestIds,
                                                   Set<String> impIds) {

        final List<String> errors = Stream.concat(
                        missingStoredDataIds(storedIdToImp, impIds).stream()
                                .map("No stored impression found for id: %s"::formatted),
                        missingStoredDataIds(storedIdToRequest, requestIds).stream()
                                .map("No stored request found for id: %s"::formatted))
                .toList();

        return StoredDataResult.of(storedIdToRequest, storedIdToImp, errors);
    }

    @Override
    public Future<StoredDataResult> getAmpStoredData(String accountId,
                                                     Set<String> requestIds,
                                                     Set<String> impIds,
                                                     Timeout timeout) {

        return getStoredData(accountId, requestIds, impIds, timeout);
    }

    @Override
    public Future<StoredDataResult> getVideoStoredData(String accountId,
                                                       Set<String> requestIds,
                                                       Set<String> impIds,
                                                       Timeout timeout) {

        return getStoredData(accountId, requestIds, impIds, timeout);
    }

    @Override
    public Future<StoredProfileResult> getProfiles(String accountId,
                                                   Set<String> requestIds,
                                                   Set<String> impIds,
                                                   Timeout timeout) {

        // TODO: change to success
        return Future.failedFuture("Not supported");
    }

    @Override
    public Future<StoredResponseDataResult> getStoredResponses(Set<String> responseIds, Timeout timeout) {
        return withTimeout(() -> getFileContents(storedResponsesDirectory, responseIds), timeout)
                .map(storedIdToResponse -> StoredResponseDataResult.of(
                        storedIdToResponse,
                        missingStoredDataIds(storedIdToResponse, responseIds).stream()
                                .map("No stored response found for id: %s"::formatted)
                                .toList()));
    }

    @Override
    public Future<Map<String, String>> getCategories(String primaryAdServer, String publisher, Timeout timeout) {
        return Future.succeededFuture(Collections.emptyMap());
    }

    private Future<Map<String, String>> getFileContents(String directory, Set<String> ids) {
        return Future.join(ids.stream()
                        .map(impId -> downloadFile(directory + withInitialSlash(impId) + JSON_SUFFIX)
                                .map(fileContent -> Tuple2.of(impId, fileContent)))
                        .toList())
                .map(CompositeFuture::<Tuple2<String, String>>list)
                .map(impIdToFileContent -> impIdToFileContent.stream()
                        .filter(tuple -> tuple.getRight() != null)
                        .collect(Collectors.toMap(Tuple2::getLeft, Tuple2::getRight)));
    }

    /**
     * When the impression id is the ad unit path it may already start with a slash and there's no need to add
     * another one.
     *
     * @param impressionId from the bid request
     * @return impression id with only a single slash at the beginning
     */
    private static String withInitialSlash(String impressionId) {
        return impressionId.startsWith("/") ? impressionId : "/" + impressionId;
    }

    private Future<String> downloadFile(String key) {
        final GetObjectRequest request = GetObjectRequest.builder().bucket(bucket).key(key).build();

        return Future.fromCompletionStage(
                        asyncClient.getObject(request, AsyncResponseTransformer.toBytes()),
                        vertx.getOrCreateContext())
                .map(BytesWrapper::asUtf8String)
                .otherwiseEmpty();
    }

    private <T> Future<T> withTimeout(Supplier<Future<T>> futureFactory, Timeout timeout) {
        final long remainingTime = timeout.remaining();
        if (remainingTime <= 0L) {
            return Future.failedFuture(new TimeoutException("Timeout has been exceeded"));
        }

        final Promise<T> promise = Promise.promise();
        final Future<T> future = futureFactory.get();

        final long timerId = vertx.setTimer(remainingTime, id ->
                promise.tryFail(new TimeoutException("Timeout has been exceeded")));

        future.onComplete(result -> {
            vertx.cancelTimer(timerId);
            if (result.succeeded()) {
                promise.tryComplete(result.result());
            } else {
                promise.tryFail(result.cause());
            }
        });

        return promise.future();
    }

    private Set<String> missingStoredDataIds(Map<String, String> fileContents, Set<String> requestedIds) {
        return SetUtils.difference(requestedIds, fileContents.keySet());
    }
}
