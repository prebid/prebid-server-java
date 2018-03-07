package org.prebid.server.handler;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

public class ValidateHandlerTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private RoutingContext routingContext;

    @Mock
    private HttpServerResponse httpServerResponse;

    private ValidateHandler validateHandler;

    @Before
    public void setUp() {
        validateHandler = ValidateHandler.create("org/prebid/server/handler/validate/schema/request.json");
        given(routingContext.response()).willReturn(httpServerResponse);
    }

    @Test
    public void shouldReturnResponseWithSchemaNotLoadedIfSchemaFileDoesNotExists() {
        // given
        given(routingContext.getBody()).willReturn(Buffer.buffer("{}"));
        validateHandler = ValidateHandler.create("notExistingPath");

        // when
        validateHandler.handle(routingContext);

        // then
        assertThat(captureResponseBody()).isEqualTo("Validation schema not loaded\n");
    }

    @Test
    public void shouldReturnResponseWithSchemaNotLoadedIfSchemaFileIsNotValidJson() {
        // given
        validateHandler = ValidateHandler.create("org/prebid/server/handler/validate/schema/not_valid.json");
        given(routingContext.getBody()).willReturn(Buffer.buffer("{}"));

        // when
        validateHandler.handle(routingContext);

        // then
        assertThat(captureResponseBody()).isEqualTo("Validation schema not loaded\n");
    }

    @Test
    public void shouldReturnResponseWithErrorParsingRequestMessageIfRequestBodyIsNotValidJson() {
        // given
        given(routingContext.getBody()).willReturn(Buffer.buffer("{"));

        // when
        validateHandler.handle(routingContext);

        // then
        assertThat(captureResponseBody()).contains("Error parsing request json");
    }

    @Test
    public void shouldReturnResponseWithValidationSuccessfulMessage() {
        // given
        given(routingContext.getBody()).willReturn(Buffer.buffer("{\"id\":\"id1\"}"));

        // when
        validateHandler.handle(routingContext);

        // then
        assertThat(captureResponseBody()).contains("Validation successful");
    }

    @Test
    public void shouldReturnResponseWithValidationErrorsIfRequestIfNotValidAgainstSchema()
            throws IOException {
        // given
        given(routingContext.getBody()).willReturn(Buffer.buffer("{}"));

        // when
        validateHandler.handle(routingContext);

        // then
        assertThat(captureResponseBody()).contains("$.id: is missing but it is required");
    }

    public String captureResponseBody() {
        final ArgumentCaptor<String> responseBodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(httpServerResponse).end(responseBodyCaptor.capture());
        return responseBodyCaptor.getValue();
    }
}
