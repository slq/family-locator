# US-001 — Zapisane miejsca i powiadomienia

**Status:** Propozycja

## User story

Jako członek rodziny chcę zapisać ważne miejsca i otrzymywać powiadomienia
o dotarciu lub wyjeździe wybranych osób, aby wiedzieć, że bliscy dotarli do celu
bez ciągłego sprawdzania mapy.

## Wartość i przykład

Rodzic zapisuje szkołę, wybiera powiadomienia o dotarciu dziecka i otrzymuje
zdarzenie „Ania dotarła do szkoły” wraz z czasem wykrytego przyjazdu.

## Zakres pierwszej wersji

Wspólne miejsca rodziny z nazwą, punktem na mapie i promieniem strefy. Każdy
członek może dodawać, edytować i usuwać miejsca, a własne powiadomienia wybiera
osobno dla osoby, miejsca oraz przyjazdu lub wyjazdu.

## Kryteria akceptacji

- [ ] Zapisane miejsce pojawia się pod tą samą nazwą i z tym samym obszarem
  u pozostałych członków rodziny; nie jest widoczne poza rodziną.
- [ ] Nie można zapisać miejsca bez nazwy, poprawnego punktu na mapie
  i dodatniego promienia.
- [ ] Odbiorca może osobno włączać i wyłączać powiadomienia o przyjeździe
  i wyjeździe wybranej osoby dla danego miejsca.
- [ ] Potwierdzona zmiana obecności osoby w strefie tworzy zdarzenie z nazwą
  osoby, miejsca, rodzajem zmiany i czasem jej wykrycia; powiadomienie może
  dotrzeć także przy zamkniętym ekranie aplikacji.
- [ ] Kolejne pomiary po tej samej stronie granicy nie tworzą nowych zdarzeń,
  a pojedynczy niedokładny pomiar przy granicy nie wystarcza do zmiany stanu.
- [ ] Pierwszy pomiar po utworzeniu lub edycji strefy ustala stan początkowy;
  nie generuje pozornego przyjazdu ani wyjazdu.
- [ ] Zaległe pomiary nie wywołują powiadomienia sugerującego bieżące dotarcie;
  prezentowane zdarzenie zachowuje rzeczywisty czas i informację o opóźnieniu.
- [ ] Brak zgody na powiadomienia jest widoczny w ustawieniach, a wyłączenie
  udostępniania lub brak pomiarów nie jest interpretowane jako opuszczenie strefy.

## Przypadki brzegowe

- Wahania GPS, przejazd obok miejsca i nakładające się strefy wymagają odróżnienia
  od potwierdzonej zmiany obecności; każde miejsce ma własny stan.
- Usunięcie miejsca kończy jego subskrypcje; usunięcie członka rodziny kończy
  dostęp do jego zdarzeń.
- Powiadomienie może być opóźnione. Interfejs nie obiecuje wykrycia przekroczenia
  granicy dokładnie w chwili jej przekroczenia.

## Zależności

Istniejąca lokalizacja i członkostwo w rodzinie. Wykrywanie obecności w miejscu
może być wykorzystane przez [US-008 — Tryb podróży](US-008-tryb-podrozy.md).
