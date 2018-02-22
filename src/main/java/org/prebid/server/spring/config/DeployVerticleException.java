package org.prebid.server.spring.config;

import org.springframework.boot.ExitCodeGenerator;

public class DeployVerticleException extends RuntimeException implements ExitCodeGenerator {

    public DeployVerticleException(String message) {
        super(message);
    }

    public DeployVerticleException(Throwable cause) {
        super(cause);
    }

    @Override
    public int getExitCode() {
        return 10;
    }
}
