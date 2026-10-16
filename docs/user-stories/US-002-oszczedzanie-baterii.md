# US-002 — Automatyczne oszczędzanie baterii

**Status:** Propozycja

## User story

Jako osoba udostępniająca lokalizację chcę, aby aplikacja dostosowywała pomiary
do ruchu i postoju, aby ograniczyć zużycie baterii bez ręcznego zmieniania trybu
w ciągu dnia.

## Wartość i przykład

Podczas pobytu w domu telefon ogranicza pomiary. Po rozpoczęciu spaceru aplikacja
wraca do częstszych aktualizacji, a rodzina nadal widzi czas ostatniej pozycji.

## Zakres pierwszej wersji

Opcjonalna automatyka w zwykłym trybie udostępniania: profil postoju i profil
ruchu, informacja o aktywnym profilu oraz bezpieczny powrót do obecnego trybu
okresowego, gdy rozpoznawanie ruchu jest niedostępne.

## Kryteria akceptacji

- [ ] Właściciel telefonu może włączyć i wyłączyć automatykę; po jej wyłączeniu
  zwykłe udostępnianie działa według dotychczasowego trybu okresowego.
- [ ] Rozpoznany postój przełącza na profil z rzadszymi pomiarami niż w ruchu;
  rozpoznane przemieszczanie przywraca profil ruchu bez działania użytkownika.
- [ ] Brak nowej pozycji sam w sobie nie jest uznawany za dowód postoju.
- [ ] Krótkie wahania rozpoznanego ruchu nie powodują ciągłego przełączania
  profili, a interfejs pokazuje aktualny profil i czas ostatniego pomiaru.
- [ ] Uruchomiony lokalnie lub przez rodzinę tryb dokładny ma pierwszeństwo
  przed automatyką; automatyka nie skraca ani nie przedłuża jego sesji.
- [ ] Po wygaśnięciu trybu dokładnego aplikacja wraca do automatyki, jeżeli jest
  włączona, albo do zwykłego trybu okresowego.
- [ ] Ręczne wyłączenie udostępniania zatrzymuje pomiary; samo wykrycie ruchu
  nie wznawia ich i nie uruchamia zatrzymanej usługi.
- [ ] Przy odmowie wymaganej zgody lub niedostępności rozpoznawania ruchu
  aplikacja informuje o ograniczeniu i korzysta z trybu okresowego.

## Przypadki brzegowe

- Jazda samochodem, spacer, telefon odłożony na biurku i krótkie zatrzymanie
  w korku nie muszą dawać identycznych odstępów pomiarów.
- Utrata internetu nie wyłącza lokalnego przełączania profili.
- Rzeczywista częstotliwość i korzyść dla baterii podlegają weryfikacji na
  telefonach rodziny; wybrany profil nie gwarantuje odstępu między pomiarami.

## Zależności

Istniejące tryby okresowy, dokładny i wyłączony oraz lokalny limit sesji dokładnej.
[US-008 — Tryb podróży](US-008-tryb-podrozy.md) korzysta z tej samej zasady
pierwszeństwa czasowego trybu dokładnego.
