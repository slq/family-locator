# US-004 — Odbierz mnie

**Status:** Propozycja

## User story

Jako członek rodziny potrzebujący transportu chcę wysłać prośbę o odbiór
z wybranego miejsca i zobaczyć, kto ją przyjął, aby łatwo ustalić odbiór
bez wymiany wielu wiadomości.

## Wartość i przykład

Po treningu dziecko wskazuje wejście do hali i wybiera „Odbierz mnie”.
Rodzic potwierdza „Jadę”, a obie osoby widzą uzgodniony punkt odbioru.

## Zakres pierwszej wersji

Jedna aktywna prośba na osobę, punkt odbioru wskazany na mapie, opcjonalna notatka
oraz stany: oczekuje, przyjęta, anulowana i zakończona. Prośby są widoczne
wyłącznie dla aktualnych członków rodziny.

## Kryteria akceptacji

- [ ] Przed wysłaniem autor zatwierdza punkt odbioru; może użyć własnej pozycji
  lub ręcznie wskazać miejsce, również przy niedostępnym GPS.
- [ ] Potwierdzona prośba pokazuje autora, punkt, notatkę i czas utworzenia;
  inni członkowie rodziny mogą ją otworzyć z aplikacji lub powiadomienia.
- [ ] Odbiorca inny niż autor może wybrać „Jadę”; przyjęta prośba pokazuje
  wszystkim zainteresowanym nazwę osoby odbierającej.
- [ ] Przy równoczesnym przyjęciu przez dwie osoby zostaje przypisana tylko
  jedna; druga otrzymuje informację, że prośba jest już przyjęta.
- [ ] Autor może anulować aktywną prośbę, a przyjmujący może zrezygnować
  z odbioru; rezygnacja przywraca oczekiwanie i informuje autora.
- [ ] Autor lub przypisana osoba odbierająca może oznaczyć odbiór jako
  zakończony; zakończonej lub anulowanej prośby nie można ponownie przyjąć.
- [ ] Bez połączenia operacja nie jest pokazywana jako wysłana ani przyjęta;
  ponowienie nie tworzy drugiej prośby, a nowa prośba wymaga zamknięcia poprzedniej.
- [ ] Punkt aktywnej prośby pozostaje miejscem uzgodnionym przy wysłaniu;
  przemieszczanie autora i nowe pomiary GPS nie przesuwają go automatycznie.

## Przypadki brzegowe

- Anulowanie podczas przyjmowania kończy się jednym wspólnym stanem; odbiorca
  nie może zobaczyć niepotwierdzonego przypisania jako sukcesu.
- Zmiana miejsca wymaga anulowania i utworzenia nowej prośby.
- Utrata członkostwa przez autora zamyka prośbę; utrata członkostwa przez
  odbierającego usuwa przypisanie i informuje pozostałych zainteresowanych.

## Zależności

Istniejąca mapa i członkostwo w rodzinie.
[US-005 — Nawigacja do osoby](US-005-nawigacja-do-osoby.md) może udostępnić
analogiczny skrót nawigacji do zatwierdzonego punktu odbioru; nie jest wymagany
do samego uzgodnienia odbioru.
