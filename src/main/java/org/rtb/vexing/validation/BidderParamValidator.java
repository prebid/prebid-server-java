package org.rtb.vexing.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaException;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.ValidationMessage;
import io.vertx.core.json.Json;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This Component aimed to validate <i>bidrequest.imp[i].ext.{bidder}</i> portion of bidRequest. It relies on
 * JSON schemas that need to be located as resources on classpath.
 */
public class BidderParamValidator {

    private static final JsonSchemaFactory SCHEMA_FACTORY = new JsonSchemaFactory();
    private static final String JSON_FILE_EXT = ".json";
    private static final String FILE_SEP = "/";

    private final Map<Bidder, JsonSchema> bidderSchemas;
    private final String schemas;

    private BidderParamValidator(Map<Bidder, JsonSchema> bidderSchemas, String schemas) {
        this.bidderSchemas = bidderSchemas;
        this.schemas = schemas;
    }

    /**
     * Validates the {@link JsonNode} input parameter against bidder's JSON-schema
     *
     * @param bidder the bidder who's schema intended to be used for validation
     * @param jsonNode the node which needs to be validated
     * @return the set of {@link java.lang.String} messages
     */
    public Set<String> validate(Bidder bidder, JsonNode jsonNode) {
        return bidderSchemas.get(bidder).validate(jsonNode).stream()
                .map(ValidationMessage::getMessage)
                .collect(Collectors.toSet());
    }

    /**
     * Returns the JSON object combining all schemas for all bidders. Each bidder has a subnode with it's schema within
     * framing object.
     * <pre>
     * {code
     *      {
     *          "appnexus": {
     *              "schema": "...."
     *          },
     *          "facebook": {
     *              "schema": "..."
     *          },
     *          "rubicon": {
     *              "schema": "...."
     *          }
     *       }
     * }
     * </pre>
     */
    public String schemas() {
        return this.schemas;
    }

    /**
     * Checks if the bidder with passed name exists.
     * @param name the name of the bidder, {@link java.lang.String} representation
     * @return {@code true} if the name is valid BidderName otherwise {@code false}
     */
    boolean isValidBidderName(String name) {
        try {
            Bidder.valueOf(name);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Constructs an instance of {@link BidderParamValidator}. This method requires all the necessary JSON schemas
     * exist as CLASSPATH resources, otherwise {@link IllegalArgumentException} will be thrown. This method consumes
     * schema directory parameter that defines the root directory for files containing schema. By convention the name
     * of each schema file same as corresponding bidder name.
     *
     * @param schemaDirectory contains JSON-schemas
     * @return configured instance of BidderParamValidator
     */
    public static BidderParamValidator create(String schemaDirectory) {
        Objects.requireNonNull(schemaDirectory);

        final Map<Bidder, JsonNode> bidderRawSchemas = new LinkedHashMap<>();

        EnumSet.allOf(Bidder.class)
                .forEach(bidder -> bidderRawSchemas.put(bidder, readFromClasspath(schemaDirectory, bidder)));

        return new BidderParamValidator(toBidderSchemas(bidderRawSchemas), toSchemas(bidderRawSchemas));
    }

    private static Map<Bidder, JsonSchema> toBidderSchemas(Map<Bidder, JsonNode> bidderRawSchemas) {
        return bidderRawSchemas.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> toBidderSchema(e.getValue(), e.getKey())));
    }

    private static String toSchemas(Map<Bidder, JsonNode> bidderRawSchemas) {
        try {
            return Json.mapper.writeValueAsString(bidderRawSchemas);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Couldn't combine json schemas into single json string");
        }
    }

    private static JsonSchema toBidderSchema(JsonNode schema, Bidder bidder) {
        final JsonSchema result;
        try {
            result = SCHEMA_FACTORY.getSchema(schema);
        } catch (JsonSchemaException e) {
            throw new IllegalArgumentException(String.format("Couldn't parse %s bidder schema", bidder), e);
        }
        return result;
    }

    private static JsonNode readFromClasspath(String schemaDirectory, Bidder bidder) {
        final JsonNode result;
        final String path = schemaDirectory + FILE_SEP + bidder + JSON_FILE_EXT;

        final InputStream resourceAsStream = BidderParamValidator.class.getResourceAsStream(path);

        if (resourceAsStream != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resourceAsStream,
                    StandardCharsets.UTF_8))) {

                result = toJsonNode(reader.lines().collect(Collectors.joining("\n")), bidder);
            } catch (IOException | RuntimeException e) {
                throw new IllegalArgumentException(
                        String.format("Failed to load %s json schema at %s", bidder, path), e);
            }
        } else {
            throw new IllegalArgumentException(String.format("Couldn't find %s json schema at %s", bidder, path));
        }
        return result;
    }

    private static JsonNode toJsonNode(String content, Bidder bidder) {
        final JsonNode result;
        if (StringUtils.isNotBlank(content)) {
            try {
                result = Json.mapper.readTree(content);
            } catch (IOException | JsonSchemaException e) {
                throw new IllegalArgumentException(String.format("Couldn't parse %s bidder schema", bidder), e);
            }
        } else {
            throw new IllegalArgumentException(
                    String.format("Couldn't parse %s bidder schema. File is empty", bidder));
        }
        return result;
    }

    /**
     * This type represents all known bidders who's schemas should exist on a schema directory
     */
    public enum Bidder {
        appnexus, facebook, index, lifestreet, pubmatic, pulsepoint, rubicon, conversant
    }
}
