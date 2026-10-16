# Pierwszy etap i granice prototypu

## Przepływ

UI uruchamia `LocationSharingService` z widocznej Activity. Usługa tworzy powiadomienie
z akcją Stop i prosi Fused Location Provider o pomiary. Room zapisuje każdy przyjęty
punkt. Dla zalogowanego użytkownika synchronizator wysyła najnowszy punkt
do bieżącej mapy oraz wszystkie oczekujące punkty w partiach do historii RTDB. UI subskrybuje listę członków i dane rodziny tylko, gdy jest aktywne.
Podczas pracy usługa nasłuchuje poleceń zmiany trybu w RTDB, także przy wyłączonym udostępnianiu.

Nie deklarujemy `ACCESS_BACKGROUND_LOCATION`: ta wersja zaczyna usługę wyłącznie
z widocznej Activity. Usługa może potem kontynuować przy wygaszonym ekranie.
`START_NOT_STICKY` świadomie wymaga ponownego startu po zatrzymaniu procesu.
Nie ma receivera BOOT_COMPLETED ani mechanizmu obchodzącego Force Stop.

## Lokalizacja i bateria

- tryb okresowy: balanced power accuracy, żądane 180 sekund;
- tryb dokładny: high accuracy, żądane 10 sekund;
- wygaśnięcie po 15 minutach na zegarze monotonicznym, niezależnie od sieci i czasu ściennego;
- czas pozostały odświeżany w trakcie pracy procesu; jeżeli proces zostanie uśpiony,
  polityka wraca do trybu okresowego przy następnym wykonaniu;
- odrzucamy niepoprawne współrzędne, nieznaną dokładność, powtórzone pomiary
  i lokalizacje starsze niż 2 minuty według elapsedRealtime;
- częstotliwości są żądane, nie gwarantowane. Brak punktu nie dowodzi braku ruchu.

## Kolejka i prywatność

Room zachowuje do 300 000 punktów; podczas pracy usuwa punkty starsze niż 30 dni.
Synchronizujemy najnowszą pozycję osobno oraz historię partiami po 200 punktów,
od najstarszych ID. Dopiero potwierdzenie historii uznaje daną partię za obsłużoną.
Ostatnia pozycja przyjmuje pomiary do 24 h; starsze punkty nadal trafiają do historii.
UUID punktu jest zapisany w Room, więc ponowienie nie tworzy duplikatu. Migracja
Room 1 → 2 nadaje trwałe klucze istniejącym rekordom bez utraty kolejki. Kolejka
przetrwa awarię procesu; jawne wyłączenie oznacza jej odrzucenie także po restarcie.
Zapis otrzymuje ownerUid w chwili pomiaru. Punkty lokalne nigdy nie przechodzą do chmury.
Synchronizacja sprawdza, czy UID nadal zgadza się z kontem; logowanie/wylogowanie
jest niedostępne podczas pracy usługi. Po zatrzymaniu anulujemy zapisy oczekujące SDK.
Potwierdzony wcześniej punkt pozostaje w bazie serwera.

Jedna osoba/UID oznacza w tym etapie jeden telefon. Podpisany SDK token Firebase
odpowiada za tożsamość. Membership nadawany tylko przez administratora wiąże UID
z rodziną. Nie ufamy familyId wpisanemu przez klienta. Transakcja chroni ostatnią
pozycję przed nadpisaniem starszym pomiarem; reguły egzekwują to niezależnie od klienta.
`receivedAt` jest timestampem serwera, `measuredAt` czasem pomiaru telefonu.
Nie oferujemy ochrony przed właścicielem telefonu fałszującym własną lokalizację.

## Lista członków i sterowanie od 0.6.0

Każdy obecny członek rodziny może zmieniać wspólne nazwy i wybierać dla dowolnej
osoby (także siebie) tryb `periodic`, `live` albo `stopped`. Roster nadal pochodzi
z indeksowanego zapytania `memberships.orderByChild("familyId").equalTo(ownFamily)`.
Obejmuje osoby bez pozycji, pomija pozycje usuniętych członków i nie ujawnia innych rodzin.

| Ścieżka w `families/{familyId}` | Zapis |
|---|---|
| `memberNames/{uid}` | Dowolny obecny członek; wspólna nazwa 1–40 znaków |
| `memberIcons/{uid}` | Wyłącznie właściciel; jeden z 12 symboli albo usunięcie wyboru |
| `locations/{uid}` | Wyłącznie właściciel; walidacja pomiaru i czasu |
| `devices/{uid}` | Wyłącznie właściciel; sharing, mode, controlReady, heartbeat i liveUntil |
| `trackingRequests/{targetUid}` | Dowolny obecny członek tej samej rodziny; UUID, własny requesterUid, mode i czas serwera |
| `trackingAcks/{targetUid}` | Wyłącznie odbiorca; UUID i tryb aktualnego żądania oraz czas odpowiedzi serwera |

