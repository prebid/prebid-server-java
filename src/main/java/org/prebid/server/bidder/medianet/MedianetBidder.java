package org.prebid.server.bidder.medianet;

import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.OpenrtbBidder;
import org.prebid.server.json.JacksonMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Medianet {@link Bidder} implementation.
 */
public class MedianetBidder extends OpenrtbBidder<Void> {

    private static final String EXTERNAL_URL_MACRO = "{{PREBID_SERVER_ENDPOINT}}";

    public MedianetBidder(String endpointUrl, JacksonMapper mapper, String externalUrl) {
        super(getEndpoint(endpointUrl, externalUrl),
                RequestCreationStrategy.SINGLE_REQUEST,
                Void.class,
                mapper);
    }

    private static String getEndpoint(String originalEndpoint, String replacement) {
        replacement = URLEncoder.encode(replacement, StandardCharsets.UTF_8);
        return StringUtils.replace(originalEndpoint, EXTERNAL_URL_MACRO, replacement);
    }
}
