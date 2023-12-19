package com.github.martinfrancois;

import com.github.martinfrancois.model.Tab;
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

    public static TreeSet<Tab> toTreeSet(String tabsJson) {
        // Using TreeSet to automatically sort and deduplicate entries based on URL
        TreeSet<Tab> tabs = new TreeSet<>(Comparator.comparing(Tab::url));

        if (tabsJson != null && !tabsJson.isEmpty()) {
            JSONArray tabsArray = new JSONArray(tabsJson);

            for (int i = 0; i < tabsArray.length(); i++) {
                JSONObject tab = tabsArray.getJSONObject(i);
                String title = tab.optString(KEY_TITLE);
                String url = tab.optString(KEY_URL);

                if (title.isBlank() && url.isBlank()) {
                    continue;
                }
                tabs.add(new Tab(title, url));
            }
        }
        return tabs;
    }
}
