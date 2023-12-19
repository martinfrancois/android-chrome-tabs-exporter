package com.github.martinfrancois;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Comparator;
import java.util.Map;
import java.util.TreeSet;

import static com.github.martinfrancois.Constants.*;

public class AndroidChromeTabsProcessor {

    private AndroidChromeTabsProcessor() {
        throw new UnsupportedOperationException("AndroidChromeTabsProcessor is a utility class and should not be instantiated.");
    }

    public static TreeSet<Map<String, String>> toTreeSet(String tabsJson) {
        // Using TreeSet to automatically sort and deduplicate entries based on URL
        TreeSet<Map<String, String>> sortedTabs = new TreeSet<>(Comparator.comparing(entry -> entry.get(HEADER_URL)));

        if (tabsJson != null && !tabsJson.isEmpty()) {
            JSONArray tabArray = new JSONArray(tabsJson);

            for (int i = 0; i < tabArray.length(); i++) {
                JSONObject tab = tabArray.getJSONObject(i);
                String title = tab.optString(KEY_TITLE);
                String url = tab.optString(KEY_URL);

                if (title.isBlank() && url.isBlank()) {
                    continue;
                }
                sortedTabs.add(Map.of(HEADER_TITLE, title, HEADER_URL, url));
            }
        }
        return sortedTabs;
    }
}
