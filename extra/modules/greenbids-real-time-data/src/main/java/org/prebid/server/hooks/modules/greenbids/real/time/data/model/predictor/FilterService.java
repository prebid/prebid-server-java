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
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class FilterService {

    public Map<String, Map<String, Boolean>> filterBidders(
            OnnxModelRunner onnxModelRunner,
            List<ThrottlingMessage> throttlingMessages,
            Double threshold) {

        final OrtSession.Result results;
        try {
            final String[][] throttlingInferenceRows = convertToArray(throttlingMessages);
            results = onnxModelRunner.runModel(throttlingInferenceRows);
            return processModelResults(results, throttlingMessages, threshold);
        } catch (OrtException e) {
            throw new PreBidException("Exception during model inference: ", e);
        }
    }

    private static String[][] convertToArray(List<ThrottlingMessage> messages) {
        return messages.stream()
                .map(message -> new String[]{
                        message.getBrowser(),
                        message.getBidder(),
                        message.getAdUnitCode(),
                        message.getCountry(),
                        message.getHostname(),
                        message.getDevice(),
                        message.getHourBucket(),
                        message.getMinuteQuadrant()})
                .toArray(String[][]::new);
    }

    private Map<String, Map<String, Boolean>> processModelResults(
            OrtSession.Result results,
            List<ThrottlingMessage> throttlingMessages,
            Double threshold) {

        return StreamSupport.stream(results.spliterator(), false)
                .filter(onnxItem -> Objects.equals(onnxItem.getKey(), "probabilities"))
                .map(onnxItem -> (OnnxTensor) onnxItem.getValue())
                .map(tensor -> extractAndProcessProbabilities(tensor, throttlingMessages, threshold))
                .flatMap(map -> map.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Map<String, Map<String, Boolean>> extractAndProcessProbabilities(
            OnnxTensor tensor,
            List<ThrottlingMessage> throttlingMessages,
            Double threshold) {
        try {
            final float[][] probabilities = extractProbabilitiesValues(tensor);
            return processProbabilities(probabilities, throttlingMessages, threshold);
        } catch (OrtException e) {
            throw new PreBidException("Exception when extracting proba from OnnxTensor: ", e);
        }
    }

    private float[][] extractProbabilitiesValues(OnnxTensor tensor) throws OrtException {
        return (float[][]) tensor.getValue();
    }

    private Map<String, Map<String, Boolean>> processProbabilities(
            float[][] probabilities,
            List<ThrottlingMessage> throttlingMessages,
            Double threshold) {
        Map<String, Map<String, Boolean>> result = new HashMap<>();

        for (int i = 0; i < probabilities.length; i++) {
            final ThrottlingMessage message = throttlingMessages.get(i);
            final String impId = message.getAdUnitCode();
            final String bidder = message.getBidder();
            final boolean isKeptInAuction = probabilities[i][1] > threshold;
            result.computeIfAbsent(impId, k -> new HashMap<>()).put(bidder, isKeptInAuction);
        }

        return result;
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
