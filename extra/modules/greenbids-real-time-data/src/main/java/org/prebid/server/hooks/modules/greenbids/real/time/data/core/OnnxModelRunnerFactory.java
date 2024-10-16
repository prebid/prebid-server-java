package org.prebid.server.hooks.modules.greenbids.real.time.data.core;

import ai.onnxruntime.OrtException;

public class OnnxModelRunnerFactory {

    public OnnxModelRunner create(byte[] bytes) throws OrtException {
        return new OnnxModelRunner(bytes);
    }
}
