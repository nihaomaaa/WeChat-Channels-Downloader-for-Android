# Liquid navigation provenance

The five Kotlin files in this folder adapt the Apache-2.0 catalog components from
[Kyant0/AndroidLiquidGlass 1.0.6](https://github.com/Kyant0/AndroidLiquidGlass/tree/1.0.6/catalog/src/main/java/com/kyant/backdrop/catalog).
This is the same original project credited by Miuix's liquid glass example.

Changes: package/import names, explicit application dark theme instead of system-only theme,
and selected semantics on tabs. Preserve the lens shader, combined backdrop, chromatic
aberration, damped drag spring, interactive highlight and geometry-dependent deformation.
The rendering implementation remains in the pinned Backdrop 1.0.6 Maven dependency.

Android 13+ uses RuntimeShader refraction. Earlier Android versions use Backdrop's supported
fallback; they cannot reproduce AGSL refraction. See the release screenshots/gesture tests.
