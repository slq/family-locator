# US-010 — Dołączanie kodem lub QR

**Status:** Propozycja

## User story

Jako członek rodziny chcę wygenerować jednorazowe zaproszenie w formie kodu
lub QR, aby dodać nową osobę bez ręcznej zmiany członkostwa w konsoli Firebase.

## Wartość i przykład

Rodzic pokazuje kod QR na swoim telefonie. Nowa osoba loguje się na własne konto,
skanuje kod, sprawdza nazwę rodziny i potwierdza dołączenie.

## Zakres pierwszej wersji

Tworzenie zaproszeń przez obecnych członków rodziny, jednorazowy kod i jego
odpowiednik QR, widoczny termin ważności, odwołanie własnego zaproszenia oraz
dołączenie zalogowanego konta bez dotychczasowego członkostwa. Bez przebudowy
logowania i bez obsługi wielu rodzin na jednym koncie.

## Kryteria akceptacji

- [ ] Aktualny członek rodziny może utworzyć zaproszenie, zobaczyć kod, QR
  i termin ważności; obie formy wskazują to samo jednorazowe zaproszenie.
- [ ] Zaproszona osoba może zeskanować QR lub ręcznie wpisać kod; odmowa
  dostępu do aparatu nie blokuje ręcznego wpisania.
- [ ] Przed dołączeniem użytkownik musi być zalogowany i potwierdza nazwę
  rodziny; samo zeskanowanie kodu nie zmienia członkostwa.
- [ ] Potwierdzenie ważnego zaproszenia przypisuje konto do właściwej rodziny,
  zużywa zaproszenie i udostępnia nowej osobie jej listę członków.
- [ ] Kod błędny, wygasły, wykorzystany lub odwołany nie nadaje dostępu;
  aplikacja pokazuje niepowodzenie i pozwala wprowadzić inny kod.
- [ ] Przy równoczesnym użyciu kodu przez dwa konta dołącza tylko jedno;
  ponowienie potwierdzenia przez już dołączone konto nie tworzy drugiego członkostwa.
- [ ] Autor może odwołać niewykorzystane zaproszenie; utrata jego członkostwa
  również unieważnia niewykorzystane zaproszenia, które utworzył.
- [ ] Brak internetu nie jest pokazywany jako udane dołączenie, a konto
  należące do innej rodziny nie jest przenoszone; kod nie daje dostępu
  do lokalizacji ani listy członków przed potwierdzonym dołączeniem.

## Przypadki brzegowe

- Użycie zaproszenia do rodziny, do której konto już należy, pokazuje ten stan
  i nie zużywa zaproszenia.
- Odwołanie lub wygaśnięcie podczas potwierdzania może zakończyć się odmową;
  interfejs uznaje wyłącznie potwierdzony wynik.
- Po przerwanym połączeniu aplikacja sprawdza faktyczny stan członkostwa,
  zanim zaproponuje ponowienie.
- Wymiana telefonu przy zachowaniu tego samego konta pozostaje osobnym procesem;
  zaproszenie nie tworzy obsługi wielu urządzeń na jednym koncie.

## Zależności

Istniejące logowanie i model członkostwa. Funkcja rozszerza obecne ręczne
przypisywanie kont do rodziny; nie wymaga pozostałych user stories.
