# Test akceptacyjny: S10, S10, A54

Testuj z wiedzą i zgodą właściciela każdego telefonu. Wyniki CI nie potwierdzają
działania sprzętowego GPS, Doze, usług Google Play ani ograniczeń One UI.

Zapisz model, wersję Androida/One UI, stan baterii na początku i końcu, ustawienia
oszczędzania energii, czas ostatniego pomiaru i czas potwierdzenia przez serwer.
Nie wrzucaj prawdziwych współrzędnych do publicznych issues.

| Próba | Oczekiwane zachowanie |
|---|---|
| Pierwsze uruchomienie bez Firebase i map | Ekran działa; komunikat o trybie lokalnym |
| Odmowa lokalizacji / tylko przybliżona lokalizacja | Pomiar nie startuje; czytelne wyjaśnienie i ustawienia |
| Odmowa powiadomień | Pomiar nie startuje bez widocznej informacji systemowej |
| Tryb dokładny na zewnątrz, potem ekran wyłączony na 20 min | Świeże punkty; po 15 min powrót do żądania 3 min |
| Zmiana zegara podczas trybu dokładnego | Nie przedłuża 15-minutowej sesji |
| Wyłączenie udostępniania w powiadomieniu | Pomiary i wysyłki kończą się; odbiór poleceń pozostaje, powiadomienie opisuje pauzę |
| Wyłącz też sterowanie w powiadomieniu | Cała usługa kończy się; brak dalszych wysyłek i zdalnego wznowienia |
| Utrata internetu na 5 min, potem powrót | Room przechowuje punkty; wszystkie trafiają do historii, ostatni również na bieżącą mapę |
| Stop bez internetu, potem powrót sieci | SDK nie wysyła oczekujących zapisów po zatrzymaniu |
| Logowanie po teście lokalnym | Stare punkty lokalne nie są wysyłane |
| Wylogowanie | Wymaga wyłączenia całej usługi, także sterowania; lokalne punkty są usuwane |
| Konto bez membership | Brak danych rodziny i brak udanej wysyłki |
| Dwa różne UID tej samej rodziny | Każdy widzi pozycje obu telefonów |
| Członek rodziny bez pomiaru | Widoczny na liście z informacją o braku pozycji |
| Zmiana nazwy innej osoby | Nowa nazwa widoczna na obu telefonach i mapie; kolejne pomiary jej nie nadpisują |
| Różne osoby na mapie bez własnych ikon | Osobne symbole, kolory i numery zgodne z listą rodziny |
| Wybór własnej ikonki i ponowny start | Symbol zachowany i widoczny u pozostałych członków; wysyłki GPS go nie zmieniają |
| Dwie osoby wybierają ten sam symbol | Różne numery i kolory pozwalają je rozpoznać |
| Dobór automatyczny i zmiana nazwy | Automatyczny symbol jest przywracany; sama zmiana nazwy nie zmienia numeru i symbolu |
| Wybór ikonki na starych regułach / offline | Czytelny błąd; brak potwierdzenia zapisu bez odpowiedzi serwera |
| Dotknięcie osoby z pozycją na liście | Ekran przewija się do mapy; zbliżenie tej osoby i wyróżnienie znacznika |
| Nowy pomiar wybranej osoby | Mapa podąża za nową pozycją; zmiana samej nazwy nie rusza kamerą |
| Pokaż wszystkich / nowe współrzędne rodziny | Wszystkie zapisane pozycje mieszczą się w widoku z marginesem |
| Dwie osoby z tymi samymi współrzędnymi / jeden pomiar | Poprawne zbliżenie bez błędu pustych granic |
| Ręczne przesunięcie, dotknięcie znacznika lub zoom | Automatyczna kamera wstrzymana, znaczniki nadal aktualne; widoczna opcja wznowienia |
| Wznów śledzenie / ponowne wybranie tej samej osoby | Natychmiastowy powrót do pozycji i dalsze śledzenie |
| Osoba bez pozycji / usunięta osoba | Nieaktywny wybór bez pozycji; po usunięciu wybranej osoby powrót do całej rodziny |
| Zdalne 15 min, odbiorca udostępnia z wygaszonym ekranem | Najpierw oczekiwanie, potem potwierdzenie i tryb dokładny; po 15 min tryb okresowy |
| Żądanie do telefonu offline | Oczekiwanie; po powrocie tylko pozostała część 15 min, po wygaśnięciu bez zmiany trybu |
| Zdalne wyłącz → zwykłe / dokładne przy schowanej aplikacji | Brak pomiarów i wysyłek podczas pauzy; po nowym poleceniu wznowienie i potwierdzenie telefonu |
| Zdalne zwykłe udostępnianie w trakcie dokładnego | Natychmiastowe przerwanie dokładnego; starszy tryb nie wraca po reconnect |
| Każda akcja przy sobie i innej osobie | Te same trzy przyciski; własne akcje działają także bez internetu |
| Żądanie do całkowicie zatrzymanej usługi / starej wersji | Brak fałszywego potwierdzenia; usługa nie uruchamia się sama; wymagane 0.6.0 na obu telefonach |
| Odbiorca wybiera tryb okresowy lub Stop | Pomiar dokładny przerwany; stare UUID nie uruchamia go ponownie po reconnect/starcie |
| Kilku nadawców i powtórne kliknięcie | Najnowsze polecenie zastępuje poprzednie; nowy UUID dokładnego odnawia 15 min; lokalna zmiana przerywa starsze polecenia |
| Zanik sieci w trakcie sesji dokładnej | Lokalny zegar kończy sesję; przeterminowany heartbeat nie jest pokazywany jako aktywny |
| Historia dowolnego członka, dzisiaj / wczoraj | Oddzielna mapa i właściwy dzień; wybór nie zmienia bieżącej mapy rodziny |
| Suwak czasu, Pokaż całą trasę, jeden punkt / pusty dzień | Właściwa godzina, dokładność i bateria; poprawna kamera albo czytelny brak danych |
| Przerwa w pomiarach ponad 15 minut | Brak linii łączącej odległe czasowo odcinki |
| Historia po 30 dniach / pierwszy niepełny dzień / zmiana czasu | Zakres 30 × 24 h, prawidłowe lokalne daty 23/25 h; starsze punkty sprzątane przy aktywnej aplikacji |
| Usunięcie własnej historii do wczoraj | Potwierdzenie; wskazany dzień włącznie znika na obu telefonach, dzisiaj pozostaje |
| Usunięcie historii do dzisiaj przy aktywnym udostępnianiu | Dotychczasowe punkty znikają; nowsze są nadal zapisywane; stara ostatnia pozycja nie wraca |
| Usuwanie innej osoby / inna rodzina | W UI brak akcji; reguły odrzucają zapis granicy usuwania |
| Usunięcie, potem synchronizacja starej kolejki offline | Usunięte punkty nie wracają; nowe trafiają do historii |
| Aktualizacja 0.6 → 0.7 z punktami offline | Room zachowuje punkty i nadaje trwałe klucze; ponowienie nie dubluje historii |
| Brak internetu / stare reguły przy historii | Czytelny błąd i ponowienie; brak fałszywego potwierdzenia kasowania |
| Cofnięcie członkostwa w konsoli | Kolejne odczyty/zapisy odrzucane; aplikacja sygnalizuje błąd |
| Force Stop / restart telefonu | Bez cichego wznowienia; ponowny start z ekranu |
| GPS wyłączony / budynek | Brak świeżego punktu lub gorsza dokładność; widoczny wiek pomiaru |
| Oszczędzanie baterii i nocne Doze | Zapisz faktyczne opóźnienia; nie zakładaj ciągłości |

