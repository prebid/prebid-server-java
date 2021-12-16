package org.prebid.server.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaException;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.ValidationMessage;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.json.EncodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.util.ResourceUtil;

import java.io.IOException;
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

    private final Map<String, JsonSchema> bidderSchemas;
    private final String schemas;

    private BidderParamValidator(Map<String, JsonSchema> bidderSchemas, String schemas) {
        this.bidderSchemas = bidderSchemas;
        this.schemas = schemas;
    }

    /**
     * Validates the {@link JsonNode} input parameter against bidder's JSON-schema
     */
    public Set<String> validate(String bidder, JsonNode jsonNode) {
        return bidderSchemas.get(bidder).validate(jsonNode).stream()
                .map(ValidationMessage::getMessage)
                .collect(Collectors.toSet());
    }

    /**
     * Returns a JSON object combining all schemas for all bidders. Each bidder has a subnode with its schema within
     * framing object.
     * <pre>
     * {code
     *      {
     *          "appnexus": {
     *              "schema": "...."
     *          },
     *          "audienceNetwork": {
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
     * Constructs an instance of {@link BidderParamValidator}. This method requires all the necessary JSON schemas
     * exist as CLASSPATH resources, otherwise {@link IllegalArgumentException} will be thrown. This method consumes
     * schema directory parameter that defines the root directory for files containing schemas. By convention the name
     * of each schema file same as corresponding bidder name.
     */
    public static BidderParamValidator create(
            BidderCatalog bidderCatalog, String schemaDirectory, JacksonMapper mapper) {

        Objects.requireNonNull(bidderCatalog);
        Objects.requireNonNull(schemaDirectory);
        Objects.requireNonNull(mapper);

        final Map<String, JsonNode> bidderRawSchemas = new LinkedHashMap<>();

        bidderCatalog.names().forEach(bidder -> bidderRawSchemas.put(
                bidder, createSchemaNode(schemaDirectory, maybeResolveAlias(bidderCatalog, bidder), mapper)));

        return new BidderParamValidator(toBidderSchemas(bidderRawSchemas), toSchemas(bidderRawSchemas, mapper));
    }

    private static Map<String, JsonSchema> toBidderSchemas(Map<String, JsonNode> bidderRawSchemas) {
        return bidderRawSchemas.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> toBidderSchema(e.getValue(), e.getKey())));
    }

    private static String toSchemas(Map<String, JsonNode> bidderRawSchemas, JacksonMapper mapper) {
        try {
            return mapper.encodeToString(bidderRawSchemas);
        } catch (EncodeException e) {
            throw new IllegalArgumentException("Couldn't combine json schemas into single json string");
        }
    }

    private static JsonSchema toBidderSchema(JsonNode schema, String bidder) {
        final JsonSchema result;
        try {
            result = SCHEMA_FACTORY.getSchema(schema);
        } catch (JsonSchemaException e) {
            throw new IllegalArgumentException(String.format("Couldn't parse %s bidder schema", bidder), e);
        }
        return result;
    }

    private static String maybeResolveAlias(BidderCatalog bidderCatalog, String bidder) {
        return ObjectUtils.defaultIfNull(bidderCatalog.bidderInfoByName(bidder).getAliasOf(), bidder);
    }

    private static JsonNode createSchemaNode(String schemaDirectory, String bidder, JacksonMapper mapper) {
        final JsonNode result;
        final String path = schemaDirectory + FILE_SEP + bidder + JSON_FILE_EXT;
        try {
            result = toJsonNode(ResourceUtil.readFromClasspath(path), bidder, mapper);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(String.format("Couldn't find %s json schema at %s", bidder, path), e);
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException(
                    String.format("Failed to load %s json schema at %s", bidder, path), e);
        }
        return result;
    }

    private static JsonNode toJsonNode(String content, String bidder, JacksonMapper mapper) {
        final JsonNode result;
        if (StringUtils.isNotBlank(content)) {
            try {
                result = mapper.mapper().readTree(content);
            } catch (IOException | JsonSchemaException e) {
                throw new IllegalArgumentException(String.format("Couldn't parse %s bidder schema", bidder), e);
            }
        } else {
            throw new IllegalArgumentException(
                    String.format("Couldn't parse %s bidder schema. File is empty", bidder));
        }
        return result;
    }
}
