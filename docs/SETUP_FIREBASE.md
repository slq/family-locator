# Konfiguracja rodzinnego prototypu

Ten etap nie wdraża Cloud Functions ani FCM. Pozwala przetestować lokalizację
i wymianę ostatniej pozycji, wspólne nazwy i zdalny tryb dokładny. Sam Auth + Realtime Database mieści się w możliwościach
Spark; Maps SDK wymaga skonfigurowanego billingu w projekcie Google Cloud.

## 1. Firebase i aplikacja Android

1. Utwórz projekt w https://console.firebase.google.com/ (Analytics nie jest potrzebne).
2. Dodaj aplikację Android z pakietem `pl.slqpl.rodzina`.
3. Utwórz **Realtime Database** (nie Firestore), najlepiej w europejskiej lokalizacji
   `europe-west1`. Wybierz **Locked mode**, nigdy publiczny tryb testowy.
4. W Project settings pobierz aktualny `google-services.json` i umieść go w `app/`.
   Pobierz plik po utworzeniu bazy, aby zawierał również jej `firebase_url`.
5. W Authentication → Sign-in method włącz **Email/Password**.
6. W Authentication → Users utwórz osobne konto dla każdego telefonu; zanotuj UID.
   Nie używaj jednego konta na kilku urządzeniach (ścieżka pozycji jest indeksowana UID).

Jeśli wcześniej zarejestrowałeś aplikację z inną nazwą pakietu, dodaj nową aplikację
Android w tym samym projekcie Firebase i pobierz jej `google-services.json`.
Konta użytkowników, baza i przypisania do rodziny pozostają w tym samym projekcie.
Na telefonie nowy pakiet jest osobną aplikacją: trzeba ponownie przyznać uprawnienia
i zalogować się. Zatrzymaj udostępnianie w starej aplikacji przed uruchomieniem nowej.
Zmiana pakietu nie zmienia SHA-1, jeśli używasz tego samego klucza podpisującego;
w ograniczeniach klucza Maps dodaj jednak nową nazwę pakietu.

## 2. Wdrożenie reguł

Reguły znajdują się w `firebase/database.rules.json`. Możesz wkleić ich zawartość
do Realtime Database → Rules i wybrać Publish albo użyć lokalnego CLI:

```sh
npm ci --ignore-scripts
npx firebase login
npx firebase deploy --only database --project TWOJ_PROJECT_ID
```

**Przy aktualizacji do 0.4.0 ponownie wdroż aktualne reguły przed instalacją APK.** Zawierają
indeks `familyId`, odczyt listy własnej rodziny oraz walidację nazw, żądań i potwierdzeń.
Od 0.4.0 zezwalają również na zapis własnej ikonki w `memberIcons/{uid}`.
Dotychczasowe przypisania `memberships` i pozycje pozostają zgodne; migracja nie jest potrzebna.

Zalogowanie CLI odbywa się na Twoim komputerze. Nie przekazuj tokena logowania
ani pliku konta serwisowego w czacie i nie umieszczaj ich w repozytorium.

## 3. Przypisanie kont do rodziny

W Realtime Database → Data dodaj dla każdego UID ścieżkę:

```text
memberships / UID_DANEJ_OSOBY / familyId = "nasza-rodzina"
```

Przykładowy fragment danych (to demonstracyjne UID, nie prawdziwe konta):

```json
{
  "memberships": {
    "UID_TELEFONU_1": { "familyId": "nasza-rodzina" },
    "UID_TELEFONU_2": { "familyId": "nasza-rodzina" },
    "UID_TELEFONU_3": { "familyId": "nasza-rodzina" }
  }
}
```

Dodawaj konkretne węzły. Nie importuj tego przykładu na korzeń istniejącej bazy,
bo import mógłby nadpisać dane. Nie musisz tworzyć `families`: pojawi się po
pierwszej poprawnej wysyłce. Użytkownicy aplikacji nie mogą zmieniać członkostwa.
Usunięcie `memberships/UID` odbiera kontu dostęp do rodziny i usuwa je z listy oraz
mapy w wersji 0.3.0. Zapisane wcześniej dane nadal pozostają na serwerze; usuń je
osobno jako administrator, jeżeli mają zniknąć również z bazy.

## 4. Opcjonalna mapa

