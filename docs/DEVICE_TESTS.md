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
| Stop w powiadomieniu | Usługa i pomiary kończą się, brak dalszych wysyłek |
| Utrata internetu na 5 min, potem powrót | Room przechowuje punkty; ostatni trafia do chmury po połączeniu |
| Stop bez internetu, potem powrót sieci | SDK nie wysyła oczekujących zapisów po zatrzymaniu |
| Logowanie po teście lokalnym | Stare punkty lokalne nie są wysyłane |
| Wylogowanie | Wymaga Stop; lokalne punkty są usuwane |
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
| Żądanie do zatrzymanej usługi / starej wersji 0.2 | Brak fałszywego potwierdzenia; usługa nie uruchamia się sama |
| Odbiorca wybiera tryb okresowy lub Stop | Pomiar dokładny przerwany; stare UUID nie uruchamia go ponownie po reconnect/starcie |
| Kilku nadawców i powtórne kliknięcie | Niezależne sesje, nowy UUID odnawia 15 min; odbiorca może zakończyć wszystkie |
| Zanik sieci w trakcie sesji dokładnej | Lokalny zegar kończy sesję; przeterminowany heartbeat nie jest pokazywany jako aktywny |
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
zmienia danych rodzinnych w Firebase.
