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
| Cofnięcie członkostwa w konsoli | Kolejne odczyty/zapisy odrzucane; aplikacja sygnalizuje błąd |
| Force Stop / restart telefonu | Bez cichego wznowienia; ponowny start z ekranu |
| GPS wyłączony / budynek | Brak świeżego punktu lub gorsza dokładność; widoczny wiek pomiaru |
| Oszczędzanie baterii i nocne Doze | Zapisz faktyczne opóźnienia; nie zakładaj ciągłości |

Najpierw testuj S10 jako nadajnik i A54 jako odbiornik, potem zamień role.
Na Samsungu sprawdź ustawienia baterii aplikacji i listę Nigdy nieusypiane aplikacje.
Porównaj zużycie przez podobny dzień bez aplikacji oraz z trybem okresowym;
test 30-minutowy nie wystarcza do wiarygodnego oszacowania całodziennego zużycia.
