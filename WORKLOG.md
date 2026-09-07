# Worklog

Timestamp in UTC, riferiti alle attività effettive.

- 2026-09-07T12:51:11Z — Verificati repository senza commit, assenza di AGENTS.md applicabili, Java 21.0.12 e Maven 3.6.3. Presente una configurazione utente in .cursor/, esclusa dal lavoro.
- 2026-09-07T12:52:50Z — Verificate le dipendenze Maven già in cache. Scelti Money e CurrencyUnit come primo componente, con JUnit per i test e precisione degli input verificata tramite UNNECESSARY.
- 2026-09-07T12:55:00Z — Creati i due tipi Java, otto test, configurazione Maven e documenti di progetto. Eseguito `mvn -o test`: 8 test, 0 fallimenti, 0 errori, 0 saltati. Verificata l'assenza di spazi finali nei file aggiunti.
- 2026-09-07T13:38:50Z — Avviato il componente di ripartizione in rate. Verificati Money, suite esistente, documentazione e stato Git; il codice del primo componente è invariato rispetto al commit precedente.
- 2026-09-07T13:41:31Z — Implementato Money.split mediante quoziente e resto in unità minime BigInteger; aggiunti 13 casi di test e aggiornati i documenti. Eseguito `mvn -o test`: 21 test, 0 fallimenti, 0 errori, 0 saltati. Verificati i diff con `git diff --check` e rivisti codice e test.
