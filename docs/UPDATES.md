# Aktualizacje aplikacji z GitHub Releases

Na ekranie głównym znajduje się panel **Aktualizacje**. Użytkownik wybiera
**Sprawdź aktualizacje**, następnie **Pobierz** i **Zainstaluj aktualizację**.
Sprawdzanie jest ręczne; aplikacja nie odpytuje GitHuba w tle.

Przy pierwszej instalacji aktualizacji Android może poprosić o zezwolenie aplikacji
Rodzina na instalowanie aplikacji z tego źródła. Po powrocie z ustawień ponownie
wybierz instalację. Każdą aktualizację zatwierdza użytkownik w instalatorze Androida.
Przed instalacją trzeba zatrzymać pomiary; po aktualizacji uruchomić je ponownie.
Poprawna aktualizacja zachowuje konto, uprawnienia i lokalną bazę.

## Pierwsza wersja z aktualizatorem

Wersję 0.2.0 trzeba zainstalować ręcznie: poprzedni prototyp nie zawiera aktualizatora.
Kolejne aktualizacje mogą już być instalowane z aplikacji.

Wersja debug i release mają różne podpisy. Android nie zastąpi debug APK plikiem
release: pierwsze przejście wymaga usunięcia wersji debug i instalacji release.
Odinstalowanie usuwa lokalne dane i wymaga ponownego logowania. Nie jest potrzebne
przy kolejnych prawidłowo podpisanych aktualizacjach release.
Na działającym emulatorze zachowujemy kanał debug, żeby nie usuwać jego danych.

## Stały klucz podpisujący

Każda rodzinna instalacja release i wszystkie jej aktualizacje muszą używać tego
samego klucza. `scripts/New-SigningKey.ps1` tworzy lokalnie
`signing/rodzina-release.jks` oraz `keystore.properties`. Pliki są ignorowane przez Git.
Skrypt odmawia nadpisania istniejącej konfiguracji. Zachowaj bezpieczną kopię obu
plików: ich utrata uniemożliwia podpisanie kolejnej aktualizacji tej instalacji.

```powershell
.\scripts\New-SigningKey.ps1
```

Uruchom ten skrypt tylko przy pierwszej konfiguracji, jeżeli klucz jeszcze nie istnieje.
Podczas konfiguracji wypisywany jest publiczny SHA-1 certyfikatu. Dodaj go razem
z pakietem `pl.slqpl.rodzina` do ograniczeń klucza Maps w Google Cloud.
SHA-1 release różni się od dotychczasowego SHA-1 debug.

## Zbudowanie wydania lokalnie

Wymagane są Python 3.11+, lokalne `app/google-services.json`, `local.properties`
z kluczem Maps oraz konfiguracja podpisywania. Wersja musi mieć rosnący `versionCode` i nowy
`versionName`. Pierwsza wersja z aktualizatorem to kod 2 i nazwa 0.2.0.

```powershell
.\scripts\Build-Release.ps1 -VersionCode 2 -VersionName 0.2.0
```

Powstaną dwa pliki poza Gitem:

- `dist/rodzina.apk` — podpisana aplikacja z konfiguracją Firebase i Maps;
- `dist/update.json` — pakiet, numer i nazwa wersji oraz SHA-256 konkretnego APK.

Skrypt sprawdza podpis APK i jego rzeczywisty pakiet oraz wersję. Odrzuca build debug.
Pliki muszą pochodzić z tego samego uruchomienia skryptu.
Wersja z listą członków i zdalnymi pomiarami używa `-VersionCode 3 -VersionName 0.3.0`.
Wersja z wyborem ikon używa `-VersionCode 4 -VersionName 0.4.0`.
Wersja z wyborem osoby i automatycznym dopasowaniem mapy używa `-VersionCode 5 -VersionName 0.5.0`.
Przed jej instalacją wdroż aktualne reguły RTDB zgodnie z `SETUP_FIREBASE.md`.

## Publikacja ręczna

1. Otwórz https://github.com/slq/family-locator/releases/new.
2. Utwórz wydanie z tagiem np. `v0.2.0` wskazującym kod tej wersji.
3. Dołącz **oba** pliki: `rodzina.apk` i `update.json`. Zachowaj dokładnie te nazwy.
4. Opublikuj jako zwykłe wydanie. Draft i prerelease nie są pobierane przez aplikację.

GitHub i pliki wydania są publiczne. Nie publikuj `keystore.properties`, klucza
podpisującego ani haseł. APK zawiera konfigurację klienta Firebase oraz klucz Maps;
dostęp do danych nadal egzekwują Auth i reguły, a klucz Maps ograniczają pakiet i certyfikat.

Wydania rodzinne są budowane i podpisywane lokalnie, a oba pliki są dodawane
ręcznie do GitHub Releases. Nie wymagają GitHub Actions ani przesyłania klucza
podpisującego lub haseł do GitHuba. Dotychczasowy workflow **Android prototype**
dotyczy wyłącznie diagnostycznych APK bez konfiguracji chmury.

## Weryfikacja po stronie aplikacji

Aplikacja korzysta z publicznego API najnowszego wydania repozytorium
`slq/family-locator`. Brak wydań albo brak `update.json` oznacza brak aktualizacji.
Nie umieszcza tokena GitHub w APK. GitHub może ograniczyć liczbę anonimowych zapytań;
wtedy trzeba spróbować później.

Aktualizator przyjmuje tylko adresy HTTPS plików wydań tego repozytorium, ogranicza
rozmiar odpowiedzi i APK, a po pobraniu sprawdza SHA-256, tożsamość pakietu,
wyższy numer wersji zgodny z manifestem i ten sam certyfikat podpisujący.
Systemowy instalator dodatkowo weryfikuje APK. FileProvider udostępnia wyłącznie
katalog cache aktualizacji; pozostałe pliki aplikacji pozostają prywatne.

Źródła: https://developer.android.com/studio/publish/app-signing,
https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls(),
https://docs.github.com/en/rest/releases/releases#get-the-latest-release.
