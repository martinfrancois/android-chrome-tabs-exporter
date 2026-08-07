package com.github.martinfrancois;

import com.github.martinfrancois.model.Tab;
import com.github.martinfrancois.testsupport.CapturedConsole;
import com.github.martinfrancois.testsupport.UtilityClasses;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.TreeSet;

import static com.github.martinfrancois.AndroidChromeTabsProcessor.toTreeSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.web.util.HtmlUtils.htmlEscape;

/**
 * Complements {@link AndroidChromeTabsProcessorTest} by pinning the behaviour that
 * {@link AndroidChromeTabsProcessor} inherits from its two third-party dependencies:
 * {@code org.json} for parsing and {@code spring-web}'s {@code HtmlUtils} for unescaping.
 *
 * <p>These are the assertions that would break if either library were swapped for a
 * lookalike that parses or unescapes differently.
 */
class AndroidChromeTabsProcessorDependencyTest {

    private CapturedConsole console;

    @BeforeEach
    void setUp() {
        console = new CapturedConsole();
    }

    @AfterEach
    void tearDown() {
        console.close();
    }

    private static Tab onlyTabFor(String title, String url) {
        String json = new JSONArray().put(new JSONObject().put("title", title).put("url", url)).toString();
        TreeSet<Tab> tabs = toTreeSet(json);
        assertThat(tabs).hasSize(1);
        return tabs.first();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            Tom &amp; Jerry                | Tom & Jerry
            5 &lt; 6 &gt; 4               | 5 < 6 > 4
            &quot;quoted&quot;            | "quoted"
            caf&eacute;                    | café
            &Uuml;mlaut                    | Ümlaut
            &nbsp;gap                      |  gap
            &#65;&#66;&#67;                | ABC
            &#x1F600;                      | 😀
            &copy; 2026                    | © 2026
            &amp;amp;                      | &amp;
            plain title                    | plain title
            """)
    void unescapesHtmlEntitiesInTitlesUsingSpringHtmlUtils(String rawTitle, String expectedTitle) {
        Tab tab = onlyTabFor(rawTitle.trim(), "https://example.com");

        assertThat(tab.title()).isEqualTo(expectedTitle.trim());
    }

    @Test
    void unescapingIsTheInverseOfSpringHtmlEscapingForRealisticTitles() {
        String original = "Tom & Jerry <b>\"quoted\"</b> café © 2026";

        Tab tab = onlyTabFor(htmlEscape(original), "https://example.com");

        assertThat(tab.title()).isEqualTo(original);
    }

    @Test
    void leavesUnknownEntitiesUntouched() {
        Tab tab = onlyTabFor("&notarealentity; &amp;", "https://example.com");

        assertThat(tab.title()).isEqualTo("&notarealentity; &");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            https://www.google.com          | https://google.com
            http://www.google.com           | http://google.com
            https://WWW.Google.com          | https://Google.com
            https://www.sub.www.google.com  | https://sub.www.google.com
            https://google.com              | https://google.com
            https://wwwgoogle.com           | https://wwwgoogle.com
            https://awww.google.com         | https://awww.google.com
            ftp://www.google.com            | ftp://www.google.com
            www.google.com                  | www.google.com
            """)
    void stripsALeadingWwwOnlyDirectlyAfterAnHttpScheme(String rawUrl, String expectedUrl) {
        Tab tab = onlyTabFor("title", rawUrl.trim());

        assertThat(tab.url()).isEqualTo(expectedUrl.trim());
    }

    @Test
    void urlIsNotHtmlUnescapedOnlyTheTitleIs() {
        // removeWww is applied to the URL, htmlUnescape only to the title.
        Tab tab = onlyTabFor("plain", "https://example.com/?a=1&amp;b=2");

        assertThat(tab.url()).isEqualTo("https://example.com/?a=1&amp;b=2");
    }

    @Test
    void reportsTheNumberOfTabsFoundBeforeDeduplication() {
        String json = new JSONArray()
                .put(new JSONObject().put("title", "Google").put("url", "https://www.google.com"))
                .put(new JSONObject().put("title", "Google again").put("url", "https://www.google.com"))
                .put(new JSONObject().put("title", "GitHub").put("url", "https://github.com"))
                .toString();

        TreeSet<Tab> tabs = toTreeSet(json);

        assertThat(tabs).hasSize(2);
        assertThat(console.progress()).containsExactly("Found 3 tabs.");
    }

    @Test
    void emptyStringInputIsTreatedAsNoTabsAndReportsNothing() {
        assertThat(toTreeSet("")).isEmpty();
        assertThat(console.progress()).isEmpty();
    }

    @Test
    void jsonObjectsMissingTheTitleOrUrlKeyFallBackToEmptyStrings() {
        // org.json's optString returns "" for absent keys, never null. A tab whose title and url
        // are both blank is skipped outright; a tab with only a title keeps an empty url, and
        // because the TreeSet is keyed on url, all url-less tabs collapse into a single entry.
        String json = new JSONArray()
                .put(new JSONObject().put("url", "https://github.com"))
                .put(new JSONObject().put("title", "Titled but no url"))
                .put(new JSONObject())
                .toString();

        TreeSet<Tab> tabs = toTreeSet(json);

        assertThat(tabs).containsExactly(
                new Tab("Titled but no url", ""),
                new Tab("", "https://github.com"));
        assertThat(tabs).extracting(Tab::title).doesNotContainNull();
    }

    @Test
    void parsesTheRealDevtoolsPayloadShapeChromeReturns() {
        // Trimmed sample of what /json/list on Chrome for Android actually returns.
        String json = """
                [ {
                   "description": "",
                   "devtoolsFrontendUrl": "/devtools/inspector.html?ws=...",
                   "faviconUrl": "https://github.githubassets.com/favicon.ico",
                   "id": "0",
                   "title": "GitHub &middot; Build software",
                   "type": "page",
                   "url": "https://www.github.com/",
                   "webSocketDebuggerUrl": "ws://.../devtools/page/0"
                }, {
                   "description": "",
                   "id": "1",
                   "title": "Google",
                   "type": "page",
                   "url": "https://www.google.com/"
                } ]
                """;

        TreeSet<Tab> tabs = toTreeSet(json);

        assertThat(tabs).containsExactly(
                new Tab("GitHub · Build software", "https://github.com/"),
                new Tab("Google", "https://google.com/"));
    }

    @Test
    void utilityClassCannotBeInstantiated() throws Exception {
        Throwable thrown = UtilityClasses.invokeConstructorAndCaptureThrowable(AndroidChromeTabsProcessor.class);

        assertThat(thrown)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("utility class");
    }
}
