# QR Direkt

Winzige Android-App zum Scannen von QR-Codes: Sie liest einen Code, öffnet den `https://`-Link im Standardbrowser und schließt sich sofort wieder. Gedacht für BlueNexus-QR-Codes (NFC + QR-Etiketten), funktioniert aber mit jedem https-Link.

## Verhalten
- **Vertraute Domain:** Der Link öffnet sofort ohne Rückfrage.
- **Fremde https-Domain:** Es erscheint „Diese Seite öffnen?“ mit Domain und Link. Mit **Immer öffnen** wird die Domain als vertraut gemerkt.
- **Taschenlampe:** Das Taschenlampen-Symbol oben links schaltet bei schlechtem Licht das Licht ein (nur bei Geräten mit Blitz).
- **Kein https-Link** (Text, WLAN, `http://` …): Der Inhalt wird als Text angezeigt, mit **Kopieren**.

## Einstellungen
Auf das **Zahnrad** oben rechts in der Scan-Ansicht tippen. Dort lassen sich vertraute Domains eintragen, per QR-Code hinzufügen oder entfernen. Eine Domain gilt samt ihrer Subdomains. Geprüft wird der Host exakt, nicht „enthält“. Beim ersten Start ist die Liste leer: Domain eintragen oder beim ersten Scan im Rückfrage-Fenster „Immer öffnen“ wählen.

## Sprache
Deutsch und Englisch, umschaltbar über die Flaggen oben rechts in den Einstellungen. Ohne Auswahl folgt die App der Handy-Sprache (Deutsch, sonst Englisch). Unten in den Einstellungen steht ein Link zum GitHub-Profil.

## Wichtig bei BlueNexus
Der Link öffnet im **Standardbrowser**. BlueNexus löst nur aus, wenn das Handy genau in diesem Browser registriert wurde („Handy registrieren“). Ist der Standardbrowser ein anderer (z. B. Samsung Internet statt Chrome), passiert beim Scan nichts.

## Voraussetzungen
Android 7+. Die App liest QR-Codes mit eigener Kamera-Ansicht (CameraX + ML Kit, vollständig offline, ohne Google Play Services) und fragt beim ersten Start einmal nach der Kamera-Berechtigung.

## Installation
APK aus dem Release herunterladen, auf dem Handy öffnen, „Installation aus unbekannten Quellen“ für den Browser/Dateimanager erlauben.

## Selbst bauen
`JAVA_HOME` auf JDK 17 setzen und `./gradlew assembleRelease`. Ohne eigenen Release-Schlüssel (`../_Schluessel/qr-direkt-keystore.properties`) wird mit dem Debug-Schlüssel signiert.


## Lizenz
Frei für alle: Public Domain (Unlicense). Jeder darf die App und den Code beliebig nutzen, ändern und weitergeben.
