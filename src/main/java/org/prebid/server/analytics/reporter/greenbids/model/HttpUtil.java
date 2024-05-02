package org.prebid.server.analytics.reporter.greenbids.model;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.deals.PlannerService;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class HttpUtil {

    private HttpUtil() {
        throw new UnsupportedOperationException("Utility class and cannot be instantiated");
    }

    private static final Logger logger = LoggerFactory.getLogger(PlannerService.class);

    public static void sendJson(String json, String endpointUrl) {
        HttpURLConnection connection = null;

        try {
            connection = getHttpURLConnection(json, endpointUrl);

            try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                final StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                logger.info("[TEST] HttpUtil/sendJson: " + response);
            }
            connection.disconnect();

        } catch (IOException e) {
            e.printStackTrace();
            assert connection != null;
            final InputStream errorStream = connection.getErrorStream();

            try (Scanner scanner = new Scanner(errorStream)) {
                final String responseBody = scanner.useDelimiter("\\A").next();
                logger.info("[TEST] HttpUtil/sendJson Scanner: " + responseBody);
            }
        }
    }

    private static HttpURLConnection getHttpURLConnection(String json, String endpointUrl) throws IOException {
        final URL url = new URL(endpointUrl);
        final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.setDoOutput(true);

        try (java.io.OutputStream os = connection.getOutputStream()) {
            final byte[] input = json.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        return connection;
    }
}
