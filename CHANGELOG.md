# Ändringslogg – SteamPulse & Deals

Alla viktiga uppdateringar och appversioner dokumenteras i denna fil och publiceras automatiskt på appens GitHub Release Page.

## [1.0.0] – 2026-11-14
### Nytt
- **GitHub Releases & App-uppdatering**: Inbyggd release-sida i appen som hämtar senaste versioner, ändringslogg och APK-filer direkt från GitHub Releases API och jämför semantisk version mot installerad build.
- **Puls & Deals**: Live-telemetri för globala Steam-spelare, 16 aktuella titlar med sparkline-grafer, SEK/EUR-växlare, filter och prisjämförelse mellan officiella butiker och keyshops.
- **Molnsynkad Bevakningslista**: Google Sign-In via Jetpack Credential Manager och säker lagring i Cloud Firestore.
- **AI-Rådgivare (Gemini)**: Flerstegs strömmande chatt med rollval (*Prisjägare*, *Spelcoach*, *Spelscout*) och modellval (*Gemini 3.8 Flash*, *Gemini 4 Argon*, *Gemini 3.5 Flash-Lite*).
- **Bildstudio**: AI-bildgenerering och bildremix i **1K**, **2K** och **4K** med **Gemini 3 Pro Image** och **Nano Banana 2.1**.
