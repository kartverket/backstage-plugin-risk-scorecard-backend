# GitHub APi

I denne løsningen brukes GitHub som database for alle ROS-analyser.
Api-et er godt dokumentert her [her](https://docs.github.com/en/rest), men her er det litt om hvordan vi bruker det.

## Vårt oppsett

Et repo kan inneholde flere ROS-analyser, og alle er lagret i en flat struktur under ```.security/ros```-mappen.
Dette gjør det ganske forutsigbart.

```bash
.
|____.security
| |____ros
| | |____ros-1.ros.yaml
| | |____ros-2.ros.yaml
| | |____ros-3.ros.yaml
| | |____ ...
```

## Test ut APIet via http-files

I filen ```.security/.backstage/.docs/http-files/github.http``` finnes det et sett med ferdige spørringer for å hente
informasjon om dette repoet.

For å bruke dette ligger environment-variablene ```http-client.env.json```. I tillegg til denne filen må du lage en egen
for authentication - ```http-client.private.env.json```:

```json
{
	"dev": {
		"accessToken": "<DITT_GIHUB_TOKEN>"
	}
}
```

## ROS State

En ROS kan i vårt tilfelle ha tre forskjellige states - publisert, kladd og sendt til godkjenning.
Når en ROS kun finnes i main-branchen og er godkjent, er status _publisert_.
Når en ROS finnes i en branch, men ikke enda i en pull request, er status _kladd_.
Her kan den også forekomme i main, men vi ser på branchen.
Når en ROS finnes i en PR, anser vi den som _sendt til godkjenning_, og den vil bli publisert når man merger og fjerner
kladde-branchen.

### Repository

Lag, oppdater eller slett filer i
repoet. [Her er eksempler for main-branch-greier](.security/.backstage/docs/http-files/repository-github-api.http).

### References

En git-referanse er en fil som inneholder en SHA-1-hash for en commit.
En referanse er basically et navn på en commit.
Denne filen med shaen kan skrives om til å peke på nye commits/sha1-hash.
En branch er altså en git-referanse som inneholder den nyeste commit-hashen din.

[Her er eksempler for referanse/branch-greier](.security/.backstage/docs/http-files/references-github-api.http).

### Pull requests

Lag og hent informasjon om pull requests. det kan du se eksempel
på [her](.security/.backstage/docs/http-files/pullrequests-github-api.http) 