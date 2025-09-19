package org.prebid.server.cache.utils;

import io.vertx.core.MultiMap;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.client.utils.URLEncodedUtils;
import org.prebid.server.util.HttpUtil;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;

public class CacheServiceUtil {

    public static final MultiMap CACHE_HEADERS = HttpUtil.headers();
    public static final String XML_CREATIVE_TYPE = "xml";
    public static final String JSON_CREATIVE_TYPE = "json";

    private CacheServiceUtil() {
    }

    public static URL getCacheEndpointUrl(String cacheSchema, String cacheHost, String path) {
        try {
            final URIBuilder uriBuilder = cacheBaseUriBuilder(cacheSchema, cacheHost);
            return uriBuilder.setPath(path).build().toURL();
        } catch (MalformedURLException | URISyntaxException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Could not get cache endpoint for prebid cache service", e);
        }
    }

    public static String getCachedAssetUrlTemplate(String cacheSchema,
                                                   String cacheHost,
                                                   String path,
                                                   String cacheQuery) {

        try {
            return HttpUtil.validateUrl(cacheBaseUriBuilder(cacheSchema, cacheHost)
                    .setPath(path)
                    .setParameters(URLEncodedUtils.parse(cacheQuery, null))
                    .build()
                    .toString());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Could not get cached asset url template for prebid cache service", e);
        }
    }

    private static URIBuilder cacheBaseUriBuilder(String cacheSchema, String cacheHost) {
        return new URIBuilder().setScheme(cacheSchema).setHost(cacheHost);
    }

}
