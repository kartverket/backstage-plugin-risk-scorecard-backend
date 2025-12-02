# Prosess for PR

Alle PR'er skal først sees på og godkjennes av en i RoS-teamet. Deretter, bruk "Request Review" og assign en fra team SKVIS for å signalisere at PR'en er ferdig med intern review. Beskriv så under hva du mener SKVIS'er skal gjøre.

## Hei SKIVS'er :wave:

<!-- Fjern alternativene som ikke er relevant -->

Kan du...:

:eyes: se kjapt over?

:monocle_face: ta en nøye gjennomgang?

:test_tube: teste lokalt? (NB: hvordan teste skal være beskrevet under)

:white_check_mark: godkjenne og merge?

<!-- Beskriv evt hvordan teste. Kan det testet direkte via Swagger? Eller må man kjøre front-enden og teste via der? -->

## 📝 Beskrivelse

<!-- Beskriv kort hva som er endret og gjerne legg ved link til Notion oppgave. -->
<!-- PS: Legg gjerne lenken til PR'en i Notion kortet også :) -->

---

## 📸 Skjermbilder (valgfritt)

<!-- Legg til skjermbilder eller GIF-er som viser endringene visuelt. -->

---

## ✅ Sjekkliste

### Generelt

- [ ] Branchen er rebaset på `main` eller main er merget inn.
- [ ] [Test-sjekklisten](#test-sjekkliste) er gjennomført hensiktsmessig.

### Test-sjekkliste

Testes lokalt av author og evt reviewer(e) etter forespørsel eller eget skjønn. Endringer som er isolert til kun API kan testes via Swagger hvis det er hensiktsmessig. Om dette dog kan påvirke eksisterende funksjonalitet i frontend (som f.eks endringer i eksisterende endepunkter), bør testing skje via frontenden. Man kan da heller linke til frontend PR'en herfra, så slipper man å definere testing dobbelt opp.

NB: Det er lov å bruke skjønn her! Har du gjort en veldig liten endring som åpenbart ikke påvirker funksjonalitet, trenger du ikke teste.

<!-- Fjern gjerne denne linjen hvis du har fulgt sjekklisten til punkt og prikke -->
Hvis du har avviket fra sjekklisten, beskriv kort hvilke vurderinger du har gjort og hva du evt har testet:

Om det er hensiktsmessig, bruk sjekklisten under. Sannsynligvis er det mer relevant å lenke til PR for frontend og vise til testingen som er gjort der.

<!-- Slett listen om du heller linker til frontend PR -->
- Introduserte endringer funker som forventet.
- Sjekk at man kan hoppe mellom RoS'er
- Sjekk at RoS kan opprettes
  - Initiell RoS
  - Kan velge kryptonøkkel
- Sjekk at RoS kan oppdateres, både i table og drawer (trykk refresh på et tiltak f.eks).
- Sjekk eventuelle nye/endrede UI-elementer i både dark- og lightmode.
- Verifiser endringer med designer(e) eller minst ett annet teammedlem hvis teamet er uten designer

---
