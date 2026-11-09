# Third-party notices

This project is licensed under the MIT license in `LICENSE`. The files listed
here come from other projects and keep their own licenses.

## Material Symbols Outlined font

`src/main/resources/com/github/martinfrancois/ui/MaterialSymbolsOutlined.ttf`
is the variable font "Material Symbols Outlined", version 2.719, copyright
2020-2024 Google LLC. It draws the two theme toggle icons in the window.

- Source: https://github.com/google/material-design-icons (directory
  `variablefont/`)
- License: Apache License 2.0, text in `licenses/Apache-2.0.txt`

## Gradle wrapper

`gradle/wrapper/gradle-wrapper.jar` is the Gradle wrapper, copyright the
original authors, licensed under the Apache License 2.0 (text in
`licenses/Apache-2.0.txt`). Source: https://github.com/gradle/gradle

## Java runtime in the installers

The installers on the releases page (`.deb`, `.rpm`, Arch package, `.msi`, `.dmg`)
bundle a Java runtime that jpackage builds with jlink from Eclipse Temurin 21, the
JDK the build uses. It is OpenJDK, licensed under the GNU General Public License,
version 2, with the Classpath Exception. Its license texts and notices ship with it,
in the `legal` directory of the bundled runtime.

- Source: https://github.com/adoptium/jdk21u, at the tag of the Temurin version that
  `.github/workflows/installers.yml` sets up; the bundled runtime names it in its `release` file

## JavaFX

The release ZIP and the installers bundle the OpenJFX 21 modules from Maven Central. OpenJFX is
licensed under the GNU General Public License, version 2, with the Classpath Exception.

- Source: https://github.com/openjdk/jfx
