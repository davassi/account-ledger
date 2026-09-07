# Account ledger

Implementazione incrementale di un ledger in memoria. `Money` gestisce importi
immutabili in AED e BHD, con somma, sottrazione, cambio di segno e ripartizione
esatta in rate.
`Account`, `Posting` e `Journal` aggiungono conti, movimenti e uno storico
append-only interrogabile per giorno valuta e sequenza conosciuta.

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

```java
var installments = Money.of(CurrencyUnit.BHD, "10.000").split(3);
// Importi: 3.334, 3.333, 3.333. Totale: 10.000 BHD.
```

`split` restituisce una lista immutabile. Distribuisce le unità minime rimanenti
alle prime rate, conservando il segno dell'importo. Sono ammessi importi zero
e negativi, e rate zero quando le unità minime sono meno delle rate richieste.
Il numero di rate deve essere positivo, altrimenti viene generata
`IllegalArgumentException`.

## Registrare e interrogare i movimenti

```java
var journal = new Journal(
        new Account("ACC-001", Money.of(CurrencyUnit.AED, "0.00")),
        new Account("ACC-002", Money.of(CurrencyUnit.BHD, "0.000")));
journal.append("E1", 1, List.of(new Posting("ACC-001", 1, Money.of(CurrencyUnit.AED, "1200.00"))));
journal.append("E2", 1, List.of(new Posting("ACC-001", 1, Money.of(CurrencyUnit.AED, "-950.00"))));
long before = journal.lastSequence();
var late = journal.append("E7", 5,
        List.of(new Posting("ACC-001", 2, Money.of(CurrencyUnit.AED, "-620.00"))));

journal.balance("ACC-001", 2, before);          // 250.00 AED
journal.balance("ACC-001", 2, late.sequence()); // -370.00 AED, prima delle fee
```

Negli esempi i tipi applicativi sono nel package `accountledger`; `List` è
`java.util.List`. La valuta del conto deriva dal saldo iniziale.

`append` registra un gruppo immutabile di movimenti, con ID dell'evento, giorno
contabilizzato e sequenza assegnata dal journal. Le rate di E10 entrano insieme
nello stesso gruppo. Ogni movimento conserva il proprio giorno valuta; le
sequenze seguono l'ordine di arrivo anche se il giorno contabilizzato arretra.

`balance(accountId, valueDay, knownThroughSequence)` somma al saldo iniziale
soltanto i movimenti del conto con valuta e sequenza entro i limiti inclusivi
indicati. La sequenza zero rappresenta il saldo iniziale. `batches()` restituisce
una copia immutabile dello storico al momento della lettura.

Una ripetizione identica dello stesso gruppo restituisce la registrazione
originale. Un ID riutilizzato con dati differenti, un conto sconosciuto o una
valuta incompatibile generano `IllegalArgumentException` prima di aggiungere
movimenti. Tutte le operazioni avvengono su un singolo thread.

Il journal registra già movimenti compensativi. Il motore degli eventi aggiungerà
la validazione dei riferimenti degli storni, le autorizzazioni, gli errori e le
chiusure giornaliere. Il replay completo e il controesempio volutamente fallente
restano nelle fasi successive.

Le scelte sono raccolte in [AMBIGUITIES.md](AMBIGUITIES.md), i numeri in
[NUMBERS.md](NUMBERS.md), i criteri rifiutati in [REJECTED.md](REJECTED.md)
e le attività effettive in [WORKLOG.md](WORKLOG.md).
