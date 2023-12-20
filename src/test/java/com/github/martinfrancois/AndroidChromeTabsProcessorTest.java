package com.github.martinfrancois;

import com.github.martinfrancois.model.Tab;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.TreeSet;

import static com.github.martinfrancois.AndroidChromeTabsProcessor.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AndroidChromeTabsProcessorTest {

    @Test
    void testToTreeSetWithMalformedJson() {
        String tabsJson = "malformedJson";
        assertThatThrownBy(() -> toTreeSet(tabsJson)).isInstanceOf(JSONException.class);
    }

    @Test
    void testToTreeSetWithTabsHavingInvalidJsonInArray() {
        String tabsJson = "[{\"title\":\"Google\",\"url\":\"https://www.google.com\"},invalid,{\"title\":\"GitHub\",\"url\":\"https://github.com\"}]";

        assertThatThrownBy(() -> toTreeSet(tabsJson)).isInstanceOf(JSONException.class);
    }

    @Test
    void testToTreeSetWithEmptyJson() {
        String tabsJson = "[]";
        TreeSet<Tab> result = toTreeSet(tabsJson);

        assertThat(result).isEmpty();
    }

    @Test
    void testToTreeSetWithNullJson() {
        TreeSet<Tab> result = toTreeSet(null);

        assertThat(result).isEmpty();
    }

    @Test
    void testToTreeSetWithTabsHavingSingleEntry() {
        JSONArray tabsArray = new JSONArray()
                .put(new JSONObject().put("title", "Google").put("url", "https://www.google.com"));

        String tabsJson = tabsArray.toString();
        TreeSet<Tab> result = toTreeSet(tabsJson);

        assertThat(result).containsExactly(
                new Tab("Google", "https://www.google.com")
        );
    }

    @Test
    void testToTreeSetWithTabsHavingNullAndBlankValues() {
        JSONArray tabsArray = new JSONArray()
                .put(new JSONObject().put("title", "").put("url", ""))
                .put(new JSONObject().put("title", "  ").put("url", "     "))
                .put(new JSONObject().put("title", "GitHub").put("url", "https://github.com"));

        String tabsJson = tabsArray.toString();
        TreeSet<Tab> result = toTreeSet(tabsJson);

        assertThat(result).containsExactly(
                new Tab("GitHub", "https://github.com")
        );
    }

    @Test
    void testToTreeSetWithTabsHavingSameUrl() {
        JSONArray tabsArray = new JSONArray()
                .put(new JSONObject().put("title", "Google").put("url", "https://www.google.com"))
                .put(new JSONObject().put("title", "Still Google").put("url", "https://www.google.com"))
                .put(new JSONObject().put("title", "Stack Overflow").put("url", "https://stackoverflow.com"));

        String tabsJson = tabsArray.toString();
        TreeSet<Tab> result = toTreeSet(tabsJson);

        assertThat(result).containsExactly(
                new Tab("Stack Overflow", "https://stackoverflow.com"),
                new Tab("Still Google", "https://www.google.com") // Duplicates based on URL should be removed
        );
    }

    @Test
    void testToTreeSetWithTabsHavingSameTitleAndUrl() {
        JSONArray tabsArray = new JSONArray()
                .put(new JSONObject().put("title", "Google").put("url", "https://www.google.com"))
                .put(new JSONObject().put("title", "GitHub").put("url", "https://github.com"))
                .put(new JSONObject().put("title", "Google").put("url", "https://www.google.com"));

        String tabsJson = tabsArray.toString();
        TreeSet<Tab> result = toTreeSet(tabsJson);

        assertThat(result).containsExactly(
                new Tab("GitHub", "https://github.com"), // Only one entry with the same title and URL should be present
                new Tab("Google", "https://www.google.com")
        );
    }

    @Test
    void testToTreeSetWithTabsHavingNullTitleAndUrlWithDuplicates() {
        JSONArray tabsArray = new JSONArray()
                .put(new JSONObject().put("title", JSONObject.NULL).put("url", JSONObject.NULL))
                .put(new JSONObject().put("title", "GitHub").put("url", "https://github.com"))
                .put(new JSONObject().put("title", JSONObject.NULL).put("url", "https://stackoverflow.com"))
                .put(new JSONObject().put("title", JSONObject.NULL).put("url", JSONObject.NULL))
                .put(new JSONObject().put("title", "GitHub").put("url", "https://github.com"));

        String tabsJson = tabsArray.toString();
        TreeSet<Tab> result = toTreeSet(tabsJson);

        assertThat(result).containsExactly(
                new Tab("GitHub", "https://github.com"),
                new Tab(null, "https://stackoverflow.com") // Duplicates with null titles and URLs should be included
        );
    }

    @Test
    void testToTreeSetWithTabsHavingMixedCaseTitlesAndUrlsWithSpecialCharactersAndDuplicates() {
        JSONArray tabsArray = new JSONArray()
                .put(new JSONObject().put("title", "google").put("url", "https://www.google.com"))
                .put(new JSONObject().put("title", "GiThUb").put("url", "https://Github.com"))
                .put(new JSONObject().put("title", "STACK Overflow").put("url", "https://stackoverflow.com"))
                .put(new JSONObject().put("title", "GitHub").put("url", "https://github.com"))
                .put(new JSONObject().put("title", "google").put("url", "https://www.google.com"));

        String tabsJson = tabsArray.toString();
        TreeSet<Tab> result = toTreeSet(tabsJson);

        assertThat(result).containsExactly(
                new Tab("GiThUb", "https://Github.com"),
                new Tab("GitHub", "https://github.com"), // keep differently cased URL duplicates
                new Tab("STACK Overflow", "https://stackoverflow.com"),
                new Tab("google", "https://www.google.com")
        );
    }

    @Test
    void testToTreeSetWithVeryLongJSONArray() {
        JSONArray tabsArray = new JSONArray();
        for (int i = 0; i < 10000; i++) {
            tabsArray.put(new JSONObject().put("title", "Tab " + i).put("url", "https://example.com/" + i));
        }

        String tabsJson = tabsArray.toString();
        TreeSet<Tab> result = toTreeSet(tabsJson);

        assertThat(result).hasSize(10000); // Test with a very long JSON array
    }

}