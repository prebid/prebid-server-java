package org.prebid.server.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.ValidationMessage;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.prebid.server.util.ResourceUtil;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Class represents endpoint to validate json request against schema.
 */
public class ValidateHandler implements Handler<RoutingContext> {

    private static final JsonSchemaFactory SCHEMA_FACTORY = new JsonSchemaFactory();
    private static final Logger logger = LoggerFactory.getLogger(ValidateHandler.class);

    private final JsonSchema jsonSchema;

    private ValidateHandler(JsonSchema jsonSchema) {
        this.jsonSchema = jsonSchema;
    }

    /**
     * Creates {@link ValidateHandler} instance by reading schema json from file. If any error occurred during
     * creating schema, {@link ValidateHandler} will be instantiated with null value schema.
     */
    public static ValidateHandler create(String schemaFilePath) {
        String schema;
        try {
            schema = ResourceUtil.readFromClasspath(schemaFilePath);
        } catch (IOException | IllegalArgumentException ex) {
            logger.error("Unable to open or read json schema file from path: {0}.", ex, schemaFilePath);
            return new ValidateHandler(null);
        }

        final JsonNode schemaJsonNode;
        try {
            schemaJsonNode = Json.mapper.readTree(schema);
        } catch (IOException ex) {
            logger.error("Unable to load request schema for path: {0}.", ex, schemaFilePath);
            return new ValidateHandler(null);
        }

        // remove id field as we do not use sub schemas and validation schema library required id field to point to
        // real resource
        ((ObjectNode) schemaJsonNode).remove("id");

        return new ValidateHandler(SCHEMA_FACTORY.getSchema(schemaJsonNode));
    }

    /**
     * Validates incoming json request against schema. Returns list of validation errors in case request is not valid,
     * otherwise returns successful validation message.
     */
    @Override
    public void handle(RoutingContext routingContext) {
        final HttpServerResponse response = routingContext.response();
        response.putHeader("Content-Type", "text/plain");
        final JsonNode requestJsonNode;
        try {
            requestJsonNode = Json.mapper.readTree(routingContext.getBody().toString());
        } catch (IOException e) {
            response.end(String.format("Error parsing request json: %s", e.getMessage()));
            return;
        }

        if (jsonSchema == null) {
            response.end("Validation schema not loaded\n");
            return;
        }

        final Set<ValidationMessage> errorMessages = jsonSchema.validate(requestJsonNode);
        if (errorMessages.isEmpty()) {
            response.end("Validation successful\n");
        } else {
            response.end(errorMessages.stream()
                    .map(validationMessage -> validationMessage.getMessage())
                    .collect(Collectors.joining("\n")));
        }
    }
}
