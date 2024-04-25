package org.prebid.server.analytics.reporter.greenbids.model;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;


public class HttpUtil {
    public static void sendJson(String json, String endpointUrl) {
        HttpURLConnection connection = null;

        try {
            connection = getHttpURLConnection(json, endpointUrl);

            try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                System.out.println("[TEST] HttpUtil/sendJson: " + response);
            }
            connection.disconnect();

        } catch (IOException e) {
            e.printStackTrace();
            assert connection != null;
            InputStream errorStream = connection.getErrorStream();

            try (Scanner scanner = new Scanner(errorStream)) {
                String responseBody = scanner.useDelimiter("\\A").next();
                System.out.println("[TEST] HttpUtil/sendJson Scanner: " + responseBody);
            }
        }
    }

    private static HttpURLConnection getHttpURLConnection(String json, String endpointUrl) throws IOException {
        URL url = new URL(endpointUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.setDoOutput(true);

        try (java.io.OutputStream os = connection.getOutputStream()) {
            byte[] input = json.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        return connection;
    }
}