1. W tym samym projekcie Google Cloud włącz billing i **Maps SDK for Android**.
2. Utwórz klucz API ograniczony do tego API i aplikacji Android.
3. Dodaj pakiet `pl.slqpl.rodzina` i SHA-1 certyfikatu użytego do podpisania APK.
   Dla lokalnego debug uzyskasz go poleceniem `./gradlew :app:signingReport`
   (na Windows: `gradlew.bat`). Nie publikuj prywatnego pliku keystore.
4. Do `local.properties` dopisz `MAPS_API_KEY=twoj_klucz`.
5. Zbuduj APK ponownie. Nie konfiguruj Map ID; aplikacja używa zwykłych markerów
   i kół dokładności. Nie wywołuje Routes, Places ani Geocoding API.

Klucz map jest częścią APK; ograniczenia klucza muszą być ustawione po stronie Google.
Alert budżetu nie jest twardym limitem wydatków. Włącz tylko używane API.

## 5. Test dwóch telefonów

Zainstaluj ten sam skonfigurowany APK 0.4.0 na obu telefonach, zaloguj osobne konta, ustaw nazwy osób
i uruchom udostępnianie. Bez klucza map zobaczysz listę pozycji, wiek pomiarów
i baterię. Z kluczem mapa pokaże też znaczniki i obszary dokładności.

W panelu **Moja rodzina** każdy członek może zmienić nazwy widoczne dla wszystkich.
Konta przypisane do rodziny pojawią się nawet bez wcześniejszego pomiaru.

Przy swoim koncie wybierz **Wybierz moją ikonkę**, wskaż jeden z 12 symboli i zapisz.
Wybór jest wspólny dla całej rodziny; inne osoby nie mogą zmieniać Twojej ikonki.
**Dobierz automatycznie** usuwa własny wybór. Domyślne ikony są różne, dopóki
wystarcza symboli w katalogu. Kolor i numer znacznika pozwalają rozróżnić również
osoby wybierające ten sam symbol. Podgląd na liście odpowiada znacznikowi na mapie.
Numery zależą od listy UID, więc mogą zmienić się przy dodaniu lub usunięciu członka.
Odczyt ikon wymaga wersji 0.4.0, a ich zapis aktualnych reguł RTDB.

Od 0.5.0 dotknij ikonki lub nagłówka osoby z napisem **Pokaż na mapie**. Ekran
przewinie się do mapy, przybliży jej ostatnią pozycję i będzie śledzić kolejne pomiary.
Przycisk **Pokaż wszystkich** przywraca dopasowanie widoku do wszystkich zapisanych
pozycji rodziny, aktualizowane przy zmianie współrzędnych. Osoba bez pomiaru ma
nieaktywny wybór na mapie. Wiek ostatniej pozycji jest pokazywany także przy zbliżeniu.

Dotknięcie mapy lub **Przeglądaj ręcznie** wstrzymuje automatyczne przesuwanie kamery.
Znaczniki nadal otrzymują aktualne pozycje. Wybierz **Wznów śledzenie**, **Wznów
dopasowanie** albo ponownie osobę / **Pokaż wszystkich**, aby przywrócić automatyczny
widok. Sam wybór osoby na mapie nie wysyła żądania dokładnych pomiarów.
Sterowanie mapą w 0.5.0 korzysta z reguł RTDB z wersji 0.4.0.

Na telefonie odbierającym włącz **Udostępniaj rodzinie**. Na drugim telefonie wybierz
przy tej osobie **Dokładne pomiary przez 15 minut**. Najpierw zobaczysz oczekiwanie,
potem potwierdzenie zmiany trybu przez odbiorcę. Usługa żąda pomiarów co około 10 s;
faktyczna częstotliwość i dokładność zależą od GPS oraz systemu.

Żądanie wygasa 15 min od wysłania, również jeśli odbiorca jest offline. Nie włącza
zatrzymanego udostępniania i nie wybudza po Force Stop. Odbiorca może przerwać
tryb dokładny albo zatrzymać pomiary. Telefony z wersją 0.2.0 nie obsługują tych żądań.

## Materiały źródłowe

- https://firebase.google.com/docs/android/setup
- https://firebase.google.com/docs/database/android/start
- https://firebase.google.com/docs/database/security
- https://developers.google.com/maps/documentation/android-sdk/get-api-key
