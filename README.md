# android-chrome-tabs-exporter

Exports every open tab of Chrome on an Android phone into an Excel file, over USB.

## What it does

A small JavaFX desktop app with one button. When you click "Export Android Tabs" it:

1. Downloads Google's `platform-tools` archive for your operating system from
   `https://dl.google.com/android/repository/platform-tools-latest-<linux|darwin|windows>.zip`
   and unpacks only the `adb` binary into the temporary directory.
2. Ends the first process named `adb` it finds running on the computer, so the port
   forward does not fail with "Address already in use".
3. Runs `adb forward tcp:50000 localabstract:chrome_devtools_remote`. If port 50000 is
   taken it moves to 50001, then 50002, and so on.
4. Requests `http://localhost:<port>/json/list`, the DevTools endpoint on which Chrome
   lists its open pages.
5. Keeps the `title` and `url` of every entry. It unescapes HTML entities in the title,
   strips a leading `www.` after `http://` or `https://`, drops entries whose title and
   URL are both blank, and collapses entries with the same URL into one row. The first
   title seen for a URL is the one written.
6. Writes `<yyyy-MM-dd> Android Tabs.xlsx` into the directory you chose, rows sorted by
   URL, and opens that directory in your file manager.
7. Deletes the `adb` binary it extracted.

## Requirements

- Java 21 or newer.
- Network access on every run, for the `platform-tools` download.
- An Android phone with Chrome, a USB cable, and USB debugging enabled on the phone.

The build and the tests run on Linux in CI. The download step has separate code paths for
Windows and macOS, but no automated check covers them.

## Install

Either download `android-chrome-tabs-exporter.jar` from the latest entry on the releases
page, or build it:

```bash
./gradlew shadowJar
```

The JAR lands in `build/libs/android-chrome-tabs-exporter.jar`.

## Phone setup

1. Open Settings, then About phone, and tap "Build number" seven times. This unlocks
   Developer options.
2. Open Settings, then System, then Developer options, and switch on "USB debugging".
   The menu names differ a little between phone makers.
3. Connect the phone with a USB cable, unlock it, and open Chrome.
4. When the phone shows "Allow USB debugging?", tap Allow. Until you do, the app prints
   "Please accept the USB debugging prompt on your phone" and retries every 3 seconds.

## Usage

```bash
java -jar android-chrome-tabs-exporter.jar
```

In the window that opens, click "Choose Directory", pick where the Excel file should go,
then click "Export Android Tabs". The text area below the button shows progress and
errors. Messages you may see:

- "No android device found." The phone is not connected, not unlocked, or USB debugging
  is off. The app retries every 3 seconds until a device shows up.
- "Failed to connect to the phone." The port forward worked but nothing answered on the
  DevTools endpoint. Open Chrome on the phone and try again.
- "adb command did not behave as expected." adb printed an error the app does not
  recognise. Its full output is in the text area.

## Output

One sheet named "Tabs" with two columns, a sky blue header row, and an autofilter over
every row. For the synthetic input in `examples/tabs.json` the file contains:

| Title                          | URL                                                         |
| ------------------------------ | ----------------------------------------------------------- |
| Recipe: Café au lait           | http://recipes.example.net/cafe-au-lait                     |
| Docs & Guides · Getting started | https://docs.example.org/getting-started?lang=en&section=2 |
| Example Domain                 | https://example.com/                                        |
| Tickets 2026 <early bird>      | https://shop.example.com/tickets/2026                       |

Six entries went in: one was blank and two shared the URL `https://www.example.com/`, so
four rows come out, with `www.` removed and `&amp;`, `&middot;`, `&eacute;`, `&lt;` and
`&gt;` in the titles decoded.

## Example without a phone

`examples/tabs.json` is an invented `/json/list` response in the shape Chrome returns.
The test `ExampleExportTest` pushes it through the same parse, deduplicate and write steps
the app uses and leaves the workbook in `build/examples/tabs.xlsx`:

```bash
./gradlew test --tests 'com.github.martinfrancois.ExampleExportTest'
```

The test asserts the four rows shown above.

## Supported Chrome versions

The code has no version check. It connects to the abstract socket
`chrome_devtools_remote` on the phone and parses whatever `/json/list` returns as a JSON
array of objects with `title` and `url` fields. Any Chrome for Android that exposes that
socket and answers that endpoint works; the entries' `type` field is ignored, so every
target Chrome lists ends up in the file. Browsers that use another socket name, for
example WebView-based ones (`webview_devtools_remote_<pid>`), are out of reach without
changing the socket name in `AdbHandler`.

## Limitations

- The app ends the first process named `adb` it finds before forwarding, which may be an
  adb server started by Android Studio or a terminal.
- `platform-tools` is downloaded again on every export. Without network access the
  download throws and the export ends with a stack trace in the text area.
- While no device is attached the app retries without limit. Close the window to stop.
- URLs are compared case-sensitively, so `https://Example.com` and `https://example.com`
  produce two rows.
- Only title and URL are exported. Nothing else from the DevTools listing is kept.
- The released JAR bundles Linux JavaFX libraries and supports Linux only. Build from
  source on another operating system; those builds have not been verified.

## Development

```bash
./gradlew build
```

This compiles, runs the tests and enforces a line coverage gate of 80%. The adb tests
download the real `platform-tools` archive and execute the real `adb` binary, so they need
network access. The UI tests run the real JavaFX toolkit headlessly through Monocle and
need no display.

## License

MIT, see `LICENSE`. The bundled Material Symbols font is Apache 2.0, see `NOTICE.md`.
Both JARs include these notices and the Apache license text under
`META-INF/android-chrome-tabs-exporter/`.
