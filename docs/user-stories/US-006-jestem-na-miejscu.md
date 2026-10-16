# US-006 — Jestem na miejscu

**Status:** Propozycja

## User story

Jako członek rodziny chcę ręcznie potwierdzić dotarcie do celu jednym
przyciskiem, aby bliscy otrzymali ode mnie jasną informację bez konieczności
pisania wiadomości.

## Wartość i przykład

Dziecko wchodzi do budynku, gdzie GPS jest niedokładny, i wybiera „Jestem
na miejscu”. Rodzina widzi, kto i kiedy wysłał potwierdzenie.

## Zakres pierwszej wersji

Ręczne zgłoszenie dotarcia z czasem naciśnięcia przycisku i opcjonalną nazwą
miejsca. Lokalizacja jest dodatkiem do potwierdzenia i nie jest wymagana
do jego wysłania. Rodzina otrzymuje powiadomienie i widzi ostatnie zgłoszenie.

## Kryteria akceptacji

- [ ] Zalogowana osoba może wybrać „Jestem na miejscu” bez uruchamiania
  dokładnych pomiarów i bez obowiązku wpisania wiadomości.
- [ ] Przycisk działa bez aktualnego GPS i przy wyłączonym udostępnianiu;
  potwierdzenie nie uruchamia pomiarów.
- [ ] Autor może opcjonalnie podać nazwę miejsca; jeżeli dołączona jest pozycja,
  zgłoszenie pokazuje również czas i dokładność tego pomiaru.
- [ ] Po potwierdzonym wysłaniu członkowie rodziny widzą autora, czas zgłoszenia
  i ewentualną nazwę miejsca; mogą otrzymać powiadomienie przy zamkniętym ekranie aplikacji.
- [ ] Zgłoszenie jest opisane jako ręczne potwierdzenie autora, a jego czas
  nie jest prezentowany jako czas niezależnie potwierdzonego pomiaru GPS.
- [ ] Bez internetu zgłoszenie ma stan „Oczekuje na wysłanie”; autor może je
  anulować, zanim zostanie przekazane rodzinie.
- [ ] Po odzyskaniu połączenia wysłane zgłoszenie zachowuje pierwotny czas
  i jest oznaczone jako dostarczone z opóźnieniem.
- [ ] Wielokrotne dotknięcie podczas wysyłania i ponowienie tej samej operacji
  nie tworzą kilku zgłoszeń; błąd wysłania pozostaje widoczny.

## Przypadki brzegowe

- Pomyłkowe dotknięcie przycisku nie stanowi automatycznego potwierdzenia
  obecności w konkretnej zapisanej strefie.
- Jeżeli w czasie oczekiwania zmieni się konto lub członkostwo autora,
  zgłoszenie nie jest wysyłane do poprzedniej ani do nowej rodziny.
- Brak zgody na powiadomienia nie usuwa potwierdzenia z widoku aplikacji.

## Zależności

Istniejące logowanie i członkostwo. Wybór nazwy z
[US-001 — Zapisane miejsca](US-001-zapisane-miejsca.md) jest opcjonalnym
ułatwieniem; brak zapisanych miejsc nie blokuje potwierdzenia.
