package org.prebid.server.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

public class VersionHandlerTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerResponse httpResponse;

    private VersionHandler versionHandler;

    @Test
    public void shouldCreateRevisionWithNoSetVersionValueWhenFileWasNotFound() throws JsonProcessingException {
        // given
        versionHandler = VersionHandler.create("not_found.json", jacksonMapper);
        given(routingContext.response()).willReturn(httpResponse);

        // when
        versionHandler.handle(routingContext);

        // then
        verify(httpResponse).end(mapper.writeValueAsString(RevisionResponse.of("not-set", "not-set")));
    }

    @Test
    public void handleShouldRespondWithNotSetWhenPropertyIsNotInFile() throws JsonProcessingException {
        // given
        versionHandler = VersionHandler.create("org/prebid/server/handler/version/empty.json", jacksonMapper);
        given(routingContext.response()).willReturn(httpResponse);

        // when
        versionHandler.handle(routingContext);

        // then
        verify(httpResponse).end(mapper.writeValueAsString(RevisionResponse.of("not-set", "not-set")));
    }

    @Test
    public void handleShouldRespondWithHashWhenPropertyIsInFile() throws JsonProcessingException {
        // given
        versionHandler = VersionHandler.create("org/prebid/server/handler/version/version.json", jacksonMapper);
        given(routingContext.response()).willReturn(httpResponse);

        // when
        versionHandler.handle(routingContext);

        // then
        verify(httpResponse).end(mapper.writeValueAsString(
                RevisionResponse.of("4df3f6192d7938ccdaac04df783c46c7e8847d08", "1.41.0")));
    }

    @AllArgsConstructor(staticName = "of")
    @Value
    private static class RevisionResponse {

        String revision;

        String version;
    }
}
