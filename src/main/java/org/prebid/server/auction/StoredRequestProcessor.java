package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonpatch.JsonPatchException;
import com.github.fge.jsonpatch.mergepatch.JsonMergePatch;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.proto.openrtb.ext.request.ExtBidRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtImp;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredRequest;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.StoredRequestResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Executes stored request processing
 */
public class StoredRequestProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StoredRequestProcessor.class);

    private final ApplicationSettings applicationSettings;
    private final TimeoutFactory timeoutFactory;
    private final long defaultTimeout;

    public StoredRequestProcessor(ApplicationSettings applicationSettings, TimeoutFactory timeoutFactory,
                                  long defaultTimeout) {
        this.applicationSettings = Objects.requireNonNull(applicationSettings);
        this.timeoutFactory = Objects.requireNonNull(timeoutFactory);
        this.defaultTimeout = defaultTimeout;
    }

    /**
     * Runs a stored request processing: gather stored request ids from {@link BidRequest} and its {@link Imp}s,
     * fetches json bodies from source by stored request ids and doing merge between original httpRequest value and
     * fetched jsons from source. In case any error happen during the process, returns failedFuture with
     * InvalidRequestException {@link InvalidRequestException} as cause.
     */
    public Future<BidRequest> processStoredRequests(BidRequest bidRequest) {
        final Map<Imp, String> impsToStoredRequestId;
        final Map<BidRequest, String> bidRequestToStoredRequestId;
        try {
            bidRequestToStoredRequestId = mapStoredRequestHolderToStoredRequestId(Collections.singletonList(bidRequest),
                    StoredRequestProcessor::getStoredRequestFromBidRequest);
            impsToStoredRequestId = mapStoredRequestHolderToStoredRequestId(bidRequest.getImp(),
                    StoredRequestProcessor::getStoredRequestFromImp);
        } catch (InvalidRequestException exception) {
            return Future.failedFuture(exception);
        }

        final Set<String> storedRequestIds = new HashSet<>(impsToStoredRequestId.values());
        storedRequestIds.addAll(bidRequestToStoredRequestId.values());
        if (storedRequestIds.isEmpty()) {
            return Future.succeededFuture(bidRequest);
        }

        return storedRequestsToBidRequest(
                applicationSettings.getStoredRequestsById(storedRequestIds, timeout(bidRequest)),
                bidRequest, bidRequestToStoredRequestId.get(bidRequest), impsToStoredRequestId);
    }

    /**
     * Fetches AMP request from the source.
     */
    public Future<BidRequest> processAmpRequest(String ampRequestId) {
        final BidRequest emptyBidRequest = BidRequest.builder().build();

        return storedRequestsToBidRequest(
                applicationSettings.getStoredRequestsByAmpId(Collections.singleton(ampRequestId),
                        timeout(emptyBidRequest)),
                emptyBidRequest, ampRequestId, Collections.emptyMap());
    }

    private Future<BidRequest> storedRequestsToBidRequest(Future<StoredRequestResult> storedRequestsFuture,
                                                          BidRequest bidRequest, String storedBidRequestId,
                                                          Map<Imp, String> impsToStoredRequestId) {
        return storedRequestsFuture
                .recover(exception -> Future.failedFuture(new InvalidRequestException(
                        String.format("Stored request fetching failed with exception: %s", exception))))
                .compose(storedRequestResult -> storedRequestResult.getErrors().size() > 0
                        ? Future.failedFuture(new InvalidRequestException(storedRequestResult.getErrors()))
                        : Future.succeededFuture(storedRequestResult))
                .map(storedRequestResult -> mergeBidRequestAndImps(bidRequest, storedBidRequestId,
                        impsToStoredRequestId, storedRequestResult));
    }

    /**
     * Runs {@link BidRequest} and {@link Imp}s merge processes.
     */
    private BidRequest mergeBidRequestAndImps(BidRequest bidRequest, String storedRequestId,
                                              Map<Imp, String> impToStoredId, StoredRequestResult storedRequestResult) {
        return mergeBidRequestImps(mergeBidRequest(bidRequest, storedRequestId, storedRequestResult), impToStoredId,
                storedRequestResult);
    }

    /**
     * Merges {@link BidRequest} from original request with request from stored request source. Values from
     * original request has higher priority than stored request values.
     */
    private BidRequest mergeBidRequest(BidRequest bidRequest, String storedRequestId,
                                       StoredRequestResult storedRequestResult) {
        if (storedRequestId != null) {
            return merge(bidRequest, storedRequestId, storedRequestResult, BidRequest.class);
        }
        return bidRequest;
    }

    /**
     * Merges {@link Imp}s from original request with Imps from stored request source. Values from original request
     * has higher priority than stored request values.
     */
    private BidRequest mergeBidRequestImps(BidRequest bidRequest, Map<Imp, String> impToStoredId,
                                           StoredRequestResult storedRequestResult) {
        if (impToStoredId.isEmpty()) {
            return bidRequest;
        }
        final List<Imp> mergedImps = new ArrayList<>(bidRequest.getImp());
        for (int i = 0; i < mergedImps.size(); i++) {
            final Imp imp = mergedImps.get(i);
            final String storedRequestId = impToStoredId.get(imp);
            if (storedRequestId != null) {
                final Imp mergedImp = merge(imp, storedRequestId, storedRequestResult, Imp.class);
                mergedImps.set(i, mergedImp);
            }
        }
        return bidRequest.toBuilder().imp(mergedImps).build();
    }


    /**
     * Merges passed object with json retrieved from {@link StoredRequestResult} by storedRequest id
     * and cast it to appropriate class. In case of any exception during merging, throws {@link InvalidRequestException}
     * with reason message.
     */
    private <T> T merge(T originalObject, String storedRequestId, StoredRequestResult storedRequestResult,
                        Class<T> classToCast) {
        final JsonNode originJsonNode = Json.mapper.valueToTree(originalObject);
        final JsonNode storedRequestJsonNode;
        try {
            storedRequestJsonNode = Json.mapper.readTree(storedRequestResult.getStoredIdToJson().get(storedRequestId));
        } catch (IOException e) {
            throw new InvalidRequestException(
                    String.format("Can't parse Json for stored request with id %s", storedRequestId));
        }
        try {
            // Http request fields have higher priority and will override fields from stored requests
            // in case they have different values
            return Json.mapper.treeToValue(JsonMergePatch.fromJson(originJsonNode).apply(storedRequestJsonNode),
                    classToCast);
        } catch (JsonPatchException e) {
            final String errorMessage = String.format(
                    "Couldn't create merge patch from origin object node for storedRequestId %s", storedRequestId);
            logger.warn(errorMessage);
            throw new InvalidRequestException(errorMessage);
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException(
                    String.format("Can't convert merging result for storedRequestId %s", storedRequestId));
        }
    }

    /**
     * Maps object to its StoredRequestId if exists. If object's extension contains storedRequest field, expected that
     * it includes id too, in another case error about missed id in stored request will be added to error list.
     * Gathers all errors into list, and in case if it is not empty, throws {@link InvalidRequestException} with
     * list of errors.
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

        if (errors.size() > 0) {
            throw new InvalidRequestException(errors);
        }

        return holderToPreBidRequest;
    }

    /**
     * Extracts {@link ExtStoredRequest} from {@link BidRequest} if exists. In case when Extension has invalid
     * format throws {@link InvalidRequestException}
     */
    private static ExtStoredRequest getStoredRequestFromBidRequest(BidRequest bidRequest) {
        if (bidRequest.getExt() != null) {
            try {
                final ExtBidRequest extBidRequest = Json.mapper.treeToValue(bidRequest.getExt(), ExtBidRequest.class);
                final ExtRequestPrebid prebid = extBidRequest.getPrebid();
                if (prebid != null) {
                    return prebid.getStoredrequest();
                }
            } catch (JsonProcessingException e) {
                throw new InvalidRequestException(
                        String.format("Incorrect bid request extension format for bidRequest with id %s",
                                bidRequest.getId()));
            }
        }
        return null;
    }

    /**
     * Extracts {@link ExtStoredRequest} from {@link Imp} if exists. In case when Extension has invalid
     * format throws {@link InvalidRequestException}
     */
    private static ExtStoredRequest getStoredRequestFromImp(Imp imp) {
        if (imp.getExt() != null) {
            try {
                final ExtImp extImp = Json.mapper.treeToValue(imp.getExt(), ExtImp.class);
                final ExtImpPrebid prebid = extImp.getPrebid();
                if (prebid != null) {
                    return prebid.getStoredrequest();
                }
            } catch (JsonProcessingException e) {
                throw new InvalidRequestException(
                        String.format("Incorrect Imp extension format for Imp with id %s", imp.getId()));
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
