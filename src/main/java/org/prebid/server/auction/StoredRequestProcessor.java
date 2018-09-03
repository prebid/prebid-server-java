package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.jsonpatch.JsonPatchException;
import com.github.fge.jsonpatch.mergepatch.JsonMergePatch;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.prebid.server.auction.model.StoredRequestResult;
import org.prebid.server.auction.model.Tuple2;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.proto.openrtb.ext.request.ExtBidRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtImp;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredRequest;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.StoredDataResult;

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
     * fetched jsons from source. In case any error happen during the process {@link BidRequest}, returns failedFuture
     * with InvalidRequestException {@link InvalidRequestException} as cause. In case of error in {@link Imp} process,
     * add invalid impression with fail error message to {@link StoredRequestResult} map and removes failed imp from
     * request.
     */
    Future<StoredRequestResult> processStoredRequests(BidRequest bidRequest) {
        final Map<BidRequest, String> bidRequestToStoredRequestId;
        final Tuple2<Map<Imp, String>, Map<Imp, String>> impToStoredRequestIdWithErrors;

        try {
            bidRequestToStoredRequestId = getBidRequestStoredRequestIds(bidRequest);

            impToStoredRequestIdWithErrors = mapStoredRequestHolderToStoredRequestId(
                    bidRequest.getImp(), StoredRequestProcessor::getStoredRequestFromImp);
        } catch (InvalidRequestException e) {
            return Future.failedFuture(e);
        }

        final Map<Imp, String> impToStoredRequestId = impToStoredRequestIdWithErrors.getLeft();
        final Map<Imp, String> impToError = impToStoredRequestIdWithErrors.getRight();
        final Set<String> requestIds = new HashSet<>(bidRequestToStoredRequestId.values());
        final Set<String> impIds = new HashSet<>(impToStoredRequestId.values());

        if (requestIds.isEmpty() && impIds.isEmpty()) {
            return createIdsNotFoundResult(bidRequest, impToError);
        }

        return storedRequestsToBidRequest(applicationSettings.getStoredData(requestIds, impIds, timeout(bidRequest)),
                bidRequest, bidRequestToStoredRequestId.get(bidRequest), impToStoredRequestId, impToError);
    }

    /**
     * Maps {@link BidRequest} to its stored request id. Throws {@link InvalidRequestException} if any errors
     * occurred during process.
     */
    private Map<BidRequest, String> getBidRequestStoredRequestIds(BidRequest bidRequest) {
        Tuple2<Map<BidRequest, String>, Map<BidRequest, String>> bidRequestToStoredRequestId;
        bidRequestToStoredRequestId = mapStoredRequestHolderToStoredRequestId(
                Collections.singletonList(bidRequest), StoredRequestProcessor::getStoredRequestFromBidRequest);

        final List<String> bidRequestErrors = new ArrayList<>(bidRequestToStoredRequestId.getRight().values());
        if (CollectionUtils.isNotEmpty(bidRequestErrors)) {
            throw new InvalidRequestException(bidRequestErrors);
        }
        return bidRequestToStoredRequestId.getLeft();
    }

    /**
     * Creates {@link StoredRequestResult} when stored request ids were not found for {@link BidRequest} and
     * {@link Imp}. Removes from {@link BidRequest} all {@link Imp}s for which error occurred during mapping stored
     * request ids. If there no imps processed without errors, failed {@link Future} with
     * {@link InvalidRequestException} returns.
     */
    private Future<StoredRequestResult> createIdsNotFoundResult(BidRequest bidRequest,
                                                                Map<Imp, String> impToErrors) {
        if (MapUtils.isNotEmpty(impToErrors)) {
            final List<Imp> imps = new ArrayList<>(bidRequest.getImp());
            imps.removeAll(impToErrors.keySet());
            if (imps.isEmpty()) {
                final List<String> errors = new ArrayList<>(impToErrors.values());
                errors.add("request.imp must contain at least one valid imp.");
                return Future.failedFuture(new InvalidRequestException(errors));
            }
            bidRequest.toBuilder().imp(imps).build();
        }
        return Future.succeededFuture(StoredRequestResult.of(bidRequest, impToErrors));
    }

    /**
     * Fetches AMP request from the source.
     */
    Future<BidRequest> processAmpRequest(String ampRequestId) {
        final BidRequest bidRequest = BidRequest.builder().build();

        return storedRequestsToBidRequest(
                applicationSettings.getAmpStoredData(Collections.singleton(ampRequestId), Collections.emptySet(),
                        timeout(bidRequest)), bidRequest, ampRequestId, Collections.emptyMap(), Collections.emptyMap())
                .map(StoredRequestResult::getBidRequest);
    }

    /**
     * Returns failed {@link Future} when any errors occurred during stored request fetching and initiates json
     * merging process for {@link BidRequest} and {@link Imp}s there are no errors.
     */
    private Future<StoredRequestResult> storedRequestsToBidRequest(Future<StoredDataResult> storedDataFuture,
                                                                   BidRequest bidRequest, String storedBidRequestId,
                                                                   Map<Imp, String> impsToStoredRequestId,
                                                                   Map<Imp, String> impToError) {
        return storedDataFuture
                .recover(exception -> Future.failedFuture(new InvalidRequestException(
                        String.format("Stored request fetching failed with exception message: %s",
                                exception.getMessage()))))
                .map(result -> mergeBidRequestAndImps(bidRequest, storedBidRequestId, impsToStoredRequestId, impToError,
                        result));
    }

    /**
     * Runs {@link BidRequest} and {@link Imp}s merge processes.
     */
    private StoredRequestResult mergeBidRequestAndImps(BidRequest bidRequest, String storedRequestId,
                                                       Map<Imp, String> impToStoredId,
                                                       Map<Imp, String> impToErrors,
                                                       StoredDataResult storedDataResult) {
        return mergeBidRequestImps(mergeBidRequest(bidRequest, storedRequestId, storedDataResult), impToStoredId,
                impToErrors, storedDataResult);
    }

    /**
     * Merges {@link BidRequest} from original request with request from stored request source. Values from
     * original request has higher priority than stored request values.
     */
    private BidRequest mergeBidRequest(BidRequest bidRequest, String storedRequestId,
                                       StoredDataResult storedDataResult) {
        final Map<String, String> storedIdToRequest = storedDataResult.getStoredIdToRequest();
        return storedRequestId != null && MapUtils.isNotEmpty(storedIdToRequest)
                && storedIdToRequest.get(storedRequestId) != null
                ? merge(bidRequest, storedIdToRequest, storedRequestId, BidRequest.class)
                : bidRequest;
    }

    /**
     * Merges {@link Imp}s from original request with Imps from stored request source. Values from original request
     * has higher priority than stored request values.
     */
    private StoredRequestResult mergeBidRequestImps(BidRequest bidRequest,
                                                    Map<Imp, String> impToStoredId,
                                                    Map<Imp, String> impToError,
                                                    StoredDataResult storedDataResult) {
        if (impToStoredId.isEmpty()) {
            return StoredRequestResult.of(bidRequest, Collections.emptyMap());
        }
        final Map<String, String> storedIdToImp = storedDataResult.getStoredIdToImp();
        final List<Imp> mergedImps = new ArrayList<>(bidRequest.getImp());
        for (int i = 0; i < mergedImps.size(); i++) {
            final Imp imp = mergedImps.get(i);
            final String storedRequestId = impToStoredId.get(imp);

            if (storedRequestId != null) {
                if (storedIdToImp.get(storedRequestId) == null) {
                    impToError.put(imp, String.format("No config found for id: %s", storedRequestId));
                    continue;
                }
                final Imp mergedImp;
                try {
                    mergedImp = merge(imp, storedIdToImp, storedRequestId, Imp.class);
                } catch (InvalidRequestException ex) {
                    impToError.put(imp, ex.getMessage());
                    continue;
                }
                mergedImps.set(i, mergedImp);
            }
        }

        mergedImps.removeAll(impToError.keySet());
        if (mergedImps.isEmpty()) {
            throw new InvalidRequestException(new ArrayList<>(impToError.values()));
        }

        return StoredRequestResult.of(bidRequest.toBuilder().imp(mergedImps).build(), impToError);
    }

    /**
     * Merges passed object with json retrieved from stored data map by id
     * and cast it to appropriate class. In case of any exception during merging, throws {@link InvalidRequestException}
     * with reason message.
     */

    private <T> T merge(T originalObject, Map<String, String> storedData, String id, Class<T> classToCast) {
        final JsonNode originJsonNode = Json.mapper.valueToTree(originalObject);
        final JsonNode storedRequestJsonNode;
        try {
            storedRequestJsonNode = Json.mapper.readTree(storedData.get(id));
        } catch (IOException e) {
            throw new InvalidRequestException(
                    String.format("Can't parse Json for stored request with id %s", id));
        }
        try {
            // Http request fields have higher priority and will override fields from stored requests
            // in case they have different values
            return Json.mapper.treeToValue(JsonMergePatch.fromJson(originJsonNode).apply(storedRequestJsonNode),
                    classToCast);
        } catch (JsonPatchException e) {
            throw new InvalidRequestException(String.format(
                    "Couldn't create merge patch from origin object node for id %s: %s", id, e.getMessage()));
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException(
                    String.format("Can't convert merging result for id %s: %s", id, e.getMessage()));
        }
    }

    /**
     * Maps object to its StoredRequestId if exists. If object's extension contains storedRequest field, expected that
     * it includes id too, in another case error about missed id in stored request will be added to error list.
     * Gathers all errors into list, and in case if it is not empty, throws {@link InvalidRequestException} with
     * list of errors.
     */
    private <K> Tuple2<Map<K, String>, Map<K, String>> mapStoredRequestHolderToStoredRequestId(
            List<K> storedRequestHolders, Function<K, ExtStoredRequest> storedRequestExtractor) {

        if (CollectionUtils.isEmpty(storedRequestHolders)) {
            return Tuple2.of(Collections.emptyMap(), Collections.emptyMap());
        }

        final Map<K, String> holderToPreBidRequest = new HashMap<>();
        final Map<K, String> holderToError = new HashMap<>();

        for (K storedRequestHolder : storedRequestHolders) {
            final ExtStoredRequest extStoredRequest;
            try {
                extStoredRequest = storedRequestExtractor.apply(storedRequestHolder);
            } catch (PreBidException ex) {
                holderToError.put(storedRequestHolder, ex.getMessage());
                continue;
            }

            if (extStoredRequest != null) {
                final String storedRequestId = extStoredRequest.getId();

                if (storedRequestId != null) {
                    holderToPreBidRequest.put(storedRequestHolder, storedRequestId);
                } else {
                    holderToError.put(storedRequestHolder, "Id is not found in storedRequest");
                }
            }
        }
        return Tuple2.of(holderToPreBidRequest, holderToError);
    }

    /**
     * Extracts {@link ExtStoredRequest} from {@link BidRequest} if exists. In case when Extension has invalid
     * format throws {@link InvalidRequestException}
     */
    private static ExtStoredRequest getStoredRequestFromBidRequest(BidRequest bidRequest) {
        final ObjectNode ext = bidRequest.getExt();
        if (ext != null) {
            try {
                final ExtBidRequest extBidRequest = Json.mapper.treeToValue(ext, ExtBidRequest.class);
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
                throw new PreBidException(String.format(
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
