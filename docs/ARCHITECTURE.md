# Pierwszy etap i granice prototypu

## Przepływ

UI uruchamia `LocationSharingService` z widocznej Activity. Usługa tworzy powiadomienie
z akcją Stop i prosi Fused Location Provider o pomiary. Room zapisuje każdy przyjęty
punkt. Dla zalogowanego użytkownika synchronizator wysyła najnowszy oczekujący punkt
do Realtime Database. UI subskrybuje listę członków i dane rodziny tylko, gdy jest aktywne.
Podczas udostępniania usługa osobno nasłuchuje żądań dokładnych pomiarów w RTDB.

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

Room zachowuje do 1000 punktów; podczas pracy usuwa punkty starsze niż 24 h.
W MVP synchronizujemy **ostatnią pozycję**, a nie historię. Po potwierdzeniu najnowszej
pozycji wcześniejsze oczekujące punkty tego użytkownika uznajemy za obsłużone.
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

## Lista członków i zdalne pomiary od 0.3.0

Każdy członek może zmieniać wspólne nazwy i wysłać żądanie dokładnych pomiarów.
Roster pochodzi z `memberships`, z indeksowanego zapytania `orderByChild("familyId")`
ograniczonego do własnej rodziny przez `equalTo`. Reguły nie pozwalają pobrać całego
katalogu ani przeglądać innych rodzin. Lista obejmuje konta bez pozycji i pomija
stare pozycje usuniętych członków. Przypisania kont nadal zmienia tylko administrator.

W `families/{familyId}` członkowie czytają wspólne dane:

| Ścieżka | Zapis |
|---|---|
| `memberNames/{uid}` | Dowolny obecny członek; nazwa 1–40 znaków, ma pierwszeństwo przed etykietą GPS |
| `memberIcons/{uid}` | Wyłącznie właściciel; jeden z 12 identyfikatorów lub usunięcie własnego wyboru |
| `locations/{uid}` | Wyłącznie właściciel pozycji; dotychczasowa walidacja |
| `devices/{uid}` | Wyłącznie właściciel: tryb, stan udostępniania, heartbeat serwera i koniec trybu dokładnego |
| `liveRequests/{targetUid}/{requesterUid}` | Wyłącznie nadawca we własnym slocie, obie osoby w tej samej rodzinie |
| `liveAcks/{targetUid}/{requesterUid}` | Wyłącznie odbiorca, identyfikator aktualnego żądania i jego wyliczony koniec |

Żądanie zawiera UUID i `requestedAt` nadany przez serwer. Wygasa dokładnie 15 minut
po tym czasie. Opóźniony odbiór wykorzystuje pozostały czas. Odbiorca przelicza go raz
na zegar monotoniczny; reconnect i zmiana zegara nie odnawiają sesji. Nowe kliknięcie
tworzy nowy UUID. Każdy nadawca ma niezależną sesję, a najpóźniejszy koniec decyduje
o trybie. Osobna lokalna sesja dokładna nie jest skracana przez usunięcie żądania.
Odbiorca filtruje nadawców przez bieżące członkostwo.

Usługa zapisuje wykorzystane UUID lokalnie. Powrót do trybu okresowego, Stop i ponowny
start nie wykonują ponownie tego samego żądania. Stop ma pierwszeństwo i zatrzymuje
nasłuchiwanie. Żądanie wysłane do zatrzymanego telefonu może zostać przyjęte dopiero
po świadomym uruchomieniu udostępniania, o ile jeszcze nie wygasło.

Potwierdzenie odbiorcy dotyczy zmiany trybu, nie uzyskania nowego fixa GPS. UI wymaga
zgodnego UUID potwierdzenia i świeżego aktywnego urządzenia, a wiek oraz dokładność
pozycji pokazuje osobno. Heartbeat jest wysyłany co około 30 s; po 90 s staje się
nieaktualny. `onDisconnect` ustawia urządzenie jako zatrzymane; Stop także wysyła taki
stan. Niedostępne urządzenie nie jest pokazywane jako aktywny tryb dokładny.

Ta wersja nie wymaga FCM, Functions ani zmiany planu Firebase. Realtime listener działa
w już uruchomionej usłudze, również przy wygaszonym ekranie. Zimny start usługi z tła
wymaga osobnej obsługi ograniczeń Androida; nie ma wybudzania po Force Stop.

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

## Dalsze etapy

Zaproszenia z krótkim TTL, zarządzanie urządzeniami, automatyczna retencja serwera,
detekcja ruchu i geofencing. Od 0.2.0 dostępne są stabilne podpisywanie release
i ręcznie uruchamiane aktualizacje z GitHub Releases; szczegóły w `UPDATES.md`.
