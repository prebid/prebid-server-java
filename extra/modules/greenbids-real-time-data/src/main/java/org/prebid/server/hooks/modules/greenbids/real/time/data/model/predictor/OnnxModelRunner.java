package org.prebid.server.hooks.modules.greenbids.real.time.data.model.predictor;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import java.util.Collections;

public class OnnxModelRunner {

    private static final OrtEnvironment ENVIRONMENT = OrtEnvironment.getEnvironment();

    private final OrtSession session;

    public OnnxModelRunner(byte[] onnxModelBytes) throws OrtException {
        session = ENVIRONMENT.createSession(onnxModelBytes, new OrtSession.SessionOptions());
    }

    public OrtSession.Result runModel(String[][] throttlingInferenceRow) throws OrtException {
        final OnnxTensor inputTensor = OnnxTensor.createTensor(OrtEnvironment.getEnvironment(), throttlingInferenceRow);
        return session.run(Collections.singletonMap("input", inputTensor));
    }
}
