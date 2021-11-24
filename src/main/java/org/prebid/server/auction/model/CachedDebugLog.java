package org.prebid.server.auction.model;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.EncodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponse;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public class CachedDebugLog {

    public static final String CACHE_TYPE = "xml";
    private static final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";

    private final boolean enabled;
    private final int ttl;
    private final Pattern escapeSymbolsPattern;

    private String cacheKey;
    private String request;
    private String headers;
    private String extBidResponse;
    private Boolean hasBids;

    private final JacksonMapper jacksonMapper;

    public CachedDebugLog(boolean enabled, int ttl, Pattern escapeSymbolsPattern, JacksonMapper jacksonMapper) {
        this.enabled = enabled;
        this.ttl = ttl;
        this.escapeSymbolsPattern = escapeSymbolsPattern;
        this.jacksonMapper = Objects.requireNonNull(jacksonMapper);
    }

    public int getTtl() {
        return ttl;
    }

    public void setRequest(String request) {
        this.request = request != null ? request : StringUtils.EMPTY;
    }

    public void setHeadersLog(CaseInsensitiveMultiMap headers) {
        this.headers = headers != null ? headers.toString() : StringUtils.EMPTY;
    }

    public void setExtBidResponse(ExtBidResponse response) {
        try {
            this.extBidResponse = response != null ? jacksonMapper.encodeToString(response) : StringUtils.EMPTY;
        } catch (EncodeException ex) {
            final String errorMessage = String.format("Unable to marshal response ext for debugging with a reason: %s",
                    ex.getMessage());
            this.extBidResponse = errorMessage;
            throw new PreBidException(errorMessage);
        }
    }

    public void setCacheKey(String cacheKey) {
        this.cacheKey = cacheKey;
    }

    public String getCacheKey() {
        return cacheKey;
    }

    public void setHasBids(boolean hasBids) {
        this.hasBids = hasBids;
    }

    public boolean hasBids() {
        return BooleanUtils.toBooleanDefaultIfNull(hasBids, true);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setErrors(List<String> errors) {
        if (StringUtils.isBlank(extBidResponse) && CollectionUtils.isEmpty(errors)) {
            extBidResponse = "No response or errors created";
            return;
        }
        extBidResponse = String.format("%s\nErrors:\n%s",
                StringUtils.isBlank(extBidResponse) ? StringUtils.EMPTY : extBidResponse,
                String.join("\n", errors));
    }

    public String buildCacheBody() {
        final String resolvedResponse = prepareValueToPublish(extBidResponse, "Response");
        final String resoledRequest = prepareValueToPublish(request, "Request");
        final String resolvedHeaders = prepareValueToPublish(headers, "Headers");
        return String.format("%s<Log>\n%s\n%s\n%s\n</Log>", XML_HEADER, resoledRequest, resolvedResponse,
                resolvedHeaders);
    }

    private String prepareValueToPublish(String value, String fieldName) {
        return String.format("<%s>%s</%s>", fieldName, value != null
                        ? escapeNonXmlSymbols(value)
                        : StringUtils.EMPTY,
                fieldName);
    }

    private String escapeNonXmlSymbols(String value) {
        return escapeSymbolsPattern != null
                ? escapeSymbolsPattern.matcher(value).replaceAll(StringUtils.EMPTY)
                : value;
    }
}
