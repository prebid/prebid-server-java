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

        return extractProbabilitiesTensors(results)
                .map(tensor -> {
                    try {
                        final float[][] probabilities = extractProbabilitiesValues(tensor);
                        return processProbabilities(probabilities, throttlingMessages, threshold);
                    } catch (OrtException e) {
                        throw new PreBidException("Exception when extracting proba from OnnxTensor: ", e);
                    }
                })
                .flatMap(map -> map.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Stream<OnnxTensor> extractProbabilitiesTensors(OrtSession.Result results) {
        return StreamSupport.stream(results.spliterator(), false)
                .filter(onnxItem -> Objects.equals(onnxItem.getKey(), "probabilities"))
                .map(onnxItem -> (OnnxTensor) onnxItem.getValue());
    }

    private float[][] extractProbabilitiesValues(OnnxTensor tensor) throws OrtException {
        return (float[][]) tensor.getValue();
    }

    private Map<String, Map<String, Boolean>> processProbabilities(
            float[][] probabiliies,
            List<ThrottlingMessage> throttlingMessages,
            Double threshold) {
        final Map<String, Map<String, Boolean>> impsBiddersFilterMap = new HashMap<>();
        IntStream.range(0, probabiliies.length)
                .mapToObj(i -> createEntry(throttlingMessages.get(i), probabiliies[i][1], threshold))
                .forEach(entry -> impsBiddersFilterMap
                        .computeIfAbsent(entry.getKey(), k -> new HashMap<>())
                        .put(entry.getValue().getKey(), entry.getValue().getValue()));
        return impsBiddersFilterMap;
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
