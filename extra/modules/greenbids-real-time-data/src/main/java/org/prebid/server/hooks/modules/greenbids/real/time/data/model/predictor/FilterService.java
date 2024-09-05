package org.prebid.server.hooks.modules.greenbids.real.time.data.model.predictor;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.data.ThrottlingMessage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class FilterService {

    public Map<String, Map<String, Boolean>> filterBidders(
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

        extractProbabilitiesTensors(results).forEach(tensor -> {
            try {
                final float[][] probabilities = extractProbabilitiesValues(tensor);
                processProbabilities(probabilities, throttlingMessages, threshold, impsBiddersFilterMap);
            } catch (OrtException e) {
                throw new PreBidException("Exception when extracting proba from OnnxTensor: ", e);
            }
        });
    }

    private Stream<OnnxTensor> extractProbabilitiesTensors(OrtSession.Result results) {
        return StreamSupport.stream(results.spliterator(), false)
                .filter(onnxItem -> Objects.equals(onnxItem.getKey(), "probabilities"))
                .map(onnxItem -> (OnnxTensor) onnxItem.getValue());
    }

    private float[][] extractProbabilitiesValues(OnnxTensor tensor) throws OrtException {
        return (float[][]) tensor.getValue();
    }

    private void processProbabilities(
            float[][] probabiliies,
            List<ThrottlingMessage> throttlingMessages,
            Double threshold,
            Map<String, Map<String, Boolean>> impsBiddersFilterMap) {

        IntStream.range(0, probabiliies.length)
                .mapToObj(i -> createEntry(throttlingMessages.get(i), probabiliies[i][1], threshold))
                .forEach(entry -> impsBiddersFilterMap
                        .computeIfAbsent(entry.getKey(), k -> new HashMap<>())
                        .put(entry.getValue().getKey(), entry.getValue().getValue()));
    }

    private Map.Entry<String, Map.Entry<String, Boolean>> createEntry(
            ThrottlingMessage message,
            float probability,
            Double threshold) {

        final String impId = message.getAdUnitCode();
        final String bidder = message.getBidder();
        final boolean isKeptInAuction = probability > threshold;
        return Map.entry(impId, Map.entry(bidder, isKeptInAuction));
    }
}
