# Family Locator / Rodzina

Prywatna aplikacja Android dla rodziny: Samsung S10, S10 i A54. Kotlin, Compose,
Fused Location Provider, Room i opcjonalny Firebase Realtime Database.

## Status: prototyp 0.1

To pierwszy działający etap, **nie gotowy system zdalnego monitorowania**.
APK bez konfiguracji działa jako lokalny tester GPS. Nie wymaga konta Firebase
ani klucza map. Po konfiguracji obsługuje logowanie, wysyłanie ostatniej pozycji
i podgląd pozycji rodziny. Usługa jest uruchamiana świadomie z ekranu aplikacji.

| Funkcja | Wersja 0.1 |
|---|---|
| Pomiary przy wygaszonym ekranie z widocznym powiadomieniem | Tak; do weryfikacji na telefonach |
| Tryb okresowy / dokładny | Około 3 min / 10 s; dokładny wygasa lokalnie po 15 min |
| Natychmiastowe zatrzymanie z ekranu lub powiadomienia | Tak |
| Lokalny zapis i ograniczona kolejka offline | Room; maks. 1000 punktów i 24 h podczas pracy |
| Diagnostyka uprawnień, baterii, wieku i dokładności pomiaru | Tak |
| Logowanie, ostatnie pozycje rodziny | Po konfiguracji Firebase i ręcznym przypisaniu kont |
| Mapa i obszar dokładności | Po dodaniu klucza Maps SDK; bez Map ID |
| Zdalne zwiększanie częstotliwości przez FCM | Następny etap |
| Automatyczne rozpoznawanie ruchu / tryb 15 min w bezruchu | Następny etap |
| Zaproszenia QR, historia tras, geofencing | Kolejne etapy |
| Automatyczne wznowienie po restarcie / ubiciu procesu | Nie; ponownie uruchom z aplikacji |

`CONCEPT.md` opisuje pełną wizję i alternatywy architektury. Aktualny prototyp
używa Firebase, bez Spring Boot, PostgreSQL i osobnego VPS.

## Uruchomienie lokalne

Wymagane: JDK 17, Android SDK 35, Build Tools 35.0.0 i Android Studio obsługujące
AGP 8.9.2. Gradle 8.11.1 jest dołączony jako wrapper. Minimalny Android: 8.0/API 26.

1. Otwórz katalog repozytorium w Android Studio.
2. Pozwól IDE utworzyć `local.properties` ze ścieżką `sdk.dir`.
3. Wybierz moduł `app`, podłącz telefon przez USB i uruchom.
4. Nadaj zgodę na **dokładną lokalizację** oraz powiadomienia.
5. Wybierz „Dokładne pomiary przez 15 minut”, wyjdź na zewnątrz i zablokuj ekran.

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
Zmiana konta wymaga zatrzymania pomiarów. Wylogowanie usuwa lokalną bazę punktów.
Zatrzymanie wysyłania pozostawia ostatnią pozycję w chmurze z oryginalnym czasem
pomiaru. Ten prototyp nie implementuje retencji danych na serwerze; administrator
może usunąć pozycję w konsoli. Lokalny limit czasu jest egzekwowany podczas pracy
usługi, nie przez zaplanowane kasowanie przy zamkniętej aplikacji.

## Testy

```sh
npm ci --ignore-scripts
npm run test:rules
```

Testy reguł korzystają wyłącznie z lokalnego emulatora i projektu
`demo-family-locator`. Nie wymagają logowania ani billingów Google.
Sprawdzają m.in. izolację rodzin, podszywanie się pod inną osobę, cofnięcie
członkostwa, walidację współrzędnych i ochronę przed starszym punktem offline.

Plan testów na telefonach: [docs/DEVICE_TESTS.md](docs/DEVICE_TESTS.md).
Architektura i następne kroki: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Ochrona konfiguracji

Repozytorium jest publiczne. Nie dodawaj klucza konta serwisowego, haseł ani klucza
podpisującego APK. `google-services.json` zawiera konfigurację klienta, nie sekret
administratora; i tak jest wykluczony z repozytorium. Uprawnienia egzekwują
Firebase Authentication i reguły bazy. Klucz Maps ogranicz do aplikacji Android,
pakietu i SHA-1 certyfikatu oraz API Maps SDK for Android.

Aplikacja nie zapisuje współrzędnych ani haseł w logach. Kopie systemowe aplikacji
są wyłączone. Nie ma reklam, analityki ani usługi automatycznego restartu.
