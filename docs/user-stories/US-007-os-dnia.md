# US-007 — Czytelna oś dnia

**Status:** Propozycja

## User story

Jako członek rodziny przeglądający dostępną historię chcę widzieć postoje,
orientacyjne godziny przyjazdu i wyjazdu oraz przewijać dzień suwakiem,
aby łatwo zrozumieć przebieg podróży bliskiej osoby.

## Wartość i przykład

Rodzic wybiera wczorajszy dzień dziecka i widzi odcinki przemieszczania oraz
postój przy szkole. Przesunięcie suwaka wskazuje zapisany pomiar z wybranej pory.

## Zakres pierwszej wersji

Rozbudowa istniejącej historii tras o listę odcinków i postojów, wybór zdarzenia
oraz suwak zsynchronizowany z mapą. Nadal obowiązują obecny wybór osoby i dnia,
30-dniowa dostępność danych oraz usuwanie własnej historii.

## Kryteria akceptacji

- [ ] Wybranie osoby i dnia pokazuje oś odpowiadającą tym samym pomiarom,
  które są dostępne na mapie historii; zmiana wyboru aktualizuje oba widoki.
- [ ] Wykryty postój prezentuje orientacyjny początek, koniec i czas trwania;
  jest oznaczony jako wniosek z pomiarów.
- [ ] Godziny przyjazdu i wyjazdu wynikają z dostępnych punktów, są opisane jako
  przybliżone i nie sugerują dokładniejszego czasu niż pozwalają dane.
- [ ] Przesunięcie suwaka wskazuje najbliższy zapisany pomiar z jego czasem,
  pozycją i dokładnością; nie tworzy fikcyjnej pozycji pomiędzy pomiarami.
- [ ] Wybranie postoju lub odcinka na osi wyróżnia odpowiadający fragment
  mapy i wskazuje jego zakres czasu.
- [ ] Przerwa między pomiarami dłuższa niż obecne 15 minut pozostaje widoczną
  luką; nie jest łączona trasą ani automatycznie uznawana za postój.
- [ ] Dzień bez pomiarów ma stan pusty, a pojedynczy punkt można obejrzeć
  bez dopisywania do niego przejazdu, przyjazdu lub czasu postoju.
- [ ] Oś respektuje strefę czasową telefonu, limit dostępności i usunięcie
  historii; po usunięciu nie pokazuje pozostałych w pamięci zdarzeń z tych danych.

## Przypadki brzegowe

- Zmiana czasu, postój przez północ i niepełny początek lub koniec dnia
  nie mogą tworzyć ujemnych czasów ani sugerować pełnego przebiegu podróży.
- Niedokładne punkty i skoki GPS nie wystarczają do przypisania potwierdzonej
  wizyty w konkretnym miejscu.
- Nieudane pobranie historii jest błędem ładowania, a nie pustym dniem.

## Zależności

Istniejąca historia tras, jej retencja, segmentacja i zasady dostępu.
[US-001 — Zapisane miejsca](US-001-zapisane-miejsca.md) może dostarczyć nazwy
miejsc; bez tej funkcji oś nadal działa z punktami na mapie.
