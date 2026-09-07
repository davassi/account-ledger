# Worklog

Timestamp in UTC, riferiti alle attività effettive.

- 2026-09-07T12:51:11Z — Verificati repository senza commit, assenza di AGENTS.md applicabili, Java 21.0.12 e Maven 3.6.3. Presente una configurazione utente in .cursor/, esclusa dal lavoro.
- 2026-09-07T12:52:50Z — Verificate le dipendenze Maven già in cache. Scelti Money e CurrencyUnit come primo componente, con JUnit per i test e precisione degli input verificata tramite UNNECESSARY.
- 2026-09-07T12:55:00Z — Creati i due tipi Java, otto test, configurazione Maven e documenti di progetto. Eseguito `mvn -o test`: 8 test, 0 fallimenti, 0 errori, 0 saltati. Verificata l'assenza di spazi finali nei file aggiunti.
- 2026-09-07T13:38:50Z — Avviato il componente di ripartizione in rate. Verificati Money, suite esistente, documentazione e stato Git; il codice del primo componente è invariato rispetto al commit precedente.
- 2026-09-07T13:41:31Z — Implementato Money.split mediante quoziente e resto in unità minime BigInteger; aggiunti 13 casi di test e aggiornati i documenti. Eseguito `mvn -o test`: 21 test, 0 fallimenti, 0 errori, 0 saltati. Verificati i diff con `git diff --check` e rivisti codice e test.
- 2026-09-07T15:18:27Z — Avviato il componente journal. Rilevate modifiche locali preesistenti a CurrencyUnit, Money e MoneySplitTest, principalmente commenti e formattazione; le aggiunte del nuovo componente usano file separati.
- 2026-09-07T15:22:13Z — Definiti Account, Posting e Journal con gruppi immutabili, sequenza per gruppo e query per valuta e conoscenza. Scelta la validazione completa prima dell'inserimento per rendere atomiche le rate di E10.
- 2026-09-07T15:25:46Z — Implementati i tre tipi e i test del journal: viste storiche, rate, duplicati, validazione, collezioni immutabili e saldi iniziali. In corso l'aggiornamento della documentazione prima della verifica della suite.
- 2026-09-07T15:27:02Z — Eseguito `mvn -o test`: 37 test, 0 fallimenti, 0 errori, 0 saltati, inclusi 16 casi del journal. Controllati i diff dei documenti e gli spazi finali dei nuovi sorgenti; rivista l'implementazione del journal.
- 2026-09-07T15:56:27Z — Implemented Authorization and Authorizations with immutable decisions in Journal.Entry, current available balances, account-scoped authorization IDs and retries that preserve the original outcome. Replaced a draft subtraction reduction with a direct loop. Ran `mvn -o test`: 58 tests, 0 failures, 0 errors, 0 skipped, including 21 authorization cases. Settlement and hold release remain the next component.
