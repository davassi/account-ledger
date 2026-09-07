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
| Rate uguali | Interpretarle come il più possibile uguali: dividere unità minime intere e assegnare il resto alle prime rate. La differenza massima è un'unità minima. |
| Ripartizione di zero e importi negativi | Supportati. Il resto conserva il segno: dividere un importo opposto compensa le stesse rate, nelle stesse posizioni. |
| Numero di rate e rate zero | Richiedere un conteggio positivo. Ammettere rate zero quando le unità minime disponibili sono meno delle rate richieste. |
| Modificabilità delle rate | Restituire una lista immutabile di Money; una ripartizione calcolata conserva il suo totale. |

## Applicate nello storico

| Ambiguità | Decisione |
| --- | --- |
| Valuta e saldo iniziale del conto | Account conserva ID e Money iniziale; la valuta è già contenuta in Money. Le definizioni dei conti sono fissate alla costruzione del journal. |
| Un evento con più movimenti | Un unico gruppo Journal.Batch conserva ID, giorno contabilizzato e movimenti. Una sola sequenza rende visibili insieme tutte le rate. |
| E10 arriva dopo E9 ma dichiara Giorno 5 | Conservare sequenza di registrazione, giorno contabilizzato e giorno valuta separatamente. Non imporre ordine crescente al giorno contabilizzato. |
| Saldo storico | La query specifica giorno valuta e sequenza massima conosciuta, entrambi inclusivi. Il saldo iniziale è incluso una volta sola; le viste storiche restano interrogabili dopo nuovi inserimenti. |
| Limiti temporali | Giorni interi positivi, senza fissare sei nel core. La sequenza zero precede la prima registrazione; cutoff negativi o oltre l'ultima registrazione sono rifiutati. |
| Valuta futura | Ammessa anche dopo il giorno contabilizzato: il movimento incide soltanto dal proprio giorno valuta. |
| Duplicati nel journal | Stesso ID, giorno contabilizzato e lista ordinata di movimenti: restituire il gruppo originale. Dati differenti con lo stesso ID: errore. La deduplicazione degli input completi sarà compito del motore degli eventi. |
| Concorrenza e atomicità | Un solo thread. Validare e copiare tutti i movimenti prima di aggiungere il gruppo. Un errore non aggiunge movimenti e non consuma sequenze. |
| Lettura dello storico | Restituire copie immutabili, anche delle liste interne; modifiche alle collezioni del chiamante non alterano conti o registrazioni. |
| Gruppo senza movimenti | Ammesso: registra i metadati e avanza la sequenza senza cambiare il saldo. Decisioni ed errori dettagliati saranno aggiunti con il motore degli eventi. |
| Rifiuti tecnici | Il journal solleva eccezioni prima della registrazione. Il motore aggiungerà la registrazione degli esiti e dei diagnostici. |
| Movimenti compensativi | Ammessi come altri Posting con segno opposto. Risolvere e validare il riferimento a un evento originale appartiene al futuro motore degli storni. |

## Decisioni di lavoro per i componenti successivi

| Ambiguità | Decisione |
| --- | --- |
| Chiusura giornaliera | Il runner fornirà chiusure esplicite dopo E2, E3, E4, E6, E8 ed E10, rispettivamente per i giorni 1–6. |
| Report storico | Distinguere le chiusure originali dalle revisioni, indicando il limite di conoscenza usato nella query. |
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
| Fee in BHD | Importo non specificato e non necessario alla traccia. Un conto BHD negativo richiede una politica esplicita; non inventare un cambio o una fee BHD 25. |
| Test volutamente fallente | Aggiungere un controesempio eseguibile separatamente sulla dipendenza delle fee dal momento dello storno, quando quella politica sarà implementata. |
