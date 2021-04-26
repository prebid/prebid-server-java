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
    private final boolean generateBidRequestId;
    private final BidRequest defaultBidRequest;
    private final ApplicationSettings applicationSettings;
    private final IdGenerator idGenerator;
    private final Metrics metrics;
    private final TimeoutFactory timeoutFactory;
    private final JacksonMapper mapper;
    private final JsonMerger jsonMerger;

    private StoredRequestProcessor(long defaultTimeout,
                                   BidRequest defaultBidRequest,
                                   boolean generateBidRequestId,
                                   ApplicationSettings applicationSettings,
                                   IdGenerator idGenerator,
                                   Metrics metrics,
                                   TimeoutFactory timeoutFactory,
                                   JacksonMapper mapper,
                                   JsonMerger jsonMerger) {

        this.defaultTimeout = defaultTimeout;
        this.defaultBidRequest = defaultBidRequest;
        this.generateBidRequestId = generateBidRequestId;
        this.applicationSettings = applicationSettings;
        this.timeoutFactory = timeoutFactory;
        this.idGenerator = idGenerator;
        this.metrics = metrics;
        this.mapper = mapper;
        this.jsonMerger = jsonMerger;
    }

    public static StoredRequestProcessor create(long defaultTimeout,
                                                String defaultBidRequestPath,
                                                boolean generateBidRequestId,
                                                FileSystem fileSystem,
                                                ApplicationSettings applicationSettings,
                                                IdGenerator idGenerator,
                                                Metrics metrics,
                                                TimeoutFactory timeoutFactory,
                                                JacksonMapper mapper,
                                                JsonMerger jsonMerger) {

        return new StoredRequestProcessor(
                defaultTimeout,
                readBidRequest(
                        defaultBidRequestPath, Objects.requireNonNull(fileSystem), Objects.requireNonNull(mapper)),
                generateBidRequestId,
                Objects.requireNonNull(applicationSettings),
                Objects.requireNonNull(idGenerator),
                Objects.requireNonNull(metrics),
                Objects.requireNonNull(timeoutFactory),
                Objects.requireNonNull(mapper),
                Objects.requireNonNull(jsonMerger));
    }

    /**
     * Runs a stored request processing: gather stored request ids from {@link BidRequest} and its {@link Imp}s,
     * fetches json bodies from source by stored request ids and doing merge between original httpRequest value and
     * fetched jsons from source. In case any error happen during the process, returns failedFuture with
     * InvalidRequestException {@link InvalidRequestException} as cause.
     */
    public Future<BidRequest> processStoredRequests(String accountId, BidRequest bidRequest) {
        final Map<BidRequest, String> bidRequestToStoredRequestId;
        final Map<Imp, String> impToStoredRequestId;
        try {
            bidRequestToStoredRequestId = mapStoredRequestHolderToStoredRequestId(
                    Collections.singletonList(bidRequest), this::getStoredRequestFromBidRequest);

            impToStoredRequestId = mapStoredRequestHolderToStoredRequestId(
                    bidRequest.getImp(), this::getStoredRequestFromImp);
        } catch (InvalidRequestException e) {
            return Future.failedFuture(e);
        }

        final Set<String> requestIds = new HashSet<>(bidRequestToStoredRequestId.values());
        final Set<String> impIds = new HashSet<>(impToStoredRequestId.values());
        if (requestIds.isEmpty() && impIds.isEmpty()) {
            return Future.succeededFuture(bidRequest);
        }

        final Future<StoredDataResult> storedDataFuture =
                applicationSettings.getStoredData(accountId, requestIds, impIds, timeout(bidRequest))
                        .compose(storedDataResult -> updateMetrics(storedDataResult, requestIds, impIds));

        return storedRequestsToBidRequest(
                storedDataFuture, bidRequest, bidRequestToStoredRequestId.get(bidRequest), impToStoredRequestId)
                .map(this::generateBidRequestIdForApp);
    }

    /**
     * Fetches AMP request from the source.
     */
    public Future<BidRequest> processAmpRequest(String accountId, String ampRequestId) {
        final BidRequest bidRequest = defaultBidRequest != null ? defaultBidRequest : BidRequest.builder().build();
        final Future<StoredDataResult> ampStoredDataFuture =
                applicationSettings.getAmpStoredData(
                        accountId, Collections.singleton(ampRequestId), Collections.emptySet(), timeout(bidRequest))
                        .compose(storedDataResult -> updateMetrics(
                                storedDataResult, Collections.singleton(ampRequestId), Collections.emptySet()));

        return storedRequestsToBidRequest(ampStoredDataFuture, bidRequest, ampRequestId, Collections.emptyMap())
                .map(this::generateBidRequestId);
    }

    /**
     * Fetches stored request.video and map existing values to imp.id.
     */
    Future<VideoStoredDataResult> videoStoredDataResult(String accountId, List<Imp> imps, List<String> errors,
                                                        Timeout timeout) {
        final Map<String, String> storedIdToImpId =
                mapStoredRequestHolderToStoredRequestId(imps, this::getStoredRequestFromImp)
                        .entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getValue,
                                impIdToStoredId -> impIdToStoredId.getKey().getId()));

        return applicationSettings.getStoredData(accountId, Collections.emptySet(), storedIdToImpId.keySet(), timeout)
                .map(storedDataResult -> makeVideoStoredDataResult(storedDataResult, storedIdToImpId, errors));
    }

    private Future<StoredDataResult> updateMetrics(StoredDataResult storedDataResult, Set<String> requestIds,
                                                   Set<String> impIds) {
        requestIds.forEach(
                id -> metrics.updateStoredRequestMetric(storedDataResult.getStoredIdToRequest().containsKey(id)));
        impIds.forEach(id -> metrics.updateStoredImpsMetric(storedDataResult.getStoredIdToImp().containsKey(id)));

        return Future.succeededFuture(storedDataResult);
    }

    private static BidRequest readBidRequest(String defaultBidRequestPath, FileSystem fileSystem,
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
                .recover(exception -> Future.failedFuture(new InvalidRequestException(
                        String.format("Stored request fetching failed: %s", exception.getMessage()))))
                .compose(result -> !result.getErrors().isEmpty()
                        ? Future.failedFuture(new InvalidRequestException(result.getErrors()))
                        : Future.succeededFuture(result))
                .map(result -> mergeBidRequestAndImps(bidRequest, storedBidRequestId,
                        impsToStoredRequestId, result));
    }

    /**
     * Runs {@link BidRequest} and {@link Imp}s merge processes.
     */
    private BidRequest mergeBidRequestAndImps(BidRequest bidRequest,
                                              String storedRequestId,
                                              Map<Imp, String> impToStoredId,
                                              StoredDataResult storedDataResult) {

        return mergeBidRequestImps(
                mergeBidRequest(mergeDefaultRequest(bidRequest), storedRequestId, storedDataResult),
                impToStoredId,
                storedDataResult);
    }

    private BidRequest mergeDefaultRequest(BidRequest bidRequest) {
        return jsonMerger.merge(bidRequest, defaultBidRequest, BidRequest.class);
    }

    /**
     * Merges original request with request from stored request source. Values from original request
     * has higher priority than stored request values.
     */
    private BidRequest mergeBidRequest(BidRequest originalRequest, String storedRequestId,
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
    private BidRequest mergeBidRequestImps(BidRequest bidRequest, Map<Imp, String> impToStoredId,
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
    private <K> Map<K, String> mapStoredRequestHolderToStoredRequestId(
            List<K> storedRequestHolders, Function<K, ExtStoredRequest> storedRequestExtractor) {

        if (CollectionUtils.isEmpty(storedRequestHolders)) {
            return Collections.emptyMap();
        }

        final Map<K, String> holderToPreBidRequest = new HashMap<>();
        final List<String> errors = new ArrayList<>();

        for (K storedRequestHolder : storedRequestHolders) {
            final ExtStoredRequest extStoredRequest = storedRequestExtractor.apply(storedRequestHolder);

            if (extStoredRequest != null) {
                final String storedRequestId = extStoredRequest.getId();

                if (storedRequestId != null) {
                    holderToPreBidRequest.put(storedRequestHolder, storedRequestId);
                } else {
                    errors.add("Id is not found in storedRequest");
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new InvalidRequestException(errors);
        }

        return holderToPreBidRequest;
    }

    /**
     * Extracts {@link ExtStoredRequest} from {@link BidRequest} if exists.
     */
    private ExtStoredRequest getStoredRequestFromBidRequest(BidRequest bidRequest) {
        final ExtRequest ext = bidRequest.getExt();
        if (ext != null) {
            final ExtRequestPrebid prebid = ext.getPrebid();
            if (prebid != null) {
                return prebid.getStoredrequest();
            }
        }
        return null;
    }

    /**
     * Extracts {@link ExtStoredRequest} from {@link Imp} if exists. In case when Extension has invalid
     * format throws {@link InvalidRequestException}.
     */
    private ExtStoredRequest getStoredRequestFromImp(Imp imp) {
        if (imp.getExt() != null) {
            try {
                final ExtImp extImp = mapper.mapper().treeToValue(imp.getExt(), ExtImp.class);
                final ExtImpPrebid prebid = extImp.getPrebid();
                if (prebid != null) {
                    return prebid.getStoredrequest();
                }
            } catch (JsonProcessingException e) {
                throw new InvalidRequestException(String.format(
                        "Incorrect Imp extension format for Imp with id %s: %s", imp.getId(), e.getMessage()));
            }
        }
        return null;
    }

    /**
     * If the request defines tmax explicitly, then it is returned as is. Otherwise default timeout is returned.
     */
    private Timeout timeout(BidRequest bidRequest) {
        final Long tmax = bidRequest.getTmax();
        return timeoutFactory.create(tmax != null && tmax > 0 ? tmax : defaultTimeout);
    }
}
