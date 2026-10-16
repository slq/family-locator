# Konfiguracja rodzinnego prototypu

Ten etap nie wdraża Cloud Functions ani FCM. Pozwala przetestować lokalizację
i wymianę ostatniej pozycji, wspólne nazwy i zdalny tryb dokładny. Sam Auth + Realtime Database mieści się w możliwościach
Spark; Maps SDK wymaga skonfigurowanego billingu w projekcie Google Cloud.

## 1. Firebase i aplikacja Android

1. Utwórz projekt w https://console.firebase.google.com/ (Analytics nie jest potrzebne).
2. Dodaj aplikację Android z pakietem `pl.slqpl.rodzina`.
3. Utwórz **Realtime Database** (nie Firestore), najlepiej w europejskiej lokalizacji
   `europe-west1`. Wybierz **Locked mode**, nigdy publiczny tryb testowy.
4. W Project settings pobierz aktualny `google-services.json` i umieść go w `app/`.
   Pobierz plik po utworzeniu bazy, aby zawierał również jej `firebase_url`.
5. W Authentication → Sign-in method włącz **Email/Password**.
6. W Authentication → Users utwórz osobne konto dla każdego telefonu; zanotuj UID.
   Nie używaj jednego konta na kilku urządzeniach (ścieżka pozycji jest indeksowana UID).

Jeśli wcześniej zarejestrowałeś aplikację z inną nazwą pakietu, dodaj nową aplikację
Android w tym samym projekcie Firebase i pobierz jej `google-services.json`.
Konta użytkowników, baza i przypisania do rodziny pozostają w tym samym projekcie.
Na telefonie nowy pakiet jest osobną aplikacją: trzeba ponownie przyznać uprawnienia
i zalogować się. Zatrzymaj udostępnianie w starej aplikacji przed uruchomieniem nowej.
Zmiana pakietu nie zmienia SHA-1, jeśli używasz tego samego klucza podpisującego;
w ograniczeniach klucza Maps dodaj jednak nową nazwę pakietu.

## 2. Wdrożenie reguł

Reguły znajdują się w `firebase/database.rules.json`. Możesz wkleić ich zawartość
do Realtime Database → Rules i wybrać Publish albo użyć lokalnego CLI:

```sh
npm ci --ignore-scripts
npx firebase login
npx firebase deploy --only database --project TWOJ_PROJECT_ID
```

**Przy aktualizacji do 0.7.0 ponownie wklej aktualne reguły w Realtime Database → Rules i kliknij Publish przed instalacją APK.** Zawierają
indeks `familyId`, odczyt listy własnej rodziny oraz walidację nazw, żądań i potwierdzeń.
Od 0.4.0 zezwalają również na zapis własnej ikonki w `memberIcons/{uid}`. Od 0.6.0 obsługują `trackingRequests`, `trackingAcks` i `devices/{uid}/controlReady`.
Od 0.7.0 dodają indeksowaną historię `history/{familyId}/{uid}`,
`historyOwners` oraz granicę usuwania `historyDeletedThrough`. Dotychczasowe
przypisania `memberships` i pozycje pozostają zgodne; nie importuj bazy od nowa.
Lokalna baza Room aktualizuje się automatycznie z zachowaniem kolejki.

Zalogowanie CLI odbywa się na Twoim komputerze. Nie przekazuj tokena logowania
ani pliku konta serwisowego w czacie i nie umieszczaj ich w repozytorium.

## 3. Przypisanie kont do rodziny

W Realtime Database → Data dodaj dla każdego UID ścieżkę:

```text
memberships / UID_DANEJ_OSOBY / familyId = "nasza-rodzina"
```

Przykładowy fragment danych (to demonstracyjne UID, nie prawdziwe konta):

```json
{
  "memberships": {
    "UID_TELEFONU_1": { "familyId": "nasza-rodzina" },
    "UID_TELEFONU_2": { "familyId": "nasza-rodzina" },
    "UID_TELEFONU_3": { "familyId": "nasza-rodzina" }
  }
}
```

Dodawaj konkretne węzły. Nie importuj tego przykładu na korzeń istniejącej bazy,
bo import mógłby nadpisać dane. Nie musisz tworzyć `families`: pojawi się po
pierwszej poprawnej wysyłce. Użytkownicy aplikacji nie mogą zmieniać członkostwa.
Usunięcie `memberships/UID` odbiera kontu dostęp do rodziny i usuwa je z listy oraz
mapy w wersji 0.3.0. Zapisane wcześniej dane nadal pozostają na serwerze; usuń je
osobno jako administrator, jeżeli mają zniknąć również z bazy.

