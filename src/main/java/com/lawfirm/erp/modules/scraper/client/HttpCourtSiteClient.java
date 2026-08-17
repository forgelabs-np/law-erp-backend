package com.lawfirm.erp.modules.scraper.client;

import com.lawfirm.erp.modules.scraper.config.ScraperProperties;
import com.lawfirm.erp.modules.scraper.converter.DevanagariConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * Session-based HTTP client for the court site.
 *
 * Flow (verified against the live site):
 *   1. GET the endpoint first — the server issues PHPSESSID + court_session + F5 cookies.
 *   2. POST the form (daily: todays_date + pesi_date + submit; weekly: pesi_bar + submit).
 *   3. The POST answers 302 with a flash message (e.g. "causelist not published"); follow
 *      the Location with a GET to receive the final page.
 *
 * The site binds the session's USER_AGENT, so a consistent browser-like UA is sent.
 * A politeness delay is applied before each request.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HttpCourtSiteClient implements CourtSiteClient {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/126.0 Safari/537.36";

    private final ScraperProperties properties;

    @Override
    public String scrapeDaily(int courtId, String dateBs) {
        String path = "/weekly_dainik/pesi/daily/" + courtId;
        String form = "todays_date=" + enc(dateBs)
                + "&pesi_date=" + enc(DevanagariConverter.toDevanagari(dateBs))
                + "&submit=" + enc("खोज्नु होस्");
        return postForm(path, form);
    }

    @Override
    public String scrapeWeekly(int courtId) {
        String path = "/weekly_dainik/pesi/weekly_pesi/" + courtId;
        String form = "pesi_bar=" + enc("all") + "&submit=" + enc("खोज्नु होस्");
        return postForm(path, form);
    }

    private String postForm(String path, String form) {
        HttpClient client = newClient();
        String base = properties.getBaseUrl();
        try {
            // 1. Establish the session.
            HttpRequest get = HttpRequest.newBuilder(URI.create(base + path))
                    .timeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                    .header("User-Agent", USER_AGENT)
                    .GET().build();
            HttpResponse<String> getResp = client.send(get, textHandler());
            if (getResp.statusCode() / 100 != 2) {
                log.warn("Court site GET {} -> {}", path, getResp.statusCode());
            }
            sleep();

            // 2. POST the form.
            HttpRequest post = HttpRequest.newBuilder(URI.create(base + path))
                    .timeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                    .header("User-Agent", USER_AGENT)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> postResp = client.send(post, textHandler());
            sleep();

            // 3. Follow the 302 to the final page.
            if (postResp.statusCode() / 100 == 3) {
                Optional<String> location = postResp.headers().firstValue("Location");
                if (location.isPresent()) {
                    URI target = URI.create(base + path).resolve(location.get());
                    HttpRequest follow = HttpRequest.newBuilder(target)
                            .timeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                            .header("User-Agent", USER_AGENT)
                            .GET().build();
                    HttpResponse<String> finalResp = client.send(follow, textHandler());
                    sleep();
                    return finalResp.body();
                }
            }
            return postResp.body();
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Court site request failed for " + path + ": " + e.getMessage(), e);
        }
    }

    private HttpClient newClient() {
        return HttpClient.newBuilder()
                .cookieHandler(new CookieManager())
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    private HttpResponse.BodyHandler<String> textHandler() {
        return HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
    }

    private void sleep() {
        try {
            Thread.sleep(properties.getRequestDelayMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
