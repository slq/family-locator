# Pierwszy etap i granice prototypu

## Przepływ

UI uruchamia `LocationSharingService` z widocznej Activity. Usługa tworzy powiadomienie
z akcją Stop i prosi Fused Location Provider o pomiary. Room zapisuje każdy przyjęty
punkt. Dla zalogowanego użytkownika synchronizator wysyła najnowszy oczekujący punkt
do Realtime Database. UI subskrybuje pozycje rodziny tylko, gdy jest aktywne.

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

## Następny PR: sesje podglądu i FCM

1. Funkcja serwerowa autoryzuje oglądającego i osobę obserwowaną w tej samej rodzinie.
2. Tworzy sesję 90 s, odnawianą przez widoczną mapę co 30 s.
3. Telefon odbiera sygnał FCM i odczytuje sesję; nie ufa parametrom z samego push.
4. Działająca usługa przełącza żądaną częstotliwość, potwierdza sesję i wysyła świeży punkt.
5. UI oznacza „na żywo” dopiero po potwierdzeniu i nowym pomiarze.
6. Lokalny zegar monotoniczny ogranicza sesję także bez internetu; lokalna pauza
   ma zawsze pierwszeństwo. Kilku oglądających nie wyłącza sobie nawzajem sesji.

Zimny start usługi z tła wymaga oddzielnej obsługi warunków Androida i uprawnień.
Nie obiecujemy wybudzenia po Force Stop. Wysoki priorytet FCM jest zarezerwowany
dla uzasadnionych, widocznych powiadomień, nie dla cichego okresowego odpytywania.

## Dalsze etapy

Zaproszenia z krótkim TTL, zarządzanie urządzeniami, automatyczna retencja serwera,
detekcja ruchu i geofencing. Od 0.2.0 dostępne są stabilne podpisywanie release
i ręcznie uruchamiane aktualizacje z GitHub Releases; szczegóły w `UPDATES.md`.
