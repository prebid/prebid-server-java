package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Video;
import io.vertx.core.Future;
import io.vertx.core.file.FileSystem;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.InvalidStoredImpException;
import org.prebid.server.exception.InvalidStoredRequestException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.identity.IdGenerator;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.JsonMerger;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.request.ExtImp;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredRequest;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.settings.model.VideoStoredDataResult;
import org.prebid.server.util.ObjectUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Executes stored request processing.
 */
public class StoredRequestProcessor {

    private static final String OVERRIDE_BID_REQUEST_ID_TEMPLATE = "{{UUID}}";

    private final long defaultTimeout;
    private final BidRequest defaultBidRequest;
    private final boolean generateBidRequestId;
    private final ApplicationSettings applicationSettings;
    private final IdGenerator idGenerator;
    private final Metrics metrics;
    private final TimeoutFactory timeoutFactory;
    private final JacksonMapper mapper;
    private final JsonMerger jsonMerger;

    public StoredRequestProcessor(long defaultTimeout,
                                  String defaultBidRequestPath,
                                  boolean generateBidRequestId,
                                  FileSystem fileSystem,
                                  ApplicationSettings applicationSettings,
                                  IdGenerator idGenerator,
                                  Metrics metrics,
                                  TimeoutFactory timeoutFactory,
                                  JacksonMapper mapper,
                                  JsonMerger jsonMerger) {

        this.defaultTimeout = defaultTimeout;
        this.defaultBidRequest = readBidRequest(
                defaultBidRequestPath, Objects.requireNonNull(fileSystem), Objects.requireNonNull(mapper));
        this.generateBidRequestId = generateBidRequestId;
        this.applicationSettings = Objects.requireNonNull(applicationSettings);
        this.idGenerator = Objects.requireNonNull(idGenerator);
        this.metrics = Objects.requireNonNull(metrics);
        this.timeoutFactory = Objects.requireNonNull(timeoutFactory);
        this.mapper = Objects.requireNonNull(mapper);
        this.jsonMerger = Objects.requireNonNull(jsonMerger);
    }

    public Future<BidRequest> processAuctionRequest(String accountId, BidRequest bidRequest) {
        return processAuctionStoredRequest(accountId, bidRequest)
                .onFailure(cause -> updateInvalidStoredResultMetrics(accountId, cause))
                .recover(StoredRequestProcessor::stripToInvalidRequestException);
    }

    private Future<BidRequest> processAuctionStoredRequest(String accountId, BidRequest bidRequest) {
        final Map<BidRequest, String> bidRequestToStoredRequestId;
        final Map<Imp, String> impToStoredRequestId;
        try {
            bidRequestToStoredRequestId = mapStoredRequestHolderToStoredRequestId(
                    Collections.singletonList(bidRequest), StoredRequestProcessor::getStoredRequestIdFromBidRequest);

            impToStoredRequestId = mapStoredRequestHolderToStoredRequestId(
                    bidRequest.getImp(), this::getStoredRequestIdFromImp);
        } catch (InvalidStoredRequestException | InvalidStoredImpException e) {
            return Future.failedFuture(e);
        }

        final Set<String> requestIds = new HashSet<>(bidRequestToStoredRequestId.values());
        final Set<String> impIds = new HashSet<>(impToStoredRequestId.values());
        if (requestIds.isEmpty() && impIds.isEmpty()) {
            return Future.succeededFuture(bidRequest);
        }

        final Future<StoredDataResult> storedDataFuture =
                applicationSettings.getStoredData(accountId, requestIds, impIds, timeout(bidRequest))
                        .onSuccess(storedDataResult -> updateStoredResultMetrics(storedDataResult, requestIds, impIds));

        return storedRequestsToBidRequest(
                storedDataFuture, bidRequest, bidRequestToStoredRequestId.get(bidRequest), impToStoredRequestId)
                .map(this::generateBidRequestIdForApp);
    }

    public Future<BidRequest> processAmpRequest(String accountId, String ampRequestId, BidRequest bidRequest) {
        return processAmpStoredRequest(accountId, ampRequestId, bidRequest)
                .onFailure(cause -> updateInvalidStoredResultMetrics(accountId, cause))
                .recover(StoredRequestProcessor::stripToInvalidRequestException);
    }

    private Future<BidRequest> processAmpStoredRequest(String accountId, String ampRequestId, BidRequest bidRequest) {
        final Future<StoredDataResult> ampStoredDataFuture = applicationSettings.getAmpStoredData(
                        accountId, Collections.singleton(ampRequestId), Collections.emptySet(), timeout(bidRequest))
                .onSuccess(storedDataResult -> updateStoredResultMetrics(
                        storedDataResult, Collections.singleton(ampRequestId), Collections.emptySet()));

        return storedRequestsToBidRequest(ampStoredDataFuture, bidRequest, ampRequestId, Collections.emptyMap())
                .map(this::generateBidRequestId);
    }

