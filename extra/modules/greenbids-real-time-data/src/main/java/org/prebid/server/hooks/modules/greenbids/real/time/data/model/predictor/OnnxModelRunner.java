package org.prebid.server.hooks.modules.greenbids.real.time.data.model.predictor;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import java.util.Collections;

public class OnnxModelRunner {

    private final OrtSession session;

    private final OrtEnvironment environment;

    public OnnxModelRunner(byte[] onnxModelBytes) throws OrtException {
        environment = OrtEnvironment.getEnvironment();
        final OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        session = environment.createSession(onnxModelBytes, options);
    }

    public OrtSession.Result runModel(String[][] throttlingInferenceRow) throws OrtException {
        final OnnxTensor inputTensor = OnnxTensor.createTensor(OrtEnvironment.getEnvironment(), throttlingInferenceRow);
        return session.run(Collections.singletonMap("input", inputTensor));
    }
}
