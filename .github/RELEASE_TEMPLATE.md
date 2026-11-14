# SteamPulse & Deals vX.Y.Z

> **Release-datum:** ÅÅÅÅ-MM-DD  
> **Appversion:** `X.Y.Z` (`versionCode: N`)  
> **Plattform:** Android 7.0+ (`minSdk 24` / `targetSdk 36`)  
> **Paket-ID:** `com.aistudio.steampulse.kxvrqm`

Kort sammanfattning av vad som är nytt i denna version av **SteamPulse & Deals**.

---

## ✨ Nya funktioner

- **Puls & Deals:** <!-- T.ex. Nya speltitlar tillagda i 2026-katalogen, förbättrad sparkline-graf eller uppdaterad SEK/EUR-växelkurs. -->
- **Bevakningslista (Cloud Firestore):** <!-- T.ex. Snabbare synkronisering av prisbevakningar och notifieringsnivåer. -->
- **AI-Rådgivare (Gemini):** <!-- T.ex. Nya promptmallar eller förbättrad deal-analys i Prisjägare / Spelcoach / Spelscout. -->
- **Bildstudio (1K / 2K / 4K):** <!-- T.ex. Nya bildformat eller förbättrad bildremix-hantering. -->
- **GitHub Releases & Uppdateringar:** <!-- T.ex. Förbättrad versionskontroll och asset-översikt. -->

---

## 🐛 Bugfixar

- **Prisjämförelse & Valuta:** <!-- T.ex. Åtgärdade avrundningsfel vid växling mellan SEK och EUR i detaljvyn. -->
- **Autentisering & Synk:** <!-- T.ex. Åtgärdade tillståndshantering vid Google Sign-In och återupptagen nätverksanslutning. -->
- **UI & Navigering:** <!-- T.ex. Justerade layout och touch-ytor för mindre skärmar och surfplattor. -->

---

## 🚀 Uppgraderingsinstruktioner

### För användare (uppdatera appen på Android)
1. Öppna **SteamPulse** och tryck på versionsknappen (`vX.Y.Z` / **Uppdatera**) längst upp till höger i appen, eller gå direkt till **Releases** på GitHub.
2. Välj denna release (`vX.Y.Z`) och tryck på **Ladda ner APK** under *Nedladdningsbara filer* (`app-release.apk` rekommenderas, annars `app-debug.apk`).
3. Öppna den nedladdade APK-filen på din Android-enhet och bekräfta installationen ovanpå befintlig version.
4. **Dina data bevaras automatiskt:** Din bevakningslista och dina riktpriser synkas via ditt Google-konto i Cloud Firestore — du behöver inte konfigurera om någonting efter uppdateringen. *(Tips: om du installerar en annan signeringsvariant än tidigare kan Android kräva att du först avinstallerar den gamla versionen innan du loggar in igen.)*

### För utvecklare (checklista inför taggning)
- [ ] Uppdatera `versionCode` och `versionName` i `app/build.gradle.kts`.
- [ ] Lägg till ändringarna under motsvarande rubrik i `CHANGELOG.md`.
- [ ] Verifiera bygget lokalt (`gradle :app:testDebugUnitTest` och APK-bygge).
- [ ] Skapa och pusha tagg för att trigga GitHub Actions-releasen:
  ```bash
  git tag vX.Y.Z
  git push origin vX.Y.Z
  ```

---

## 📦 Bifogade filer

| Fil | Beskrivning | Rekommenderad för |
| --- | --- | --- |
| `app-release.apk` | Signerad produktions-APK *(om release-signing är konfigurerad)* | Vanliga användare |
| `app-debug.apk` | Test- och utvecklings-APK | Testning & förhandsvisning |
