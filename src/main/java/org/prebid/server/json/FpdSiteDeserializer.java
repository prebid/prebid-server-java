package org.prebid.server.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Site;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.log.ConditionalLogger;

import java.io.IOException;

/**
 * This class is a trick when client sends invalid request
 * with site.domain as ARRAY instead of STRING in first party data:
 * <pre>
 * {
 *   "ext": {
 *     "prebid": {
 *       "bidderconfig": [
 *         {
 *           "bidders": [ "some-bidder" ],
 *           "config": {
 *             "fpd": {
 *               "site": {
 *                 "domain": [
 *                   "first.domain",
 *                   "second.domain"
 *                 ]
 *               }
 *             }
 *           }
 *         }
 *       ]
 *     }
 *   }
 * }
 * </pre>
 * In that case the first domain will be used.
 * <p>
 * PS. We don't want to spread this solution through entire project, just fix first party data case.
 */
public class FpdSiteDeserializer extends JsonDeserializer<Site> {

    private static final Logger logger = LoggerFactory.getLogger(FpdSiteDeserializer.class);
    private static final ConditionalLogger conditionalLogger = new ConditionalLogger(logger);

    private static final String DOMAIN_FIELD = "domain";

    @Override
    public Site deserialize(JsonParser parser, DeserializationContext ctx) throws IOException {
        final ObjectCodec mapper = parser.getCodec();
        final JsonNode node = mapper.readTree(parser);

        if (node.has(DOMAIN_FIELD)) {
            final JsonNode domainNode = node.get(DOMAIN_FIELD);
            if (domainNode instanceof ArrayNode) {
                final ObjectNode updatedNode = (ObjectNode) node;

                if (!domainNode.isEmpty()) { // get first domain
                    final JsonNode firstDomain = domainNode.get(0);
                    if (firstDomain.isTextual()) {
                        updatedNode.set(DOMAIN_FIELD, firstDomain);
                        conditionalLogger.info(
                                String.format("Incorrect FPD.site.domain format: ARRAY was fixed to %s", firstDomain),
                                100);
                    }
                } else { // remove domain field since it is empty array
                    updatedNode.set(DOMAIN_FIELD, null);
                    conditionalLogger.info("Incorrect FPD.site.domain ARRAY format: empty, so is removed", 100);
                }
            }
        }

        return mapper.treeToValue(node, Site.class);
    }
}
