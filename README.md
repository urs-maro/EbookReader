# Inkwell — cititor de ebook-uri pentru Android

Aplicație nativă de citit cărți electronice (Kotlin + Jetpack Compose), construită pentru
tableta Nokia T20 (Android 13) dar funcțională pe orice Android 8.0+ (minSdk 26).

## Ce face

- **EPUB / EPUB3** — parsare completă (container + OPF + spine), cuprins (nav/NCX), copertă, metadate.
- **Efect „page curl"** tip iBooks — îndoirea paginii urmărește degetul, cu umbră și degrade pe
  spatele paginii. Se poate dezactiva din setări (atunci rămâne un slide simplu).
- **Reglaje de citire**: mărime text, 5 fonturi (Literata, Lora, Inter + Serif/Sans de sistem,
  fonturile sunt incluse în aplicație), spațiere rânduri, margini, temă (Alb / Sepia / Noapte),
  luminozitate din aplicație.
- **Mai multe formate**: EPUB, TXT, FB2, CBZ (benzi desenate). MOBI/AZW3 se importă dar deocamdată
  nu se deschid (necesită conversie — vezi „Pași următori").
- **Conversie PDF → ebook**:
  - PDF digital (cu strat de text) → textul e extras cu PDFBox și **re-aranjat ca document
    reflowable**, deci se citește cu aceleași reglaje de font/culoare ca un EPUB.
  - PDF scanat (fără text) → paginile sunt randate ca imagini cu `PdfRenderer` (din Android),
    fără să blocheze aplicația. (OCR opțional e un pas următor.)
- **Bibliotecă/raft** cu copertă, titlu, autor și **progres de citire salvat per carte** (poziția
  se reține automat). Import prin selectorul de fișiere; apăsare lungă pe o carte = ștergere.

## Arhitectură (pachete separate)

```
app/src/main/java/app/inkwell/reader/
├── core/         Modele (BookContent, Chapter, RichBlock, ImagePageProvider)
├── parser/       EPUB, TXT, FB2, CBZ, HtmlToRich, FormatDetector   ← reader-engine (parsare)
├── pdf/          PdfSupport (reflow text + randare pagini scanate)  ← pdf-convert
├── reader/       Paginator, PageRenderer, PaginatedBook, BookOpener, BookPaginationBuilder
├── data/         LibraryRepository, SettingsRepository, BookImporter, modele  ← library
└── ui/           library/ (raft) + reader/ (cititor, PageCurl, SettingsSheet) + theme/
```

Ideea cheie: efectul de curl are nevoie de fiecare pagină ca **bitmap**, așa că paginarea e proprie
(nu WebView-ul din Readium). Astfel EPUB, TXT, FB2, PDF-reflow și paginile-imagine (PDF scanat, CBZ)
devin toate „pagini-bitmap" și trec prin **același** motor de curl. Interfața `BookOpener`/`BookContent`
e pluggable, deci Readium poate fi adăugat ca backend de parsare EPUB fără a atinge restul.

## Cum compilezi

Necesită: Android Studio instalat (pentru JDK/JBR și SDK) sau un JDK 25 + Android SDK (platforma
android-37, build-tools 36). Wrapper-ul Gradle (9.9.1) se descarcă singur la primul build.

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME="C:\Users\Ionut\AppData\Local\Android\Sdk"
cd "C:\Users\Ionut\Downloads\claude local\EbookReader"
.\gradlew.bat assembleDebug
```

APK-ul rezultă în `app\build\outputs\apk\debug\app-debug.apk`.

> Note de versiuni (vezi și `settings.gradle.kts` / `gradle/libs.versions.toml`):
> Gradle 9.9.1, AGP 9.5.1, Kotlin 2.4.21, Compose BOM 2026.09.00, compileSdk 37, minSdk 26.
> AGP 9 are Kotlin încorporat, deci NU se aplică pluginul `kotlin.android`.

## Cum instalezi pe tabletă prin adb

Tableta e conectată wireless (ex. `10.10.10.161:39925`). Dacă `adb` nu e în PATH, folosește calea
completă: `C:\Users\Ionut\AppData\Local\Android\Sdk\platform-tools\adb.exe`.

```powershell
adb devices                                   # confirmă că tableta apare „device"
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell monkey -p app.inkwell.reader -c android.intent.category.LAUNCHER 1
```

Apoi, în aplicație: **Adaugă** → alege un fișier EPUB/PDF/FB2/TXT/CBZ → se deschide la atingere.
În cititor: atinge marginea dreaptă/stânga sau trage cu degetul pentru a întoarce pagina (curl);
atinge centrul pentru bare + iconița de **setări**.

## Fișier de test

`samples/sample.epub` — un EPUB mic (2 capitole) pentru verificare rapidă.

## Pași următori (neincluse în v1)

- Readium Kotlin Toolkit ca backend de parsare EPUB (interfața e deja pregătită).
- MOBI/AZW3: conversie reală (momentan doar import).
- OCR opțional pentru PDF-uri scanate.
- Imagini inline în fluxul reflowable (acum sunt afișate doar coperțile).
- Curl pe colț (corner-peel) pe lângă plierea pe toată înălțimea.