Jedna ścieżka polecenia na telefon oznacza, że ostatni zapis serwera zastępuje poprzednie
polecenia od wszystkich nadawców. Polecenie czeka na odbiór maksymalnie 15 min.
Tryb dokładny kończy się 15 min od wysłania; opóźniony odbiór wykorzystuje tylko pozostały
czas. Po przyjęciu tryby zwykły i wyłączony pozostają aktywne do kolejnej zmiany.
Tryb dokładny zawsze wraca do zwykłego udostępniania, również po włączeniu z pauzy.
Monotoniczny zegar kończy sesję bez internetu i nie odnawia jej po reconnect.

Lokalny przycisk działa również bez internetu. Nowsza lokalna zmiana ma pierwszeństwo
przed starszym poleceniem serwera. Nowa usługa ignoruje polecenia wysłane przed jej
uruchomieniem. Wykorzystane UUID i granica lokalnej zmiany chronią przed ponownym
wykonaniem. Usunięcie autora z rosteru kończy jego aktywne zdalne sterowanie.

`stopped` usuwa żądanie lokalizacji z Fused Location Provider, anuluje zadania pomiarów
oraz wysyłki, wycofuje oczekujące zapisy SDK i usuwa niewysłane punkty właściciela
z kolejki Room. Nie usuwa wcześniej potwierdzonej pozycji z serwera. Odbiór poleceń
pozostaje w działającej usłudze pierwszoplanowej z powiadomieniem opisującym pauzę.
To pozwala rodzinie wznowić pomiary przy schowanej aplikacji, bez zimnego startu
usługi lokalizacji z tła. Osobny przycisk **Wyłącz udostępnianie i sterowanie rodzinne**
oraz akcja powiadomienia wyłączają całą usługę. Force Stop i restart też ją kończą.

Potwierdzenie wysyłamy po pomyślnej rejestracji albo usunięciu żądania pomiarów.
Potwierdza zmianę trybu, a nie uzyskanie nowej pozycji GPS. Stan urządzenia i ack są
zapisywane atomowo. Heartbeat co około 30 s, świeżość 90 s i `controlReady` rozróżniają
pauzę z możliwością zdalnego wznowienia od niedostępnego telefonu. `onDisconnect`
wyłącza oba stany. Lista wymaga zgodnego UUID i trybu; osobno pokazuje wygasłe,
oczekujące, zakończone i zmienione polecenia oraz nieaktualne potwierdzenia.

Sterowanie wymaga wersji co najmniej 0.6.0 na obu telefonach i aktualnych reguł RTDB.
Reguły `liveRequests`/`liveAcks` pozostają dla zgodności między starszymi klientami;
0.6.0 korzysta wyłącznie z `trackingRequests`/`trackingAcks`. Nie potrzeba FCM,
Functions ani zmiany planu Firebase. Po pełnym zatrzymaniu trzeba uruchomić
sterowanie świadomie z ekranu aplikacji.

Źródła ograniczeń Androida:
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- https://developer.android.com/develop/sensors-and-location/location/permissions

## Ikony członków od 0.4.0

`FamilyIcons` rozdziela automatyczne symbole na podstawie całego rosteru posortowanego
według UID. Nazwy, kolejność na ekranie i brak pozycji nie zmieniają przypisania.
Zapisane własne wybory mają pierwszeństwo, a automatyczne unikają zajętych symboli.
Powtarzające się własne wybory oraz rodziny większe niż katalog nadal mają osobne
numery znaczników. Kolor jest przypisany do numeru; te same dane dają taki sam wygląd
u wszystkich członków. Zmiana składu rodziny może zmienić numery.

W RTDB zapisujemy tylko identyfikator symbolu, osobno od nazwy i pozycji. Reguły
walidują katalog i UID właściciela. Starsze klienty nie zapisują tej ścieżki, a ich
wysyłki GPS nie nadpisują ikon. Brak wpisu oznacza dobór automatyczny.
`MemberMarker` rysuje bitmapę z symbolem, kolorem i numerem. Używają jej zarówno mapa,
jak i podgląd na liście. Obszar dokładności ma kolor danej osoby. Ikony działają
bez Map ID, przesyłania obrazków i Firebase Storage.

## Widok mapy od 0.5.0

