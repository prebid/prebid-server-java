package org.prebid.server.cache.utils;

import io.vertx.core.MultiMap;
import org.prebid.server.util.HttpUtil;

import java.net.MalformedURLException;
import java.net.URL;

public class CacheServiceUtil {

    public static final MultiMap CACHE_HEADERS = HttpUtil.headers();
    public static final String XML_CREATIVE_TYPE = "xml";
    public static final String JSON_CREATIVE_TYPE = "json";

    private CacheServiceUtil() {
    }

    public static URL getCacheEndpointUrl(String cacheSchema, String cacheHost, String path) {
        try {
            final URL baseUrl = getCacheBaseUrl(cacheSchema, cacheHost);
            return new URL(baseUrl, path);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Could not get cache endpoint for prebid cache service", e);
        }
    }

    private static URL getCacheBaseUrl(String cacheSchema, String cacheHost) throws MalformedURLException {
        return new URL(cacheSchema + "://" + cacheHost);
    }

    public static String getCachedAssetUrlTemplate(String cacheSchema,
                                                   String cacheHost,
                                                   String path,
                                                   String cacheQuery) {

        try {
            final URL baseUrl = getCacheBaseUrl(cacheSchema, cacheHost);
            return new URL(baseUrl, path + "?" + cacheQuery).toString();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Could not get cached asset url template for prebid cache service", e);
        }
    }

}
