# Criteri rifiutati

Le conclusioni seguenti derivano dall'analisi della traccia. Sono implementati
Money, ripartizione in rate, conti e journal. MoneySplitTest verifica la
conservazione del totale di E10; JournalTest verifica retrodatazioni, viste
storiche e contabilizzazione atomica delle rate. Commissioni, interessi e
autorizzazioni saranno verificati nei componenti successivi.

| Criterio | Motivo del rifiuto |
| --- | --- |
| 2 — E7 causa una sola fee | La revisione storica rende negativi i giorni 2, 4 e 5. Dopo la fee del giorno 2, il saldo del giorno 3 resta positivo a 5.00. Servono tre assessment distinti, scoperti il giorno 5. |
| 7 — Tre rate da BHD 3.334 | La somma sarebbe 10.002. La ripartizione conservativa è 3.334, 3.333, 3.333. |
| 8 — Scartare il resto degli interessi | Violerebbe l'uguaglianza imposta tra accrual giornalieri arrotondati e credito capitalizzato. Il totale sarà definito dalla loro somma. |

## Rifiuti dipendenti dalla politica scelta

| Criterio | Motivo e alternativa |
| --- | --- |
| 4 — Rifiutare ogni regolamento senza autorizzazione | La politica scelta considera il regolamento autorevole: E6 addebita 180.00 e registra un'anomalia. La politica opposta sarebbe coerente con le sole regole non negoziabili; il criterio non è universalmente falso. |
| 6 — E9 ripristina automaticamente saldi e fee | La politica scelta conserva le fee. E9 compensa soltanto i 620.00 di E7. Una politica di rimborso potrebbe ripristinare i valori economici aggiungendo nuovi movimenti, senza violare append-only. |

I criteri 1, 3 e 5 sono accettati. Il criterio 1 è ora verificato tramite la query
del saldo al Giorno 2 dopo E7, prima delle fee. Il criterio 5 è condizionale:
nella traccia Auth-B risulta rifiutata, quindi un test separato dovrà esercitare
il caso approvato.

## Approcci abbandonati durante l'implementazione

Nessun approccio è stato implementato e poi abbandonato finora.
Questa sezione verrà aggiornata soltanto quando ciò avverrà realmente.
