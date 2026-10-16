# Family Locator / Rodzina

Prywatna aplikacja Android dla rodziny: Samsung S10, S10 i A54. Kotlin, Compose,
Fused Location Provider, Room i opcjonalny Firebase Realtime Database.

## Status: prototyp 0.8

To pierwszy działający etap, **nie gotowy system zdalnego monitorowania**.
APK bez konfiguracji działa jako lokalny tester GPS. Nie wymaga konta Firebase
ani klucza map. Po konfiguracji obsługuje logowanie, wysyłanie ostatniej pozycji
i podgląd pozycji rodziny. Usługa jest uruchamiana świadomie z ekranu aplikacji.

| Funkcja | Wersja 0.8 |
|---|---|
| Pomiary przy wygaszonym ekranie z widocznym powiadomieniem | Tak; do weryfikacji na telefonach |
| Tryb okresowy / dokładny | Około 3 min / 10 s; dokładny wygasa lokalnie po 15 min |
| Natychmiastowe zatrzymanie z ekranu lub powiadomienia | Tak |
| Lokalny zapis i ograniczona kolejka offline | Room; maks. 300 000 punktów i 30 dni podczas pracy |
| Diagnostyka uprawnień, baterii, wieku i dokładności pomiaru | Tak |
| Logowanie, ostatnie pozycje rodziny | Po konfiguracji Firebase i ręcznym przypisaniu kont |
| Mapa i obszar dokładności | Po dodaniu klucza Maps SDK; bez Map ID |
| Aktualizacje z aplikacji | Od 0.2.0: GitHub Releases, pobranie APK i instalator Androida |
| Lista wszystkich członków i wspólne nazwy | Każdy członek rodziny, również osoby bez zapisanej pozycji |
| Własne ikonki na mapie | 12 symboli do wyboru, kolor i numer członka; wybór synchronizowany z rodziną |
| Wybór osoby i widok całej rodziny | Dotknięcie osoby przybliża jej pozycję; automatyczne dopasowanie i śledzenie kolejnych pomiarów |
| Tryb udostępniania każdej osoby, także siebie | Zwykły, dokładny przez 15 min lub wyłączony; każdy członek może zmienić tryb każdego telefonu |
| Zdalne wznowienie po wyłączeniu udostępniania | Tak, gdy sterowanie rodzinne pozostaje włączone z widocznym powiadomieniem; wymagane 0.6.0 i nowe reguły |
| Automatyczne rozpoznawanie ruchu / tryb 15 min w bezruchu | Następny etap |
| Historia położenia | 30 dni; dowolny członek rodziny i wybrany dzień na osobnej mapie |
| Usuwanie historii do daty | Dane dowolnego członka rodziny, wskazany dzień włącznie; kolejka offline nie przywraca usuniętych punktów |
| Zaproszenia QR, geofencing | Kolejne etapy |
| Automatyczne wznowienie po restarcie / ubiciu procesu | Nie; ponownie uruchom z aplikacji |

`CONCEPT.md` opisuje pełną wizję i alternatywy architektury. Aktualny prototyp
używa Firebase, bez Spring Boot, PostgreSQL i osobnego VPS.

## Interfejs

Aplikacja otwiera się na mapie. Dolna nawigacja rozdziela cztery widoki:

- **Mapa** — pozycje rodziny, skrócona lista i stan udostępniania tego telefonu.
- **Rodzina** — pełna lista osób; dotknięcie wiersza lub znacznika otwiera panel
  z pomiarem, trzema trybami udostępniania i skrótem do historii. Osoby bez
  pozycji również mają dostępny panel i sterowanie.
- **Historia** — wybór osoby i dnia, mapa trasy oraz suwak zapisanych pomiarów.
  Zmiana osoby lub dnia od razu pobiera właściwą historię.
- **Ustawienia** — uruchamianie i pełne zatrzymywanie usługi, logowanie,
  aktualizacje oraz diagnostyka.

Pomiar starszy niż 5 minut jest oznaczany jako stara pozycja, także gdy telefon
nadal potwierdza dostępność. Czas pomiaru i bateria opisują zapisany punkt.
Potwierdzenie zdalnego polecenia jest pokazywane dopiero po odpowiedzi telefonu.
Wybrana zakładka i stan przeglądania są zachowywane przy odtworzeniu ekranu.

## Uruchomienie lokalne

Wymagane: JDK 17, Android SDK 35, Build Tools 35.0.0 i Android Studio obsługujące
AGP 8.9.2. Gradle 8.11.1 jest dołączony jako wrapper. Minimalny Android: 8.0/API 26.

