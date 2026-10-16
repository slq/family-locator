# US-005 — Nawigacja do członka rodziny

**Status:** Propozycja

## User story

Jako członek rodziny chcę jednym przyciskiem otworzyć nawigację do ostatniej
znanej pozycji bliskiej osoby, aby łatwo do niej dotrzeć bez przepisywania adresu.

## Wartość i przykład

Rodzic otwiera szczegóły dziecka, sprawdza, że pozycja pochodzi sprzed dwóch
minut, i wybiera „Nawiguj”, aby otworzyć cel w zainstalowanej aplikacji mapowej.

## Zakres pierwszej wersji

Skrót przy wybranej osobie, przekazujący współrzędne ostatniego dostępnego pomiaru
do zewnętrznej aplikacji nawigacyjnej. Bez wyznaczania trasy i przewidywanego
czasu dojazdu wewnątrz Rodziny.

## Kryteria akceptacji

- [ ] Dla osoby z dostępną pozycją jej szczegóły udostępniają akcję „Nawiguj”.
- [ ] Przed uruchomieniem nawigacji widoczne są nazwa osoby, czas ostatniego
  pomiaru i jego dokładność; użytkownik wie, do którego punktu wyznacza trasę.
- [ ] Dla nieaktualnej pozycji aplikacja dodatkowo wymaga potwierdzenia
  nawigacji do ostatniego znanego punktu i pokazuje jego wiek.
- [ ] Po zatwierdzeniu zewnętrzna aplikacja otrzymuje współrzędne osoby
  wybranej w chwili naciśnięcia przycisku.
- [ ] Jeżeli dostępnych jest kilka aplikacji obsługujących nawigację i brak
  domyślnej, użytkownik może wybrać aplikację.
- [ ] Brak pozycji lub niepoprawne współrzędne blokują uruchomienie nawigacji
  i wyświetlają czytelny powód.
- [ ] Jeżeli nie ma aplikacji obsługującej nawigację, Rodzina pokazuje komunikat
  i pozostaje na ekranie osoby bez błędu.
- [ ] Uruchomienie nawigacji nie zmienia trybu pomiarów wybranej osoby
  i nie wymaga włączenia udostępniania własnej lokalizacji w Rodzinie.

## Przypadki brzegowe

- Późniejsze przemieszczenie osoby nie przesuwa automatycznie celu w otwartej
  aplikacji nawigacyjnej; ponowny skrót korzysta z aktualnie dostępnej pozycji.
- Brak internetu nie usuwa zapisanej pozycji, ale możliwość wyznaczenia trasy
  zależy od aplikacji nawigacyjnej.
- Jeżeli osoba przestaje należeć do rodziny przed uruchomieniem akcji, skrót
  nie udostępnia jej danych.
- Określenie nieaktualności korzysta z tej samej oceny świeżości co mapa rodziny.

## Zależności

Istniejące pozycje rodziny i szczegóły osoby. Analogiczna akcja dla
[US-004 — Odbierz mnie](US-004-odbierz-mnie.md) musi używać zatwierdzonego punktu
odbioru, który może różnić się od bieżącej pozycji autora.
