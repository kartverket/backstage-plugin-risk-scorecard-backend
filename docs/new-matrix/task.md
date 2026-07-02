# Transition to new shared matrix for Kartverket

## User story

As a governance advisor I want the risk matrices in all of Kartverket’s solutions to be aligned, so that we can aggregate and evaluate risk across the organization

## Task

The task is to implement the new matrix (docs/new-matrix/matrix.md) along with new descriptions for probability (docs/new-matrix/sannsynlighet.md) and consequence (docs/new-matrix/konsekvens.md)

Currently risk is estemated using a logarithmic scale using a shared base, as outlined below:

> The estimated risk is a calculation based on the risks the different scenarios pose. If there is a high probability that a serious consequence will occur, this could potentially become a large cost for the organization. In other words, the cost is an attempt to make the risk value more tangible and is the sum of the estimated risk for all the risk scenarios in this risk scorecard.
> How do we calculate the estimated risk?
> Probability (P) is measured in incidents per year and consequence (C) is measured in cost (in NOK) per incident. The estimated risk is calculated as 20P+C-1 NOK/year.
> Probability (incidents/year)
> 1: 20-2 = 0.0025 incidents/year = Once every 400 years
> 2: 20-1 = 0.05 incidents/year = Once every 20 years
> 3: 200 = 1 incidents/year = Annually
> 4: 201 = 20 incidents/year = Monthly
> 5: 202 = 400 incidents/year = Daily
> Consequence (NOK/incident)
> 1: 203 = 8 000 NOK/incident = 1 workday
> 2: 204 = 160 000 NOK/incident = 1 work month
> 3: 205 = 3 200 000 NOK/incident = 1 work year
> 4: 206 = 64 000 000 NOK/incident = 20 work years
> 5: 207 = 1 280 000 000 NOK/incident = 400 work years
> Example
> A risk scenario with probability 4 (201 = 20 incidents/year) and consequence 2 (204 = 160 000 NOK/incident) has an estimated risk of 204+2-1 = 3 200 000 NOK/year.

The new matrix differs in the following ways:

1. ...
2. ...

## Subtasks

- [ ] Endre matrise slik at farger samsvarer
- [ ] Tilpasse beskrivelser og sannsynlighet og konsekvens iht nye beskrivelser
- [ ] Finne ut om dette krever en ny migrering og snakke om hvordan dette best bør håndteres
- [ ] Sette faste verdier for hvert konsekvensnivå
      Valg mellom eksponensiell formel vs sette faste verdier
- [ ] Vurdere hvordan vi kan veilede teamene til å finne riktig risikoeier basert på restrisiko i RoS-analysen (se Def risikoaksept i Excel-arket)
      Løses trolig gjennom retningslinjer, se svar fra Kristine her