`FamilyOverview` łączy wybór osoby z przewinięciem ekranu do mapy.
`FamilyMapView` zachowuje wybrany UID, tryb automatyczny/ręczny i numer żądania
ponownego przybliżenia. Wybór jest zachowany przy odtworzeniu Activity i resetowany
przy zmianie konta. Usunięcie wybranej osoby z rosteru przywraca całą rodzinę;
przejściowa pusta lista podczas ładowania nie usuwa wyboru.

`FamilyMap` domyślnie dopasowuje kamerę do wszystkich zapisanych pozycji. Dla jednej
pozycji lub identycznych współrzędnych stosuje stałe zbliżenie, dla wielu różnych
punktów `LatLngBounds` z marginesem na znaczniki. Używa rzeczywistych wymiarów mapy
i czeka na jej powiązanie z SDK, bez wymagania pobrania kafelków. Przy wybranej osobie
śledzi tylko jej współrzędne, zachowując widoczne pozostałe znaczniki.

Zmiana współrzędnych aktualizuje widok automatyczny; nazwy, ikony, heartbeat i sam
czas pomiaru nie uruchamiają animacji. Dotknięcie mapy przełącza na przeglądanie
ręczne bez konsumowania gestu. Znaczniki nadal aktualizują się w tym trybie.
Wznowienie i **Pokaż wszystkich** od razu ustawiają kamerę ponownie. Stary lokalny
pomiar nie powiększa granic rodziny; stanowi fallback tylko przy braku pozycji
rodzinnych i zgodności ownerUid z bieżącą sesją. Wybór mapy nie zmienia pomiarów GPS.

## Historia od 0.7.0

`FamilyHistory` zapisuje `history/{familyId}/{uid}/{pointKey}` poza `families`,
aby istniejący listener bieżącej mapy nie pobierał tras. Punkt zawiera współrzędne,
dokładność, baterię, całkowity `measuredAt` w ms i serwerowy `receivedAt`. Ponowienie
może odświeżyć tylko czas odbioru; zmiana treści istniejącego punktu jest odrzucana.
`historyOwners/{uid}` w rodzinie pozwala sprzątać dane również po odejściu osoby.

Widok subskrybuje indeks `measuredAt` jednej osoby i jednego dnia kalendarzowego,
z limitem 10 000 punktów. Granice dnia uwzględniają strefę i zmianę czasu 23/25 h.
Połowy milisekundy w granicach zapytania zachowują włączenie całkowitych timestampów
i typ double również w emulatorze RTDB. Zakres ogranicza retencja i granica usunięcia.
Zmiana sesji, rosteru albo dnia zamyka poprzednią subskrypcję i czyści stan widoku.
Trasa łączy pomiary, nie rekonstruuje przebiegu po drogach; luki ponad 15 min
rozpoczynają nowy odcinek. Osobna mapa nie zmienia ustawień widoku bieżącej rodziny.

Właściciel usuwa własne dane: transakcja najpierw podnosi monotoniczny
`families/{familyId}/historyDeletedThrough/{uid}`, potem kasuje punkty partiami po
1000 i transakcyjnie usuwa ostatnią pozycję, jeśli jej czas mieści się w zakresie.
Równoległy nowszy pomiar przeżywa usuwanie. Reguły blokują stare zapisy także do
`locations`, a Room usuwa skasowane próbki przy synchronizacji i po udanym kasowaniu.
Data obejmuje wskazany lokalny dzień włącznie, a dzisiaj tylko czas do wykonania.
Po błędzie użytkownik może ponowić kasowanie; granica nadal blokuje przywrócenie.
Członkowie rodziny widzą trasy innych obecnych członków, ale nie mogą przesuwać
ich granicy usunięcia. Mogą dokończyć kasowanie punktów już objętych granicą.

Retencja wynosi 30 × 24 h. Klient odrzuca stare próbki, zawęża widok i wykonuje
ograniczone sprzątanie (maks. 5 × 1000 punktów na właściciela w jednym przebiegu)
przy otwarciu aplikacji/historii oraz co godzinę działania usługi. Nie ma zdalnego
harmonogramu: fizyczne usuwanie przy wszystkich wyłączonych aplikacjach jest
odroczone do ich ponownej aktywności. Reguły dopuszczają odczyt wygasłych partii
wyłącznie w ograniczonych zapytaniach przeznaczonych do sprzątania. Bieżąca ostatnia
pozycja jest niezależna od retencji historii, ale respektuje ręczną granicę usunięcia.

## Dalsze etapy

Zaproszenia z krótkim TTL, zarządzanie urządzeniami, retencja serwera niezależna od aktywności telefonów,
detekcja ruchu i geofencing. Od 0.2.0 dostępne są stabilne podpisywanie release
i ręcznie uruchamiane aktualizacje z GitHub Releases; szczegóły w `UPDATES.md`.
