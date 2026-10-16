# US-008 — Tryb podróży

**Status:** Propozycja

## User story

Jako członek rodziny rozpoczynający podróż chcę wskazać cel i czasowo udostępniać
dokładniejszą lokalizację, aby bliscy mogli obserwować drogę i zobaczyć,
czy dotarcie zostało potwierdzone.

## Wartość i przykład

Przed wyjściem użytkownik wybiera dom jako cel i uruchamia „Wracam do domu”.
Rodzina widzi aktywną podróż, cel, czas pozostały oraz ostatnią pozycję.

## Zakres pierwszej wersji

Jedna podróż na osobę, cel wskazany na mapie lub wybrany z zapisanych miejsc,
czasowa sesja dokładna oraz zakończenie ręczne, po potwierdzonym dotarciu
lub po upływie limitu. Pierwsza wersja zachowuje obecny limit 15 minut trybu
dokładnego i nie odnawia go automatycznie.

## Kryteria akceptacji

- [ ] Właściciel telefonu może zatwierdzić cel i uruchomić podróż; brak celu
  lub wymaganej zgody lokalizacyjnej blokuje start z czytelnym powodem.
- [ ] Po przyjęciu sesji rodzina widzi cel, stan podróży, pozostały czas i wiek
  ostatniej pozycji; sam start nie oznacza otrzymania świeżego pomiaru.
- [ ] Podróż uruchamia czasowy tryb dokładny z widocznym terminem zakończenia,
  nie dłuższy niż obecne 15 minut; dla jednej osoby aktywna jest jedna podróż.
- [ ] Potwierdzone dotarcie do obszaru celu może zakończyć podróż automatycznie;
  pojedynczy niedokładny punkt ani brak nowych danych nie potwierdzają dotarcia.
- [ ] Właściciel może ręcznie zakończyć podróż i wskazać, czy dotarł;
  zakończenie bez potwierdzenia nie jest opisywane jako przyjazd.
- [ ] Upływ limitu kończy dokładne pomiary również bez internetu i pozostawia
  stan „Sesja zakończona — dotarcie niepotwierdzone”, jeżeli nie było potwierdzenia.
- [ ] Po zwykłym zakończeniu sesji pomiary wracają do trybu okresowego lub
  włączonej automatyki; nowe jawne polecenie zmiany trybu kończy sesję
  podróży, a podróż nie odtwarza poprzedniego trybu wbrew temu poleceniu.
- [ ] Brak potwierdzenia wysłania pokazuje stan oczekiwania, a zatrzymanie
  udostępniania nie jest oznaczane jako dotarcie; ponowne połączenie nie
  rozpoczyna ani nie przedłuża zakończonej sesji.

## Przypadki brzegowe

- Dłuższa droga wymaga świadomego uruchomienia nowej sesji; wygaśnięcie
  intensywnych pomiarów nie świadczy o zakończeniu rzeczywistej podróży.
- Restart lub zakończenie procesu nie daje podstaw do wyświetlenia dotarcia;
  ekran uwzględnia wiek ostatniego potwierdzonego stanu.
- Zmiana celu wymaga zakończenia starej i uruchomienia nowej podróży.

## Zależności

Istniejący czasowy tryb dokładny i sterowanie rodzinne.
Wykrywanie dotarcia współdzieli zasady z
[US-001 — Zapisane miejsca](US-001-zapisane-miejsca.md).
Po zakończeniu uwzględnia
[US-002 — Oszczędzanie baterii](US-002-oszczedzanie-baterii.md), jeśli wdrożone.
