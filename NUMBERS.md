# Numeri

## Componente Money

| Numero | Origine e motivazione |
| --- | --- |
| AED: 2 decimali | Imposto dal requisito; dimezzare la scala perderebbe i centesimi. |
| BHD: 3 decimali | Imposto dal requisito; una scala inferiore perderebbe i millesimi. |
| Zero | Identità della somma e risultato di un importo sommato al proprio opposto; non è una soglia configurabile. |
| 1200.00, 950.00, 620.00 | Importi E1, E2 ed E7, usati nei test. Producono 250.00 e -370.00; dimezzarli cambierebbe la traccia richiesta. |
| 1.2, 1.2000, 1.20, 1.200 | Rappresentazioni dello stesso valore usate per verificare normalizzazione ed uguaglianza. Il valore è esemplificativo; conta la diversa scala. |
| 1.001 AED, 1.0001 BHD | Input non rappresentabili alle rispettive scale, che devono essere rifiutati. |
| 0.10 + 0.20 = 0.30 | Esempio di aritmetica decimale esatta. Dimezzare i valori non aggiungerebbe una proprietà diversa. |
| 0.001 + 0.002 = 0.003 BHD | Verifica operazioni in millesimi; dimezzare 0.001 produrrebbe un input non rappresentabile. |
| 1.00 AED e 1.000 BHD | Importi di uguale valore numerico usati per isolare l'incompatibilità delle valute. |

## Versioni di build

| Versione | Motivazione |
| --- | --- |
| Java 21 | JDK già disponibile nell'ambiente; usato come release di compilazione. |
| JUnit 5.10.5 | Versione già in cache; permette test standard senza un runner personalizzato. |
| Maven Resources 3.3.1, Compiler 3.13.0, Surefire 3.5.2 | Versioni già in cache, fissate per non dipendere dai default di Maven. |
| POM 4.0.0 | Versione del formato Maven, non un parametro applicativo. |
| Progetto 0.1.0-SNAPSHOT | Identificativo iniziale di sviluppo. |

Le versioni identificano strumenti e formati: dimezzarle non ha un significato
funzionale. Il progetto è verificato con Maven 3.6.3, presente nell'ambiente.

## Numeri previsti dai componenti successivi

La traccia impone sei giorni, saldi iniziali zero, una commissione AED 25.00,
un tasso giornaliero dello 0.04% (fattore esatto 0.0004), un massimo di una
commissione per conto e giorno, e tre rate per il credito BHD 10.000.
Questi valori non sono parametri scelti per ottimizzare un risultato.
