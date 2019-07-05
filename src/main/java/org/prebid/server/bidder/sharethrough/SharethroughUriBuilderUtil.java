package org.prebid.server.bidder.sharethrough;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import org.prebid.server.bidder.sharethrough.model.StrUriParameters;

import java.net.URISyntaxException;
import java.util.List;

class SharethroughUriBuilderUtil {

    private SharethroughUriBuilderUtil() {
    }

    /**
     * Creates uri with parameters for sharethrough request
     */
    static String buildSharethroughUrl(String baseUri, String supplyId, String strVersion, StrUriParameters params) {
        return new URIBuilder()
                .setPath(baseUri)
                .addParameter("placement_key", params.getPkey())
                .addParameter("bidId", params.getBidID())
                .addParameter("consent_required", String.valueOf(params.isConsentRequired()))
                .addParameter("consent_string", params.getConsentString())
                .addParameter("instant_play_capable", String.valueOf(params.isInstantPlayCapable()))
                .addParameter("stayInIframe", String.valueOf(params.isIframe()))
                .addParameter("height", String.valueOf(params.getHeight()))
                .addParameter("width", String.valueOf(params.getWidth()))

                .addParameter("supplyId", supplyId)
                .addParameter("strVersion", strVersion)
                .toString();
    }

    /**
     * Creates uri with parameters for sharethrough request
     */
    static StrUriParameters buildSharethroughUrlParameters(String uri) {
        try {
            final URIBuilder uriBuilder = new URIBuilder(uri);
            final List<NameValuePair> queryParams = uriBuilder.getQueryParams();

            return StrUriParameters.builder()
                    .height(getHeight(queryParams))
                    .width(getWidth(queryParams))
                    .iframe(Boolean.parseBoolean(getValueByKey(queryParams, "stayInIframe")))
                    .consentRequired(Boolean.parseBoolean(getValueByKey(queryParams, "consent_required")))
                    .pkey(getValueByKey(queryParams, "placement_key"))
                    .bidID(getValueByKey(queryParams, "bidId"))
                    .consentString(getValueByKey(queryParams, "consent_string"))
                    .build();

        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Cant resolve uri: " + uri, e);
        }
    }

    private static int getHeight(List<NameValuePair> nameValuePairs) {
        final String height = getValueByKey(nameValuePairs, "height");
        try {
            return Integer.parseInt(height);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Height is not a number. Height value = " + height, e);
        }
    }

    private static int getWidth(List<NameValuePair> nameValuePairs) {
        final String width = getValueByKey(nameValuePairs, "width");
        try {
            return Integer.parseInt(width);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Width is not a number. Width value = " + width, e);
        }
    }

    private static String getValueByKey(List<NameValuePair> nameValuePairs, String key) {
        return nameValuePairs.stream()
                .filter(nameValuePair -> nameValuePair.getName().equals(key))
                .map(NameValuePair::getValue)
                .findFirst()
                .orElse("");
    }
}
