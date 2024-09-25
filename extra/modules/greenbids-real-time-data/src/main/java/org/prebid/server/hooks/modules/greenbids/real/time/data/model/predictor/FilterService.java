package org.prebid.server.hooks.modules.greenbids.real.time.data.model.predictor;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.data.ThrottlingMessage;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
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

        validateThrottlingMessages(throttlingMessages);

        return StreamSupport.stream(results.spliterator(), false)
                .filter(onnxItem -> {
                    validateOnnxTensor(onnxItem);
                    return Objects.equals(onnxItem.getKey(), "probabilities");
                })
                .map(onnxItem -> (OnnxTensor) onnxItem.getValue())
                .map(tensor -> {
                    validateTensorSize(tensor, throttlingMessages.size());
                    return extractAndProcessProbabilities(tensor, throttlingMessages, threshold);
                })
                .flatMap(map -> map.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private void validateThrottlingMessages(List<ThrottlingMessage> throttlingMessages) {
        if (throttlingMessages == null || CollectionUtils.isEmpty(throttlingMessages)) {
            throw new PreBidException("throttlingMessages cannot be null or empty");
        }
    }

    private void validateOnnxTensor(Map.Entry<String, OnnxValue> onnxItem) {
        if (!(onnxItem.getValue() instanceof OnnxTensor)) {
            throw new PreBidException("Expected OnnxTensor for 'probabilities', but found: "
                    + onnxItem.getValue().getClass().getName());
        }
    }

    private void validateTensorSize(OnnxTensor tensor, int expectedSize) {
        final long[] tensorShape = tensor.getInfo().getShape();
        if (tensorShape.length == 0 || tensorShape[0] != expectedSize) {
            throw new PreBidException("Mismatch between tensor size and throttlingMessages size");
        }
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

        final Map<String, Map<String, Boolean>> result = new HashMap<>();

        for (int i = 0; i < probabilities.length; i++) {
            final ThrottlingMessage message = throttlingMessages.get(i);
            final String impId = message.getAdUnitCode();
            final String bidder = message.getBidder();
            final boolean isKeptInAuction = probabilities[i][1] > threshold;
            result.computeIfAbsent(impId, k -> new HashMap<>()).put(bidder, isKeptInAuction);
        }

        return result;
    }
}
