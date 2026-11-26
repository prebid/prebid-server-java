package org.prebid.server.it;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.matching.ContentPattern;
import com.github.tomakehurst.wiremock.matching.EqualToJsonPattern;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.github.tomakehurst.wiremock.verification.NearMiss;
import net.javacrumbs.jsonunit.core.Configuration;
import net.javacrumbs.jsonunit.core.Option;
import net.javacrumbs.jsonunit.core.internal.Diff;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.math.BigDecimal;
import java.util.List;

public class WireMockAfterTestDiagnostics implements AfterTestExecutionCallback {

    private final WireMockExtension wireMock;

    public WireMockAfterTestDiagnostics(WireMockExtension wireMock) {
        this.wireMock = wireMock;
    }

    @Override
    public void afterTestExecution(ExtensionContext context) {
        context.getExecutionException().ifPresent(this::addDiagnostics);
    }

    private void addDiagnostics(Throwable failure) {
        try {
            wireMock.findAllUnmatchedRequests()
                    .forEach(request -> addRequestDiagnostics(request, failure));
        } catch (RuntimeException e) {
            failure.addSuppressed(e);
        }
    }

    private void addRequestDiagnostics(LoggedRequest request, Throwable failure) {
        try {
            final List<NearMiss> nearMisses = wireMock.findNearMissesFor(request);
            if (!nearMisses.isEmpty()) {
                compareRequestBody(nearMisses.getFirst(), failure);
            }
        } catch (RuntimeException e) {
            failure.addSuppressed(e);
        }
    }

    private static void compareRequestBody(NearMiss nearMiss, Throwable failure) {
        final List<ContentPattern<?>> patterns = nearMiss.getStubMapping()
                .getRequest()
                .getBodyPatterns();

        if (patterns == null) {
            return;
        }

        for (ContentPattern<?> pattern : patterns) {
            if (pattern instanceof EqualToJsonPattern jsonPattern) {
                compareJson(nearMiss.getRequest(), jsonPattern, failure);
            }
        }
    }

    private static void compareJson(LoggedRequest request,
                                    EqualToJsonPattern pattern,
                                    Throwable failure) {

        final Diff diff = Diff.create(
                pattern.getExpected(),
                request.getBodyAsString(),
                "actual",
                "",
                makeConfiguration(pattern));

        if (!diff.similar()) {
            failure.addSuppressed(new AssertionError(
                    "WireMock request body mismatch: %s %s%n%s".formatted(
                            request.getMethod(),
                            request.getUrl(),
                            diff.differences())));
        }
    }

    private static Configuration makeConfiguration(EqualToJsonPattern pattern) {
        Configuration configuration = Configuration.empty().withTolerance(BigDecimal.ZERO);
        if (Boolean.TRUE.equals(pattern.isIgnoreArrayOrder())) {
            configuration = configuration.withOptions(Option.IGNORING_ARRAY_ORDER);
        }
        if (Boolean.TRUE.equals(pattern.isIgnoreExtraElements())) {
            configuration = configuration.withOptions(
                    Option.IGNORING_EXTRA_FIELDS,
                    Option.IGNORING_EXTRA_ARRAY_ITEMS);
        }
        return configuration;
    }
}
