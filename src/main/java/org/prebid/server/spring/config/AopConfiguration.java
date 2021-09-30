package org.prebid.server.spring.config;

import io.vertx.core.Future;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.prebid.server.health.HealthMonitor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Configuration
public class AopConfiguration {

    @Bean
    HealthMonitor healthMonitor() {
        return new HealthMonitor();
    }

    @Aspect
    @Component
    static class HealthMonitorAspect {

        @Autowired
        HealthMonitor healthMonitor;

        @Around(value = "execution(* org.prebid.server.vertx.http.HttpClient.*(..)) "
                + "|| execution(* org.prebid.server.settings.ApplicationSettings.*(..)) "
                + "|| execution(* org.prebid.server.geolocation.GeoLocationService.*(..))")
        public Future<?> around(ProceedingJoinPoint joinPoint) {
            try {
                return ((Future<?>) joinPoint.proceed())
                        .map(this::handleSucceedRequest)
                        .recover(this::handleFailRequest);
            } catch (Throwable e) {
                throw new IllegalStateException("Error while processing health monitoring", e);
            }
        }

        private <T> Future<T> handleFailRequest(Throwable throwable) {
            healthMonitor.incTotal();
            return Future.failedFuture(throwable);
        }

        private <T> T handleSucceedRequest(T result) {
            healthMonitor.incTotal();
            healthMonitor.incSuccess();
            return result;
        }
    }
}
