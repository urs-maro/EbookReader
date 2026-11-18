# Semnarea aplicației (release APK)

APK-urile *debug* declanșează avertismentul „this app may be harmful" la instalare.
Un APK *release* semnat cu cheia ta proprie arată mult mai curat. Semnarea se face local,
pe calculatorul tău (cheia privată nu trebuie să ajungă niciodată în cloud/git).

Proiectul e deja pregătit: `app/build.gradle.kts` citește datele de semnare din
`keystore.properties` (ignorat de git), iar build-ul `release` le aplică automat.

## 1. Generează keystore-ul (o singură dată)

Ai nevoie de `keytool` (vine cu JDK-ul / cu Android Studio, în `...\jbr\bin`).
În rădăcina proiectului rulează:

```powershell
keytool -genkeypair -v -keystore crio-release.jks -alias crio -keyalg RSA -keysize 2048 -validity 10000
```

Te întreabă o parolă pentru keystore, apoi câteva date (nume, organizație — pot fi orice),
apoi o parolă pentru cheie (poți apăsa Enter ca să fie aceeași). Rezultă fișierul
`crio-release.jks` în rădăcina proiectului.

> ⚠️ Păstrează `crio-release.jks` și parolele la loc sigur, pentru totdeauna. Toate
> actualizările viitoare trebuie semnate cu aceeași cheie; dacă o pierzi, utilizatorii vor
> trebui să dezinstaleze aplicația ca să instaleze o versiune nouă.

## 2. Creează `keystore.properties`

Copiază `keystore.properties.example` ca `keystore.properties` și completează parolele:

```
storeFile=crio-release.jks
storePassword=parola_ta_keystore
keyAlias=crio
keyPassword=parola_ta_cheie
```

## 3. Construiește APK-ul semnat

```powershell
.\gradlew.bat assembleRelease
```

Rezultă: `app\build\outputs\apk\release\CRIO-Books-Reader-release.apk` — semnat, gata de
atașat la un release GitHub.

## Note

- Dacă pe tabletă ai deja instalată o versiune *debug*, dezinstaleaz-o înainte de a instala
  cea *release* (semnături diferite → Android refuză actualizarea peste ea).
- `keystore.properties`, `crio-release.jks` și `*.jks` sunt deja în `.gitignore`.