## 4. Opcjonalna mapa

1. W tym samym projekcie Google Cloud włącz billing i **Maps SDK for Android**.
2. Utwórz klucz API ograniczony do tego API i aplikacji Android.
3. Dodaj pakiet `pl.slqpl.rodzina` i SHA-1 certyfikatu użytego do podpisania APK.
   Dla lokalnego debug uzyskasz go poleceniem `./gradlew :app:signingReport`
   (na Windows: `gradlew.bat`). Nie publikuj prywatnego pliku keystore.
4. Do `local.properties` dopisz `MAPS_API_KEY=twoj_klucz`.
5. Zbuduj APK ponownie. Nie konfiguruj Map ID; aplikacja używa zwykłych markerów
   i kół dokładności. Nie wywołuje Routes, Places ani Geocoding API.

Klucz map jest częścią APK; ograniczenia klucza muszą być ustawione po stronie Google.
Alert budżetu nie jest twardym limitem wydatków. Włącz tylko używane API.

## 5. Test dwóch telefonów

Zainstaluj ten sam skonfigurowany APK 0.7.0 na obu telefonach, zaloguj osobne konta, ustaw nazwy osób
i uruchom udostępnianie. Bez klucza map zobaczysz listę pozycji, wiek pomiarów
i baterię. Z kluczem mapa pokaże też znaczniki i obszary dokładności.

W panelu **Moja rodzina** każdy członek może zmienić nazwy widoczne dla wszystkich.
Konta przypisane do rodziny pojawią się nawet bez wcześniejszego pomiaru.

Przy swoim koncie wybierz **Wybierz moją ikonkę**, wskaż jeden z 12 symboli i zapisz.
Wybór jest wspólny dla całej rodziny; inne osoby nie mogą zmieniać Twojej ikonki.
**Dobierz automatycznie** usuwa własny wybór. Domyślne ikony są różne, dopóki
wystarcza symboli w katalogu. Kolor i numer znacznika pozwalają rozróżnić również
osoby wybierające ten sam symbol. Podgląd na liście odpowiada znacznikowi na mapie.
Numery zależą od listy UID, więc mogą zmienić się przy dodaniu lub usunięciu członka.
Odczyt ikon wymaga wersji 0.4.0, a ich zapis aktualnych reguł RTDB.

Od 0.5.0 dotknij ikonki lub nagłówka osoby z napisem **Pokaż na mapie**. Ekran
przewinie się do mapy, przybliży jej ostatnią pozycję i będzie śledzić kolejne pomiary.
Przycisk **Pokaż wszystkich** przywraca dopasowanie widoku do wszystkich zapisanych
pozycji rodziny, aktualizowane przy zmianie współrzędnych. Osoba bez pomiaru ma
nieaktywny wybór na mapie. Wiek ostatniej pozycji jest pokazywany także przy zbliżeniu.

Dotknięcie mapy lub **Przeglądaj ręcznie** wstrzymuje automatyczne przesuwanie kamery.
Znaczniki nadal otrzymują aktualne pozycje. Wybierz **Wznów śledzenie**, **Wznów
dopasowanie** albo ponownie osobę / **Pokaż wszystkich**, aby przywrócić automatyczny
widok. Sam wybór osoby na mapie nie wysyła żądania dokładnych pomiarów.
Sterowanie mapą w 0.5.0 korzysta z reguł RTDB z wersji 0.4.0.

Przy każdej osobie, także przy własnym koncie, są trzy akcje:

1. **Udostępnianie położenia** — zwykłe pomiary co około 3 minuty.
2. **Dokładne pomiary przez 15 min** — żądanie dokładnej pozycji co około 10 sekund,
   następnie powrót do zwykłego udostępniania.
3. **Wyłączenie udostępniania** — bez nowych pomiarów i wysyłek. Ostatnia zapisana
   pozycja pozostaje na mapie z czasem jej pomiaru.

Na telefonie odbierającym najpierw uruchom udostępnianie albo wybierz **Włącz sterowanie
rodzinne bez pomiarów**. Nadaj dokładną lokalizację i powiadomienia. Usługa pozostawia
widoczne powiadomienie także przy wyłączonym udostępnianiu, aby rodzina mogła zdalnie
wznowić pomiary. Lista pokaże pauzę i możliwość zdalnego włączenia.

