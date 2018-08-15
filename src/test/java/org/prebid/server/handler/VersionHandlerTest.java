package org.prebid.server.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;


public class VersionHandlerTest {

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
        versionHandler = VersionHandler.create("not_found.json");
        given(routingContext.response()).willReturn(httpResponse);
        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);

        // when
        versionHandler.handle(routingContext);

        // then
        verify(httpResponse).end(Json.mapper.writeValueAsString(RevisionResponse.of("not-set")));
    }

    @Test
    public void handleShouldRespondWithInternalServerErrorWhenPropertyIsNotInFile() throws JsonProcessingException {
        // given
        versionHandler = VersionHandler.create("org/prebid/server/handler/version/empty.json");
        given(routingContext.response()).willReturn(httpResponse);
        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);

        // when
        versionHandler.handle(routingContext);

        // then
        verify(httpResponse).end(Json.mapper.writeValueAsString(RevisionResponse.of("not-set")));
    }


    @Test
    public void handleShouldRespondWithHashWhenPropertyIsInFile() throws JsonProcessingException {
        // given
        versionHandler = VersionHandler.create("org/prebid/server/handler/version/version.json");
        given(routingContext.response()).willReturn(httpResponse);
        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);

        // when
        versionHandler.handle(routingContext);

        // then
        verify(httpResponse).end(Json.mapper.writeValueAsString(
                RevisionResponse.of("4df3f6192d7938ccdaac04df783c46c7e8847d08")));
    }

    @AllArgsConstructor(staticName = "of")
    @Value
    private static class RevisionResponse {

        String revision;
    }
}
