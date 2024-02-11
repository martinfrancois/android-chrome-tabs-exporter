package com.github.martinfrancois;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class RequestHandler {
    private RequestHandler() {
        throw new UnsupportedOperationException("RequestHandler is a utility class and should not be instantiated.");
    }

    /**
     * Sends a GET request to the specified URL and returns the response body as a {@link String}.
     * @param url to send the GET request to
     * @return response body as a {@link String}
     */
    public static String get(String url) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(java.net.URI.create(url))
                .GET()
                .build();
        try (HttpClient httpClient = HttpClient.newHttpClient()) {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() == 200) {
                return response.body();
            } else {
                Main.updateError.accept("HTTP Request failed with status code: " + response.statusCode());
            }
        } catch (Exception e) {
            Main.updateError.accept("Failed to connect to the phone. Make sure your phone is plugged in, unlocked, USB debugging is enabled, you allowed USB debugging and the Chrome app is open. You can also try reconnecting your phone if none of this helps.");
            throw new RuntimeException(e);
        }
        return null;
    }
}
