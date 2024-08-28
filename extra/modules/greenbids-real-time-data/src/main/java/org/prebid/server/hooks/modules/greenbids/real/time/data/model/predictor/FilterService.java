package org.prebid.server.hooks.modules.greenbids.real.time.data.model.predictor;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.data.ThrottlingMessage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

public class FilterService {

    public Map<String, Map<String, Boolean>> runModeAndFilterBidders(
            OnnxModelRunner onnxModelRunner,
            List<ThrottlingMessage> throttlingMessages,
            String[][] throttlingInferenceRows,
            Double threshold) {
        final Map<String, Map<String, Boolean>> impsBiddersFilterMap = new HashMap<>();
        final OrtSession.Result results;
        try {
            results = onnxModelRunner.runModel(throttlingInferenceRows);
            processModelResults(results, throttlingMessages, threshold, impsBiddersFilterMap);
        } catch (OrtException e) {
            throw new PreBidException("Exception during model inference: ", e);
        }
        return impsBiddersFilterMap;
    }

    private void processModelResults(
            OrtSession.Result results,
            List<ThrottlingMessage> throttlingMessages,
            Double threshold,
            Map<String, Map<String, Boolean>> impsBiddersFilterMap) {
        StreamSupport.stream(results.spliterator(), false)
                .filter(onnxItem -> Objects.equals(onnxItem.getKey(), "probabilities"))
                .forEach(onnxItem -> {
                    final OnnxValue onnxValue = onnxItem.getValue();
                    final OnnxTensor tensor = (OnnxTensor) onnxValue;
                    try {
                        final float[][] probas = (float[][]) tensor.getValue();
                        IntStream.range(0, probas.length)
                                .mapToObj(i -> {
                                    final ThrottlingMessage message = throttlingMessages.get(i);
                                    final String impId = message.getAdUnitCode();
                                    final String bidder = message.getBidder();
                                    final boolean isKeptInAuction = probas[i][1] > threshold;
                                    return Map.entry(impId, Map.entry(bidder, isKeptInAuction));
                                })
                                .forEach(entry -> impsBiddersFilterMap
                                        .computeIfAbsent(entry.getKey(), k -> new HashMap<>())
                                        .put(entry.getValue().getKey(), entry.getValue().getValue()));
                    } catch (OrtException e) {
                        throw new PreBidException("Exception when extracting proba from OnnxTensor: ", e);
                    }
                });
    }
}
