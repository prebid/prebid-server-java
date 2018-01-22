package org.rtb.vexing.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaException;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.ValidationMessage;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.HashMap;
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
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String JSON_FILE_EXT = ".json";
    private static final String FILE_SEP = "/";

    private final Map<Bidder, BidderSchema> bidderSchemas;

    private BidderParamValidator(Map<Bidder, BidderSchema> bidders) {
        this.bidderSchemas = bidders;
    }

    /**
     * Validates {@link JsonNode} input parameter against bidder's JSON-schema
     *
     * @param bidder the bidder who's schema intended to be used for validation
     * @param jsonNode the node which needs to be validated
     * @return the set of {@link java.lang.String} messages
     */
    public Set<String> validate(Bidder bidder, JsonNode jsonNode) {
        return bidderSchemas.get(bidder).parsedSchema
                .validate(jsonNode).stream()
                .map(ValidationMessage::getMessage)
                .collect(Collectors.toSet());
    }

    /**
     * Return raw string JSON schema for particular {@link Bidder}.
     *
     * @param bidder the bidder who's schema should be returned
     * @return raw string JSON schema
     */
    public String schema(Bidder bidder) {
        return bidderSchemas.get(bidder).rawSchema;
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
     * exist as CLASSPATH resources, otherwise {@link IllegalArgumentException} will be thrown.
     *
     * @param schemaDirectory contains JSON-schemas
     * @return configured instance of BidderParamValidator
     */
    public static BidderParamValidator create(String schemaDirectory) {
        Objects.requireNonNull(schemaDirectory);

        final Map<Bidder, BidderSchema> bidderSchemas = new HashMap<>();

        EnumSet.allOf(Bidder.class)
                .forEach(bidder -> bidderSchemas.put(bidder, readBidderSchema(schemaDirectory, bidder)));

        return new BidderParamValidator(bidderSchemas);
    }

    private static BidderSchema readBidderSchema(String schemaDirectory, Bidder bidder) {
        final BidderSchema result;
        final String path = schemaDirectory + FILE_SEP + bidder + JSON_FILE_EXT;
        final String rawSchema = readFromClasspath(path);

        if (StringUtils.isNotBlank(rawSchema)) {
            try {
                result = BidderSchema.of(rawSchema, SCHEMA_FACTORY.getSchema(MAPPER.readTree(rawSchema)));
            } catch (IOException | JsonSchemaException e) {
                throw new IllegalArgumentException(String.format("Couldn't parse %s bidder schema from %s", bidder,
                        path), e);
            }
        } else {
            throw new IllegalArgumentException(
                    String.format("Couldn't parse %s bidder schema from %s. File is empty", bidder, path));
        }
        return result;
    }

    private static String readFromClasspath(String path) {
        final String content;

        final InputStream resourceAsStream = BidderParamValidator.class.getResourceAsStream(path);

        if (resourceAsStream != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resourceAsStream,
                    StandardCharsets.UTF_8))) {
                content = reader.lines().collect(Collectors.joining("\n"));
            } catch (IOException | RuntimeException e) {
                throw new IllegalArgumentException(String.format("Failed to load json schema at %s", path), e);
            }
        } else {
            throw new IllegalArgumentException(String.format("Couldn't find json schema at %s", path));
        }

        return content;
    }

    /**
     * This type represents all known bidders who's schemas should exist on a schema directory
     */
    public enum Bidder {
        appnexus, facebook, index, lifestreet, pubmatic, pulsepoint, rubicon
    }

    @AllArgsConstructor(staticName = "of")
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    private static class BidderSchema {
        String rawSchema;
        JsonSchema parsedSchema;
    }
}