Na drugim telefonie wybierz dowolną z trzech akcji przy tej osobie. Polecenie najpierw
oczekuje, a następnie otrzymuje potwierdzenie odbiorcy. Wymaga internetu; czeka
maksymalnie 15 minut. Tryb dokładny kończy się 15 min od wysłania, więc po opóźnionym
odbiorze działa tylko przez pozostały czas. Częstotliwość i dokładność zależą od GPS
oraz systemu. Zwykły tryb i pauza po potwierdzeniu nie wygasają.

Każdy członek może zmienić tryb każdego telefonu. Obowiązuje ostatnie polecenie;
nowsza zmiana lokalna także przerywa poprzednią. Przyciski przy własnym koncie
działają lokalnie, również bez internetu. Sprawdź zdalne **wyłącz → włącz** przy
schowanej aplikacji, a także przerwanie trybu dokładnego i wygaszenie po 15 min.

**Wyłącz udostępnianie i sterowanie rodzinne** albo **Wyłącz też sterowanie**
w powiadomieniu kończy całą usługę. Po tym, po restarcie lub Force Stop trzeba
uruchomić ją z aplikacji; wcześniejsze polecenia nie włączą jej ponownie.
Zdalne trzy tryby wymagają wersji 0.6.0 na każdym telefonie. Starsze wersje korzystają
z poprzednich żądań i nie współpracują z nowym sterowaniem.

## 6. Historia od 0.7.0

Po opublikowaniu aktualnych reguł i zainstalowaniu APK 0.7.0 na każdym telefonie
historia zaczyna powstawać podczas udostępniania. Wcześniejszych tras nie da się
odtworzyć z samej ostatniej pozycji; zachowane lokalnie, niewysłane punkty mogą
zostać dosłane po odzyskaniu internetu. Wyłączenie udostępniania zatrzymuje również
zbieranie historii i usuwa niewysłaną kolejkę.

Przy dowolnej osobie wybierz **Historia na mapie** albo przejdź do panelu
**Historia położenia**, wybierz osobę i dzień, a następnie **Pokaż historię na mapie**.
Każdy członek rodziny może przeglądać historię pozostałych. Mapa pokazuje początek,
koniec oraz linię między pomiarami; suwakiem wybierzesz konkretną godzinę i punkt.
**Pokaż całą trasę** przywraca dopasowanie mapy. Przerwy ponad 15 minut nie są
łączone linią. Daty i godziny odnoszą się do strefy telefonu przeglądającego dane.
Zakres to ruchome 30 dni, więc pierwszy dostępny dzień może być niepełny.

Wybierając siebie, użyj **Usuń moją historię do daty…**, wskaż datę i potwierdź.
Usunięcie obejmuje cały wybrany dzień i wcześniejsze dane; dla dzisiaj kończy się
w chwili wykonania operacji, a kolejne nowe pomiary nadal są zapisywane.
Usuwać można własną historię. Zapisana na serwerze granica usuwania blokuje
ponowne dosłanie skasowanych pomiarów, również ze starszej wersji aplikacji.
Jeśli ostatnia pozycja na bieżącej mapie też należy do usuwanego zakresu, znika.
Operacja wymaga internetu. W razie błędu ponów ją, aby dokończyć fizyczne kasowanie;
już zapisana granica nadal ukrywa dane. Nie kasuj ręcznie `historyDeletedThrough`,
bo usuniesz ochronę przed przywróceniem z kolejki offline.

Historia jest pobierana osobno, po jednej osobie i dniu, do 10 000 pomiarów na
zapytanie (więcej niż 25 godzin pomiarów co 10 sekund). Otwarcie bieżącej mapy
nie pobiera historii. Funkcja nie wymaga Functions, FCM, dodatkowego API tras ani
zmiany planu Firebase; odczyty i zapisy podlegają limitom obecnego planu.

Starsze dane są niewidoczne w historii po 30 dniach, a sprzątanie serwera wykonują
aktywne aplikacje: przy otwarciu, przeglądaniu historii i co godzinę pracy usługi,
w ograniczonych partiach, także dla osób, które opuściły rodzinę. Gdy wszystkie
aplikacje są wyłączone, fizyczne dane pozostają do następnego sprzątania. Ścisłe
kasowanie na serwerze bez działających telefonów wymagałoby zadania w chmurze.
Ostatnia pozycja bieżącej mapy ma własny czas pomiaru i nie jest historią trasy.

## Materiały źródłowe

- https://firebase.google.com/docs/android/setup
- https://firebase.google.com/docs/database/android/start
- https://firebase.google.com/docs/database/security
- https://developers.google.com/maps/documentation/android-sdk/get-api-key
