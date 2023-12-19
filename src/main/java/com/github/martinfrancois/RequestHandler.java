package com.github.martinfrancois;

import java.io.IOException;
import java.net.ConnectException;
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
                System.err.println("HTTP Request failed with status code: " + response.statusCode());
                System.exit(1);
            }
        } catch (ConnectException e) {
            System.err.println("Failed to connect to the phone. Make sure your phone is plugged in, USB debugging is enabled and you allowed USB debugging.");
            System.exit(1);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        return null;
    }
}
