package com.github.martinfrancois;

import com.github.martinfrancois.model.Tab;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Comparator;
import java.util.TreeSet;

public class AndroidChromeTabsProcessor {

    private AndroidChromeTabsProcessor() {
        throw new UnsupportedOperationException("AndroidChromeTabsProcessor is a utility class and should not be instantiated.");
    }

    public static TreeSet<Tab> toTreeSet(String tabsJson) {
        // Using TreeSet to automatically sort and deduplicate entries based on URL
        TreeSet<Tab> tabs = new TreeSet<>(Comparator.comparing(Tab::url));

        if (tabsJson != null && !tabsJson.isEmpty()) {
            JSONArray tabsArray = new JSONArray(tabsJson);
            System.out.println("Found " + tabsArray.length() + " tabs.");

            for (int i = 0; i < tabsArray.length(); i++) {
                JSONObject tab = tabsArray.getJSONObject(i);
                String title = tab.optString("title");
                String url = tab.optString("url");

                if (title.isBlank() && url.isBlank()) {
                    continue;
                }
                tabs.add(new Tab(title, removeWww(url)));
            }
        }
        return tabs;
    }

    /**
     * Removes the "www." from the beginning of the URL.
     * This makes the URLs more consistent, which ensures
     * that the URLs are sorted and deduplicated correctly.
     * @param url to remove the "www." from
     * @return the URL without the "www."
     */
    private static String removeWww(String url) {
        return url.replaceAll("(?i)^(https?://)www\\.", "$1");
    }
}