Najpierw testuj S10 jako nadajnik i A54 jako odbiornik, potem zamień role.
Na Samsungu sprawdź ustawienia baterii aplikacji i listę Nigdy nieusypiane aplikacje.
Porównaj zużycie przez podobny dzień bez aplikacji oraz z trybem okresowym;
test 30-minutowy nie wystarcza do wiarygodnego oszacowania całodziennego zużycia.

Automatyczny test UI na emulatorze z usługami Google Play i kluczem Maps:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

Test używa syntetycznych członków i pozycji. Sprawdza kliknięcie osoby, przewinięcie
do mapy, tryb ręczny, wznowienie i powrót do całej rodziny; nie odczytuje ani nie
zmienia danych rodzinnych w Firebase. Obejmuje też wybór osoby i dnia historii,
potwierdzenie kasowania i kamerę trasy oraz migrację bazy Room.

Test usługi ze zdalnym wyłączeniem i wznowieniem przy schowanej Activity używa
osobnych aplikacji Firebase, testowych kont i bazy Room w pamięci. Ten sam skrypt
sprawdza historię: ponowienie wysyłki bez duplikatów, odczyt innej osoby, kasowanie
do końca dnia, ochronę przed odtworzeniem offline i stronicowane sprzątanie. Uruchom na
emulatorze po zbudowaniu APK debug oraz androidTest (JDK 21 dla emulatorów Firebase):

```powershell
npx firebase emulators:exec --project demo-family-locator --only auth,database "powershell.exe -NoProfile -File scripts/Test-TrackingControl.ps1 -SdkPath C:/Dev/JS/family-locator/.android-sdk"
```

Standardowe `connectedDebugAndroidTest` pomija ten opt-in test, jeśli nie podano
argumentu `firebaseEmulators=true`. Test nie zmienia konta ani danych prawdziwej rodziny.
