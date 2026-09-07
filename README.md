# Account ledger

Implementazione incrementale di un ledger in memoria. Il primo componente è
`Money`: importi immutabili in AED e BHD, con somma, sottrazione e cambio di segno.

## Eseguire i test

Servono Java 21 e Maven. Dalla directory del progetto:

```sh
mvn test
```

Con le dipendenze già in cache è disponibile anche `mvn -o test`.
Surefire stampa il numero di test, errori e fallimenti; `BUILD SUCCESS` indica
che la suite è passata. I risultati sono in `target/surefire-reports/`.

## Usare Money

```java
var balance = Money.of(CurrencyUnit.AED, "1200.00")
        .subtract(Money.of(CurrencyUnit.AED, "950.00"));
// balance.amount() è 250.00; balance.currency() è AED.
```

Gli input devono essere rappresentabili esattamente nella valuta: `1.2000` AED
diventa `1.20`, mentre `1.001` AED genera `ArithmeticException`. Le operazioni
tra valute diverse generano `IllegalArgumentException`.

I prossimi componenti sono la ripartizione in rate, lo storico, le autorizzazioni
e le chiusure giornaliere. Il replay di sei giorni e il controesempio volutamente
fallente appartengono alle fasi successive.

Le scelte sono raccolte in [AMBIGUITIES.md](AMBIGUITIES.md), i numeri in
[NUMBERS.md](NUMBERS.md), i criteri rifiutati in [REJECTED.md](REJECTED.md)
e le attività effettive in [WORKLOG.md](WORKLOG.md).
