# User story: Automatyczne wznowienie lokalizacji po zakończeniu procesu

**Status:** Zaimplementowana; testy na fizycznych Samsungach pozostają do wykonania.

**Jako** użytkownik Family Locator  
**chcę**, aby aplikacja automatycznie wznawiała wcześniej włączoną usługę po zakończeniu procesu przez Androida,  
**aby** udostępnianie lokalizacji i sterowanie rodzinne mogły działać bez ponownego otwierania aplikacji.

## Kryteria akceptacji

1. Po zakończeniu procesu przez system Android może odtworzyć usługę bez otwierania ekranu aplikacji.
2. Usługa przywraca poprzednie konto i tryb działania:
    - okresowy — wznawia pomiary i synchronizację;
    - dokładny — wykorzystuje wyłącznie pozostały czas pierwotnej sesji;
    - pauza — przywraca sterowanie rodzinne bez pomiarów i wysyłania lokalizacji.
3. Jeżeli termin dokładnych pomiarów minął podczas przerwy, aplikacja wraca do trybu okresowego. Zmiana zegara telefonu nie przedłuża sesji.
4. Po odtworzeniu widoczne jest powiadomienie informujące o aktualnym stanie usługi.
5. Kolejka pomiarów offline pozostaje zachowana po ubiciu procesu.
6. Pełne wyłączenie usługi z aplikacji lub powiadomienia trwale wyłącza automatyczne wznowienie. Pauza zachowuje dotychczasowe usuwanie niewysłanych pomiarów.
7. Brak poprawnego zapisu sesji, cofnięcie wymaganych uprawnień lub niezgodność konta zatrzymują odtwarzanie bez pętli ponownych uruchomień.
8. Oczekiwanie na odtworzenie konta Firebase jest rozróżniane od braku zalogowania.
9. Wznowienie zachowuje ochronę przed powtarzaniem poleceń i pierwszeństwo lokalnych zmian. Sesja uruchomiona zdalnie wymaga weryfikacji aktualnego żądania przed wznowieniem pomiarów.

## Wskazówki implementacyjne

- Wykorzystać `START_STICKY` i obsłużyć odtworzenie z `intent == null`.
- Zapisywać atomowo stan sesji w SharedPreferences: włączenie usługi, konto lub tryb lokalny, tryb pomiarów, termin monotoniczny, `BOOT_COUNT` oraz stan ochrony poleceń zdalnych.
- Oddzielić świadome zatrzymanie od zwalniania zasobów w `onDestroy`.
- Zachować istniejące uprawnienia i schemat Firebase.

## Weryfikacja

- Testy odtwarzania trybów, terminów, konta, uprawnień i poleceń zdalnych.
- Testy zachowania kolejki offline oraz pełnego zatrzymania.
- Rzeczywiste ubicie procesu aplikacji debug na emulatorze i próby na Samsungach przy wygaszonym ekranie.
- Poprawne przejście testów jednostkowych, budowania APK, lint oraz istniejącego testu sterowania rodzinnego.

## Wynik weryfikacji — 2026-10-17

- 47 testów jednostkowych: poprawnie.
- Budowanie debug APK i androidTest oraz lint: poprawnie (lint bez błędów).
- 33 testy reguł RTDB: poprawnie; reguły i schemat nie zostały zmienione.
- Sterowanie rodzinne i historia z emulatorami Auth/RTDB: 3 testy poprawnie.
- Zestaw Androida 35: 16 zaliczonych; 3 próby opt-in uruchomione osobno.
- `Test-BackgroundRecovery.ps1`: rzeczywiste ubicie procesu dla okresowego,
  dokładnego (również powtórnie), wygasłego dokładnego i pauzy; powrót powiadomienia,
  trwałość kolejki Room, odrzucenie innego konta bez odrzucania kolejki oraz pełny Stop:
  poprawnie.
- Próby na fizycznych Samsungach z wygaszonym ekranem: do wykonania według
  `docs/DEVICE_TESTS.md`; podczas implementacji dostępny był tylko emulator.

## Ograniczenia

Autostart po restarcie telefonu pozostaje poza zakresem. „Wymuś zatrzymanie” wymaga ponownego działania użytkownika. Termin wznowienia usługi zależy od Androida.