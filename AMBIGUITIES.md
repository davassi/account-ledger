# Ambiguità e decisioni

## Applicate nel componente Money

| Ambiguità | Decisione |
| --- | --- |
| Precisione degli input | Rifiutare valori che richiedono arrotondamento. I calcoli futuri arrotonderanno esplicitamente al proprio confine. |
| Zeri finali e uguaglianza | Normalizzare ogni importo alla scala della valuta, anche passando direttamente dal costruttore. |
| Importi negativi e zero | Ammessi in Money: servono per saldi, movimenti e rettifiche. La validazione di richieste positive appartiene alle operazioni. |
| Valute diverse | Somma e sottrazione richiedono la stessa valuta. Nessuna conversione implicita. |
| Valori mancanti | Valuta e importo obbligatori; usare le eccezioni standard Java. |
| Limite massimo dell'importo | Nessun limite di business specificato; mantenere l'aritmetica BigDecimal esatta senza MathContext che tronchi i risultati. |

## Decisioni di lavoro per i componenti successivi

| Ambiguità | Decisione |
| --- | --- |
| E10 arriva dopo E9 ma dichiara Giorno 5 | Conservare ordine di registrazione, giorno contabilizzato e giorno valuta separatamente. |
| Chiusura giornaliera | Il runner fornirà chiusure esplicite dopo E2, E3, E4, E6, E8 ed E10, rispettivamente per i giorni 1–6. |
| Saldo storico | Ogni query specificherà giorno valuta e sequenza massima conosciuta. Un report distinguerà chiusure originali e revisioni. |
| Fee su movimenti retrodatati | Riesaminare i giorni già chiusi, dalla valuta dell'evento in avanti, in ordine cronologico. Il giorno corrente si valuta alla chiusura. |
| Data delle fee storiche | Registrarle quando scoperte, con valuta del giorno riesaminato. La fee di un giorno influisce sui saldi successivi. |
| Fee dopo uno storno | Conservare gli addebiti già applicati; eventuali rimborsi sono movimenti compensativi distinti. Append-only non impedisce tali rimborsi. |
| Regolamento senza autorizzazione | Considerarlo movimento autorevole: addebitare e registrare l'anomalia. È una politica scelta, non imposta dalle regole non negoziabili. |
| Disponibilità insufficiente | Il controllo riguarda le autorizzazioni. Addebiti e regolamenti possono rendere negativo il saldo. |
| Decisioni sulle autorizzazioni | Usare le informazioni disponibili al momento dell'elaborazione; conservarne l'esito durante le revisioni storiche. |
| Regolamento inferiore al blocco | Addebitare il regolato e liberare l'intero blocco. La traccia richiede un solo regolamento completo per autorizzazione. |
| Scadenza dei blocchi | Nessuna scadenza implicita nella finestra. Un'autorizzazione rifiutata resta rifiutata dopo nuovi accrediti. |
| Storno | Compensare i movimenti principali dell'evento indicato, senza estendere automaticamente lo storno alle fee derivate. Rifiutare riferimenti invalidi o un secondo storno completo. |
| Arrotondamento degli interessi | HALF_EVEN, alla precisione del conto. La scelta rileva nei casi a metà, che la traccia non definisce. |
| Interesse retrodatato | Rettificare gli accrual giornalieri mediante nuovi fatti; mantenere tali accrual fuori dal saldo spendibile fino alla capitalizzazione. |
| Base dell'interesse | Saldo aggiornato dopo le fee, positivo soltanto. L'interesse del Giorno 6 è calcolato prima della capitalizzazione della finestra. |
| Totale capitalizzato | Somma esatta dei sei accrual giornalieri arrotondati e rettificati; un solo accredito per conto e finestra. |
| Rate uguali | Interpretarle come il più possibile uguali: distribuire unità minime, assegnando il resto alle prime rate. |
| Duplicati | Stesso ID e stesso contenuto: nessun secondo effetto finanziario. Contenuto diverso con lo stesso ID: errore. |
| Concorrenza e atomicità | Replay su un solo thread; registrare insieme tutti i fatti prodotti da un input valido. |
| Fee in BHD | Importo non specificato e non necessario alla traccia. Un conto BHD negativo richiede una politica esplicita; non inventare un cambio o una fee BHD 25. |
| Test volutamente fallente | Aggiungere un controesempio eseguibile separatamente sulla dipendenza delle fee dal momento dello storno, quando quella politica sarà implementata. |
