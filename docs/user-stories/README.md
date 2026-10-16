# User stories — Family Locator

Dziesięć propozycji rozwoju aplikacji Rodzina. Każdy dokument opisuje potrzebę
użytkownika, zakres pierwszej wersji oraz kryteria akceptacji.

**Status wszystkich historii: Propozycja.** Dokumenty nie oznaczają, że opisane
funkcje są już dostępne.

## Indeks

| ID | Funkcja | Dokument |
|---|---|---|
| US-001 | Zapisane miejsca i powiadomienia | [Zapisane miejsca](US-001-zapisane-miejsca.md) |
| US-002 | Automatyczne oszczędzanie baterii | [Oszczędzanie baterii](US-002-oszczedzanie-baterii.md) |
| US-003 | Alerty o baterii i braku aktualizacji | [Alerty telefonu](US-003-alerty-telefonu.md) |
| US-004 | Prośba o odbiór | [Odbierz mnie](US-004-odbierz-mnie.md) |
| US-005 | Nawigacja do członka rodziny | [Nawigacja do osoby](US-005-nawigacja-do-osoby.md) |
| US-006 | Ręczne potwierdzenie dotarcia | [Jestem na miejscu](US-006-jestem-na-miejscu.md) |
| US-007 | Postoje i przewijanie historii | [Oś dnia](US-007-os-dnia.md) |
| US-008 | Czasowe udostępnianie podczas podróży | [Tryb podróży](US-008-tryb-podrozy.md) |
| US-009 | Podgląd rodziny na ekranie głównym | [Widżet](US-009-widzet.md) |
| US-010 | Dołączanie do rodziny kodem lub QR | [Zaproszenia QR](US-010-zaproszenia-qr.md) |

Numery identyfikują historie i zachowują kolejność propozycji; nie wyznaczają
kolejności wdrożenia.

## Punkt wyjścia

Aplikacja ma już mapę rodziny, listę członków, informację o baterii i czasie
pomiaru, tryb okresowy, czasowy tryb dokładny oraz sterowanie rodzinne. W kodzie
istnieje również historia tras z wyborem osoby i dnia, ograniczeniem do 30 dni
oraz usuwaniem własnej historii. US-007 opisuje jej rozbudowę.

Opisy uwzględniają obecny model jednej osoby i jednego telefonu na konto.
Dostęp do danych pozostaje ograniczony do własnej rodziny. Nowe funkcje
korzystające z lokalizacji respektują wyłączenie pomiarów i istniejące zasady
sterowania rodzinnego.

## Wspólne zasady akceptacji

- Czas ostatniego pomiaru lub zgłoszenia jest odróżniony od czasu jego odbioru.
- Brak nowych danych nie oznacza automatycznie wyłączenia telefonu, utraty zasięgu
  ani sytuacji niebezpiecznej.
- Funkcje nie obiecują stałej dokładności GPS ani natychmiastowego dostarczenia
  powiadomienia. Opóźnione dane są odpowiednio oznaczane.
- Brak internetu i brak wymaganych zgód mają czytelny stan w interfejsie;
  niepotwierdzona operacja nie jest prezentowana jako zakończona.
- Usunięcie członkostwa lub zmiana konta odbiera dostęp do danych poprzedniej
  rodziny, również w dodatkowych widokach i widżecie.

## Granice dokumentacji

Historie opisują zachowanie produktu. Projekt API, schematów danych, algorytmów
oraz szczegółowy plan implementacji wymagają osobnego opracowania. Powiązania
między historiami oznaczono w ich sekcjach „Zależności”; brak powiązania nie
wyklucza wspólnej infrastruktury.
