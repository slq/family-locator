# US-003 — Alerty o baterii i braku aktualizacji

**Status:** Propozycja

## User story

Jako członek rodziny chcę otrzymywać powiadomienia o niskiej baterii i długim
braku nowych pozycji wybranych osób, aby wiedzieć, kiedy dane mogą przestać
być aktualne i kiedy warto skontaktować się z bliskim.

## Wartość i przykład

Rodzic ustawia alert przy baterii poniżej 15% i po 30 minutach bez nowej pozycji.
Powiadomienie pokazuje ostatni zgłoszony stan i jego czas, bez przypisywania
przyczyny braku aktualizacji.

## Zakres pierwszej wersji

Osobne ustawienia odbiorcy dla każdej osoby i rodzaju alertu: włączenie,
próg procentowy lub czasowy oraz czasowe wyciszenie. Stan alertu jest dostępny
także w aplikacji.

## Kryteria akceptacji

- [ ] Odbiorca może niezależnie skonfigurować oba rodzaje alertów dla wybranych
  członków swojej rodziny, bez zmieniania preferencji innych odbiorców.
- [ ] Ustawienia odrzucają próg baterii poza zakresem 1–99% i niedodatni czas
  oczekiwania na pozycję.
- [ ] Nowy pomiar z baterią poniżej ustawionego progu rozpoczyna pojedynczy alert;
  nieznany poziom baterii nie jest traktowany jako 0%.
- [ ] Alert braku aktualizacji powstaje po przekroczeniu ustawionego wieku
  ostatniego pomiaru, gdy udostępnianie jest włączone; sam heartbeat nie
  zastępuje nowego pomiaru lokalizacji.
- [ ] Powiadomienie zawiera osobę, rodzaj alertu i czas źródłowego pomiaru;
  brak aktualizacji nie jest opisywany jako pewne wyłączenie telefonu lub utrata zasięgu.
- [ ] Kolejne odczyty tego samego stanu nie ponawiają alertu; nowy alert jest
  możliwy po powrocie do prawidłowego stanu i ponownym przekroczeniu progu.
- [ ] Wyłączenie alertu lub aktywne wyciszenie blokuje nowe powiadomienia danego
  typu; po zakończeniu wyciszenia nie jest odtwarzana seria zaległych alertów.
- [ ] Powiadomienia mogą dotrzeć bez otwartego ekranu aplikacji, a brak zgody na
  ich wyświetlanie jest widoczny; opóźniony raport nie udaje aktualnego stanu baterii.

## Przypadki brzegowe

- Osoba bez pierwszej pozycji ma stan „Brak danych”; alert czasowy zaczyna działać
  po otrzymaniu pierwszego pomiaru.
- Jawnie wyłączone udostępnianie nie uruchamia alertu braku pozycji.
- Po odzyskaniu połączenia starsze punkty nie zerują wieku nowszego pomiaru;
  usunięty członek znika z ustawień i nie generuje kolejnych alertów.

## Zależności

Istniejące czasy pomiarów, raportowany poziom baterii i status udostępniania.
Należy uwzględnić rzadsze pomiary z
[US-002 — Oszczędzanie baterii](US-002-oszczedzanie-baterii.md).