1. Otwórz katalog repozytorium w Android Studio.
2. Pozwól IDE utworzyć `local.properties` ze ścieżką `sdk.dir`.
3. Wybierz moduł `app`, podłącz telefon przez USB i uruchom.
4. Nadaj zgodę na **dokładną lokalizację** oraz powiadomienia.
5. Otwórz **Ustawienia** i wybierz tryb **Dokładny** (15 minut), wyjdź na zewnątrz i zablokuj ekran.

Budowanie i weryfikacja z Windows PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Linux/macOS:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.
Workflow **Android prototype** w GitHub Actions udostępnia ZIP `rodzina-local-debug`
z diagnostycznym APK bez konfiguracji chmury i map. To nie jest wersja release.
Klucz debug z CI może zmieniać się między uruchomieniami; Android może wtedy
wymagać odinstalowania poprzedniego testowego APK (utrata lokalnych danych).
Do trwałych rodzinnych instalacji potrzebny będzie osobny, zachowany klucz release.

## Chmura i mapa

Pełna instrukcja: [docs/SETUP_FIREBASE.md](docs/SETUP_FIREBASE.md).

- identyfikator aplikacji: **`pl.slqpl.rodzina`**;
- konfiguracja klienta: lokalny `app/google-services.json`;
- opcjonalny klucz map: `MAPS_API_KEY` w lokalnym `local.properties` lub zmiennej środowiska;
- logowanie Email/Password; jeden użytkownik Firebase na jeden telefon;
- członkostwo w rodzinie nadaje administrator przez konsolę;
- reguły bazy muszą zostać wdrożone **przed użyciem prawdziwych lokalizacji**.

Punkty zebrane bez logowania mają `ownerUid = null` i nie są później wysyłane.
Zmiana konta wymaga wyłączenia udostępniania i sterowania rodzinnego. Wylogowanie usuwa lokalną bazę punktów.
Wyłączenie samego udostępniania zatrzymuje pomiary i wysyłki oraz usuwa niewysłaną
kolejkę, zachowując odbiór poleceń rodziny. Całą usługę wyłącza osobny przycisk
w aplikacji lub powiadomieniu. Po pełnym zatrzymaniu i restarcie uruchom ją ponownie
z aplikacji; stare polecenia nie są ponawiane.
Zatrzymanie wysyłania pozostawia ostatnią pozycję w chmurze z oryginalnym czasem
pomiaru. Od 0.7.0 historia obejmuje ostatnie 30 dni. Starsze punkty są stopniowo
usuwane podczas pracy aplikacji dowolnego członka rodziny. Gdy wszystkie telefony
są wyłączone, fizyczne sprzątanie czeka na ponowne uruchomienie aplikacji.
Nie ma osobnego serwera ani zaplanowanego zadania w chmurze. Ostatnia pozycja
na bieżącej mapie pozostaje niezależna od retencji historii; ręczne usunięcie
historii usuwa również ostatnią pozycję, jeśli mieści się w wybranym zakresie.

## Testy

```sh
npm ci --ignore-scripts
npm run test:rules
```

Testy reguł korzystają wyłącznie z lokalnego emulatora i projektu
`demo-family-locator`. Nie wymagają logowania ani billingów Google.
Sprawdzają m.in. izolację rodzin, podszywanie się pod inną osobę, cofnięcie
członkostwa, walidację współrzędnych i ochronę przed starszym punktem offline,
listę członków, wspólne nazwy, autorstwo żądań i potwierdzeń. Testy Kotlin sprawdzają
także wygaśnięcie zdalnych sesji, brak ponownego wykonania i potwierdzanie stanu,
30-dniową retencję, zmianę czasu oraz granice usuwania. Testy Android sprawdzają
migrację Room, wybór historii, kamerę trasy i usuwanie na emulatorze Firebase.

Plan testów na telefonach: [docs/DEVICE_TESTS.md](docs/DEVICE_TESTS.md).
Podpisywanie i publikacja aktualizacji: [docs/UPDATES.md](docs/UPDATES.md).
Architektura i następne kroki: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Ochrona konfiguracji

Repozytorium jest publiczne. Nie dodawaj klucza konta serwisowego, haseł ani klucza
podpisującego APK. `google-services.json` zawiera konfigurację klienta, nie sekret
administratora; i tak jest wykluczony z repozytorium. Uprawnienia egzekwują
Firebase Authentication i reguły bazy. Klucz Maps ogranicz do aplikacji Android,
pakietu i SHA-1 certyfikatu oraz API Maps SDK for Android.

Aplikacja nie zapisuje współrzędnych ani haseł w logach. Kopie systemowe aplikacji
są wyłączone. Nie ma reklam, analityki ani usługi automatycznego restartu.
