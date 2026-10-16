# US-009 — Widżet rodziny

**Status:** Propozycja

## User story

Jako członek rodziny chcę widzieć ostatnią lokalizację, baterię i świeżość danych
wybranych osób na ekranie głównym telefonu, aby szybko sprawdzić sytuację
bez otwierania aplikacji.

## Wartość i przykład

Użytkownik umieszcza widżet z dwiema osobami. W drodze z pracy sprawdza czas
ostatniej pozycji dziecka i jednym dotknięciem przechodzi do jego mapy.

## Zakres pierwszej wersji

Widżet Android z wyborem członków rodziny, nazwą i ikoną osoby, skrótem
lokalizacji, czasem pomiaru, ostatnio zgłoszoną baterią oraz przejściem do aplikacji.
Skrót lokalizacji może być nazwą zapisanego miejsca lub współrzędnymi; adres
pocztowy i dodatkowa mapa w widżecie nie są wymagane.

## Kryteria akceptacji

- [ ] Podczas dodawania lub konfiguracji widżetu użytkownik może wybrać osoby
  ze swojej aktualnej rodziny i później zmienić ten wybór.
- [ ] Każdy wpis pokazuje nazwę, ikonę, skrót ostatniej lokalizacji, czas pomiaru
  i poziom baterii z tego raportu.
- [ ] Nieznana bateria i brak pozycji są opisane jako brak danych; nie są
  zastępowane zerem ani fikcyjną lokalizacją.
- [ ] Wiek pomiaru i stan nieaktualności są widoczne; nieudane odświeżenie
  nie ustawia nowego czasu przy starym punkcie.
- [ ] Dotknięcie osoby otwiera Rodzinę z wybraną osobą na mapie, a utrata
  dostępu do tej osoby jest obsłużona czytelnym komunikatem.
- [ ] Widżet udostępnia ręczne odświeżenie ze stanem powodzenia lub błędu;
  brak internetu zachowuje ostatnie dane z ich właściwym czasem.
- [ ] Wylogowanie lub zmiana konta usuwa dane poprzedniej sesji z widżetu,
  a potwierdzona utrata członkostwa usuwa osoby, do których utracono dostęp.
- [ ] Obecność widżetu i jego odświeżanie nie uruchamiają GPS, trybu dokładnego
  ani udostępniania lokalizacji; widżet korzysta z dostępnych raportów.

## Przypadki brzegowe

- Zmieniona nazwa lub ikona pojawia się przy kolejnym skutecznym odświeżeniu.
- Gdy usunięto wszystkie wybrane osoby, widżet proponuje ponowną konfigurację.
- System może opóźniać aktualizacje w tle. Widżet nie obiecuje stałego odstępu
  odświeżania i pokazuje czas danych także wtedy, gdy odświeżanie nie działa.
- Dane pozostawione offline są wyraźnie ostatnio zapisanymi raportami, a nie
  potwierdzeniem bieżącej dostępności osoby.

## Zależności

Istniejąca lista rodziny, pozycje, baterie i wybór osoby na mapie.
Nazwy z [US-001 — Zapisane miejsca](US-001-zapisane-miejsca.md) są opcjonalnym
ulepszeniem skrótu lokalizacji.
