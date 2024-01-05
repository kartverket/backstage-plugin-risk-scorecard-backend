# ROS-as-code
Proof-of-concept på dokumentasjon og skjema for å lage og jobbe med en kryptert risiko-og sårbarhetsanalyse (ROS) nær koden.

Grunntanken er at en ROS bør endre seg med koden og at det derfor er hensiktsmessig at ROSen ligger _sammen_ med koden.
Det er også en del informasjonselementer som er ganske standard, som åpner for å lage en strukturert ROS, som (i noen grad) kan være maskinlesbar.

I utgangspunktet bør ikke en kodenær ROS røpe mer enn en trusselaktør kan utlede fra koden uansett.
Samtidig er det mulig å argumentere for at en ROS oppsummerer sårbarheter på en slik måte at en angriper kan få en lettere jobb.

Løsningen her er å lage et _skjema_ [JSON Schema](https://json-schema.org/) for en strukturert ROS, og kryptere informasjonselementene i .yaml-dokumentet som følger skjemaet.

Vi har benyttet [sops](https://github.com/getsops/sops) for å realisere dette, og tatt utgangspunkt i at IDEen er IntelliJ. Det finnes flere plugins til IntelliJ for å editere filer sammen med sops. Vi har brukt [Simple sops edit](https://plugins.jetbrains.com/plugin/21317-simple-sops-edit).

Til å lage nøkkelpar og kryptere/dekryptere ROSer har vi her i dokumentasjonen benyttet [age](https://github.com/FiloSottile/age) og [GCP KMS](https://cloud.google.com/security/products/security-key-management?hl=en), men det finnes støtte for en rekke nøkkelsystemer i sops.

[Her er et eksempel på ukryptert ROS](eksempel/ukryptert.ros.yaml), og det finnes en [kryptert ROS for dette repo'et](Kartverket/ros-as-code/.sikkerhet/ros/ROSaC.ros.yaml).

Det finnes også en [videosnutt som viser hvordan du oppretter en sikker ROS og forvalter den i IntelliJ](eksempel/Opprette%20og%20endre%20ROS%20720p.mov) når du har gjort oppsettet under.

## Oppsett SOPS

### Installasjon
Installer [sops](https://github.com/getsops/sops), f.eks `brew install sops`

Last ned [Simple sops edit](https://plugins.jetbrains.com/plugin/21317-simple-sops-edit) og legg til som plugin til IntelliJ

### Oppsett for et repo
Vi antar ROSer skal ligge i katalogen `.sikkerhet/ros` på rot i repo'et, og at ROS-filene har suffikset `.ros.yaml`.
Opprett filen `.sops.yaml` i `.sikkerhet/`-katalogen. Den bør inneholde regulære uttrykket for å identifisere ros-filene,
og nøklene for de krypteringsmetodene som er i bruk:

```
creation_rules:
  - path_regex: \.ros\.yaml$
    age: '<AGE_PUBLIC_KEY>'
    gcp_kms: <GCP_KEY_ID>
    ...(andre nøkler) ...
```
Konfigurering av krypteringsnøkler er beskrevet under.

* I et shell naviger til `.sikkerhet/ros` i repo'et.
* Kjør `sops <navn>.ros.yaml`. Standard editor vil åpnes (default er vim).
* Endre eksempel-innholdet til å inneholde gyldig yaml, f.eks linjen, `versjon: "1.0"`

I IntelliJ burde du nå finne `.sikkerhet/ros/<navn>.ros.yaml`. Hvis du åpner den burde du se det krypterte innholdet og et banner øverst med en edit-knapp.
Trykker du på den, åpnes en dekryptert versjon av fila. Endrer du og lukker den, vil den krypterte fila oppdateres. Den commit'es med øvrige endringer i koden.

## Konfigurere krypteringsnøkler (mesternøkler)

Det antas her at `.sops.yaml` finnes i `.sikkerhet/`-katalogen. Når nøkler fjernes eller legges til kan du da kjøre:

`sops updatekeys <navn>.ros.yaml`

og få rekryptert data-nøkkelen med de gjeldende mesternøklene.

### Konfigurere age

* Sørg for at [age](https://github.com/FiloSottile/age) er installert, f.eks `brew install age`
* Hvis du har en `keys.txt` med mesternøkkelen, legg den i `$HOME/Library/Application Support/sops/age/keys.txt` (på OS X)

#### Generere ny mesternøkkel
* Opprett nøkkelpar med `age-keygen -o keys.txt`. Ta vare på offentlig nøkkel, heretter kalt `<AGE_PUBLIC_KEY>`
* På OS X må `keys.txt` ligge i `$HOME/Library/Application Support/sops/age/keys.txt`
* Alle på teamet må ha den private nøkkelen (altså `keys.txt`). Den må nødvendigvis distribueres på en sikker måte, f.eks med 1Password eller Dashlane.
* Oppdater `./sikkerhet/.sops.yaml`:
```
creation_rules:
  - path_regex: \.ros\.yaml$
    ...
    age: '<AGE_PUBLIC_KEY>'
```
* Kjør `sops updatekeys <navn>.ros.yaml`

### Konfigurere GCP KMS
* Sørg for at Google-CLIen, `gcloud` er installert, f.eks `brew install --cask google-cloud-sdk`
* I et konsoll kjør `gcloud auth application-default login`. Det vil logge deg inn på GCP (i en nettleser) og lagre lokalt påloggingsinformasjon som benyttes av GCP-biblioteker (som sops bruker)

Er repo'et satt opp med ROSer kryptert med GCP KMS og du har fått tilgang til mesternøkkelen, er dette alt som skal til.

#### Generere ny mesternøkkel
* Naviger til [GCP KMS](https://console.cloud.google.com/security/kms/keyrings) pålogget med Kartverket-bruker
* Velg riktig prosjekt (eller opprett et nytt hvis du kan; det anbefales at nøkler holdes i et eget prosjekt)
* Opprett en "Key ring", f.eks kalt `ROS` (hvis du ikke har en egnet allerede). Velg `Multi-region` og `eur4 (Netherlands and Finland)`
* Lag en nøkkel, for eksempel `ROS-as-code`
* Under `Actions` i nøkkel-lista, velg `Copy resource name`. Det vil putte `<GCP_KEY_ID>` på utklippstavla. Den vil se noe slikt ut: `projects/<prosjekt-id>/locations/eur4/keyRings/ROS/cryptoKeys/ROS-as-code`
* Oppdater `./sikkerhet/.sops.yaml`:
```
creation_rules:
  - path_regex: \.ros\.yaml$
    ...
    gcp_kms: <GCP_KEY_ID>
```
* Kjør `sops updatekeys <navn>.ros.yaml`

## Validere en ros.yaml med skjema i IntelliJ

I `IntelliJ > Settings > Languages & Frameworks > Schemas and DTDs > JSON Schema Mappings` opprett nytt skjema:

* `Name`: Gi et passende navn, f.eks `ROS-validering`
* `Schema file or URL:` Legg inn url til ROS-skjema, [ros_schema_no_v1_0.json](https://kartverket.github.io/ros-as-code/schema/ros_schema_no_v1_0.yaml)
* `Schema version:` `JSON Schema version 4`
* Legg til `File path pattern:` `*ros.yaml`

Nå skal du kunne fylle ut ROS-analysen og få kontekst-hjelp og innholdet validert

## Rotere data-nøkkel

Det regnes som god praksis å rotere nøkler regelmessig. En ukryptert data-nøkkel på avveie gjør det mulig å dekryptere det ene dokumentet som er kryptert.

* I et shell naviger til `.sikkerhet/ros` i repo'et.
* Kjør `sops -r <navn>.ros.yaml`