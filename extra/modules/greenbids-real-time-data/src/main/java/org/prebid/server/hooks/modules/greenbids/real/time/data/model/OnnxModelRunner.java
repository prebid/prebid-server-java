package org.prebid.server.hooks.modules.greenbids.real.time.data.model;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import java.util.Collections;

public class OnnxModelRunner {
    private OrtSession session;
    private OrtEnvironment environment;

    public OnnxModelRunner(String modelPath) throws OrtException {
        environment = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        session = environment.createSession(modelPath, options);
    }

    public OrtSession.Result runModel(String[][] throttlingInferenceRow) throws OrtException {
        OnnxTensor inputTensor = OnnxTensor.createTensor(OrtEnvironment.getEnvironment(), throttlingInferenceRow);
        return session.run(Collections.singletonMap("input", inputTensor));
    }
}
