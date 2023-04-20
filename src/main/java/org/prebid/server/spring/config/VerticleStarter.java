package org.prebid.server.spring.config;

import org.prebid.server.vertx.verticles.VerticleDefinition;
import org.prebid.server.vertx.verticles.VerticleDeployer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.util.List;

@Configuration
public class VerticleStarter {

    @Autowired
    private VerticleDeployer deployer;

    @Autowired
    private List<VerticleDefinition> verticleDefinitions;

    @PostConstruct
    public void startVerticles() {
        verticleDefinitions.forEach(deployer::deploy);
    }
}