    Future<VideoStoredDataResult> videoStoredDataResult(String accountId,
                                                        List<Imp> imps,
                                                        List<String> errors,
                                                        Timeout timeout) {

        return videoStoredDataResultInternal(accountId, imps, errors, timeout)
                .onFailure(cause -> updateInvalidStoredResultMetrics(accountId, cause))
                .recover(StoredRequestProcessor::stripToInvalidRequestException);
    }

    private Future<VideoStoredDataResult> videoStoredDataResultInternal(String accountId,
                                                                        List<Imp> imps,
                                                                        List<String> errors,
                                                                        Timeout timeout) {

        final Map<Imp, String> impToStoredRequestId;
        try {
            impToStoredRequestId = mapStoredRequestHolderToStoredRequestId(imps, this::getStoredRequestIdFromImp);
        } catch (InvalidStoredImpException e) {
            return Future.failedFuture(e);
        }

        final Map<String, String> storedIdToImpId = impToStoredRequestId.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getValue, impIdToStoredId -> impIdToStoredId.getKey().getId()));

        return applicationSettings.getStoredData(accountId, Collections.emptySet(), storedIdToImpId.keySet(), timeout)
                .map(storedDataResult -> makeVideoStoredDataResult(storedDataResult, storedIdToImpId, errors));
    }

    private void updateInvalidStoredResultMetrics(String accountId, Throwable cause) {
        if (cause instanceof InvalidStoredRequestException) {
            metrics.updateAccountRequestRejectedByInvalidStoredRequestMetrics(accountId);
        } else if (cause instanceof InvalidStoredImpException) {
            metrics.updateAccountRequestRejectedByInvalidStoredImpMetrics(accountId);
        }
    }

    private static <T> Future<T> stripToInvalidRequestException(Throwable cause) {
        return Future.failedFuture(new InvalidRequestException(
                "Stored request processing failed: " + cause.getMessage()));
    }

    private void updateStoredResultMetrics(StoredDataResult storedDataResult,
                                           Set<String> requestIds,
                                           Set<String> impIds) {

        requestIds.forEach(
                id -> metrics.updateStoredRequestMetric(storedDataResult.getStoredIdToRequest().containsKey(id)));

        impIds.forEach(
                id -> metrics.updateStoredImpsMetric(storedDataResult.getStoredIdToImp().containsKey(id)));
    }

    private static BidRequest readBidRequest(String defaultBidRequestPath,
                                             FileSystem fileSystem,
                                             JacksonMapper mapper) {

        return StringUtils.isNotBlank(defaultBidRequestPath)
                ? mapper.decodeValue(fileSystem.readFileBlocking(defaultBidRequestPath), BidRequest.class)
                : null;
    }

    private VideoStoredDataResult makeVideoStoredDataResult(StoredDataResult storedDataResult,
                                                            Map<String, String> storedIdToImpId,
                                                            List<String> errors) {

        final Map<String, String> storedIdToStoredImp = storedDataResult.getStoredIdToImp();
        final Map<String, Video> impIdToStoredVideo = new HashMap<>();

        for (Map.Entry<String, String> storedIdToImpIdEntry : storedIdToImpId.entrySet()) {
            final String storedId = storedIdToImpIdEntry.getKey();
            final String storedImp = storedIdToStoredImp.get(storedId);
            if (storedImp == null) {
                errors.add(String.format("No stored Imp for stored id %s", storedId));
                continue;
            }

            final String impId = storedIdToImpIdEntry.getValue();
            final Video video = parseVideoFromImp(storedImp);
            if (video == null) {
                errors.add(String.format("No stored video found for Imp with id %s", impId));
                continue;
            }

            impIdToStoredVideo.put(impId, video);
        }

        return VideoStoredDataResult.of(impIdToStoredVideo, errors);
    }

    private Video parseVideoFromImp(String storedJson) {
        if (StringUtils.isNotBlank(storedJson)) {
            try {
                final Imp imp = mapper.mapper().readValue(storedJson, Imp.class);
                return imp.getVideo();
            } catch (JsonProcessingException e) {
                return null;
            }
        }
        return null;
    }

    private Future<BidRequest> storedRequestsToBidRequest(Future<StoredDataResult> storedDataFuture,
                                                          BidRequest bidRequest,
                                                          String storedBidRequestId,
                                                          Map<Imp, String> impsToStoredRequestId) {

        return storedDataFuture
                .compose(result -> !result.getErrors().isEmpty()
                        ? Future.failedFuture(new InvalidStoredRequestException(result.getErrors()))
                        : Future.succeededFuture(result))
                .map(result -> mergeBidRequestAndImps(
                        bidRequest, storedBidRequestId, impsToStoredRequestId, result));
    }

    /**
     * Runs {@link BidRequest} and {@link Imp}s merge processes.
     * <p>
     * The merging priority is: original request > stored request > default request
     */
    private BidRequest mergeBidRequestAndImps(BidRequest bidRequest,
                                              String storedRequestId,
                                              Map<Imp, String> impToStoredId,
                                              StoredDataResult storedDataResult) {

        final BidRequest mergedWithStoredRequest = mergeBidRequest(bidRequest, storedRequestId, storedDataResult);

        final BidRequest mergedWithDefaultRequest = mergeDefaultRequest(mergedWithStoredRequest);

        return mergeImps(mergedWithDefaultRequest, impToStoredId, storedDataResult);
    }

    private BidRequest mergeDefaultRequest(BidRequest bidRequest) {
        return jsonMerger.merge(bidRequest, defaultBidRequest, BidRequest.class);
    }

    /**
     * Merges original request with request from stored request source. Values from original request
     * has higher priority than stored request values.
     */
    private BidRequest mergeBidRequest(BidRequest originalRequest,
                                       String storedRequestId,
                                       StoredDataResult storedDataResult) {

        final String storedRequest = storedDataResult.getStoredIdToRequest().get(storedRequestId);
        return StringUtils.isNotBlank(storedRequestId)
                ? jsonMerger.merge(originalRequest, storedRequest, storedRequestId, BidRequest.class)
                : originalRequest;
    }

    /**
     * Merges {@link Imp}s from original request with Imps from stored request source. Values from original request
     * has higher priority than stored request values.
     */
    private BidRequest mergeImps(BidRequest bidRequest,
                                 Map<Imp, String> impToStoredId,
                                 StoredDataResult storedDataResult) {

        if (impToStoredId.isEmpty()) {
            return bidRequest;
        }
        final List<Imp> mergedImps = new ArrayList<>(bidRequest.getImp());
        for (int i = 0; i < mergedImps.size(); i++) {
            final Imp imp = mergedImps.get(i);
            final String storedRequestId = impToStoredId.get(imp);
            if (storedRequestId != null) {
                final String storedImp = storedDataResult.getStoredIdToImp().get(storedRequestId);
                final Imp mergedImp = jsonMerger.merge(imp, storedImp, storedRequestId, Imp.class);
                mergedImps.set(i, mergedImp);
            }
        }
        return bidRequest.toBuilder().imp(mergedImps).build();
    }

    private BidRequest generateBidRequestIdForApp(BidRequest bidRequest) {
        return bidRequest.getApp() != null
                ? generateBidRequestId(bidRequest)
                : bidRequest;
    }

    private BidRequest generateBidRequestId(BidRequest bidRequest) {
        return generateBidRequestId || Objects.equals(bidRequest.getId(), OVERRIDE_BID_REQUEST_ID_TEMPLATE)
                ? bidRequest.toBuilder().id(idGenerator.generateId()).build()
                : bidRequest;
    }

    /**
     * Maps object to its StoredRequestId if exists. If object's extension contains storedRequest field, expected
     * that it includes id too, in another case error about missed id in stored request will be added to error list.
     * Gathers all errors into list, and in case if it is not empty, throws {@link InvalidRequestException} with list
     * of errors.
     */
    private static <K> Map<K, String> mapStoredRequestHolderToStoredRequestId(
            List<K> storedRequestHolders,
            Function<K, String> storedRequestIdExtractor) {

        if (CollectionUtils.isEmpty(storedRequestHolders)) {
            return Collections.emptyMap();
        }

        final Map<K, String> holderToPreBidRequest = new HashMap<>();

        for (K storedRequestHolder : storedRequestHolders) {
            final String storedRequestId = storedRequestIdExtractor.apply(storedRequestHolder);
            if (storedRequestId != null) {
                holderToPreBidRequest.put(storedRequestHolder, storedRequestId);
            }
        }

        return holderToPreBidRequest;
    }

    private static String getStoredRequestIdFromBidRequest(BidRequest bidRequest) {
        final ExtRequestPrebid prebid = ObjectUtil.getIfNotNull(bidRequest.getExt(), ExtRequest::getPrebid);
        final ExtStoredRequest extStoredRequest = ObjectUtil.getIfNotNull(prebid, ExtRequestPrebid::getStoredrequest);

        if (extStoredRequest == null) {
            return null;
        }

        final String storedRequestId = extStoredRequest.getId();
        if (storedRequestId == null) {
            throw new InvalidStoredRequestException("Id is not found in storedRequest");
        }

        return storedRequestId;
    }

    private String getStoredRequestIdFromImp(Imp imp) {
        if (imp.getExt() == null) {
            return null;
        }

        final ExtImp extImp;
        try {
            extImp = mapper.mapper().treeToValue(imp.getExt(), ExtImp.class);
        } catch (JsonProcessingException e) {
            throw new InvalidStoredImpException(
                    "Incorrect Imp extension format for Imp with id " + imp.getId() + ": " + e.getMessage());
        }

        final ExtStoredRequest extStoredRequest = ObjectUtil.getIfNotNull(
                extImp.getPrebid(), ExtImpPrebid::getStoredrequest);
        if (extStoredRequest == null) {
            return null;
        }

        final String storedRequestId = extStoredRequest.getId();
        if (storedRequestId == null) {
            throw new InvalidStoredImpException("Id is not found in storedRequest");
        }

        return storedRequestId;
    }

    /**
     * If the request defines tmax explicitly, then it is returned as is. Otherwise, default timeout is returned.
     */
    private Timeout timeout(BidRequest bidRequest) {
        final Long tmax = bidRequest.getTmax();
        return timeoutFactory.create(tmax != null && tmax > 0 ? tmax : defaultTimeout);
    }
}
