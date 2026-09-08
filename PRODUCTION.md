# Production notes

MAL is deliberately small: one in-memory, append-only journal and a set of
operations that derive balances, holds, fees and interest from it. An accepted
[Journal.Entry](src/main/java/accountledger/core/Journal.java) stores the original
request and all its effects under one sequence, so an event is either fully
visible or absent. Reversals add compensating postings; they never rewrite
history. Identical requests are idempotent, while reuse of an event ID with
different data is rejected.

The most valuable decision is that a balance has two time dimensions:
`valueDay` says when money takes effect and `knownThroughSequence` says which
facts were known. This preserves both an original close and its later corrected
view. The trade-off is that almost every decision is currently rebuilt by
scanning history.

This note describes the production consequences of that implementation. It is
an engineering assessment, not a claim that the exercise is a complete banking
ledger or compliance system.

## Append-only at scale

One hundred copies of this tiny fixture would still be only a few thousand
entries, so claiming a numeric breaking point would be false precision. In a
sustained workload, CPU and allocation pressure would emerge first because work
per request grows with the full history. A representative benchmark is still
required before sizing a service.

| Path | Current behaviour | Consequence at scale |
| --- | --- | --- |
| Append | Retry detection copies the permitted journal prefix and scans it for the event ID. N new events therefore require O(N²) total lookup work. | Ingestion slows as history grows, even when every new event is simple. |
| Balance | Every query scans the accepted prefix and filters postings by account and value day. | Authorization, close and report latency grows with total journal size, including other accounts. |
| Availability | The balance is scanned, then all authorization decisions are scanned again to rebuild latest hold state. | A busy account pays repeatedly for settled and declined authorizations that no longer reserve money. |
| Closing | Fee and interest work repeatedly scans closed days and prior derived entries. | Backdated entries make end-of-day work increasingly expensive. |

State also grows without a bound:

- `Journal` retains every request, posting, authorization state, diagnostic and
  accrual for the process lifetime.
- `Replay` retains every rejected attempt in memory.
- `DailyReport` copies the cumulative rejection list into every saved snapshot;
  with a long window and regular failures, that can grow quadratically with the
  number of days.
- Active holds have no expiry, so both their state and their reserved amount can
  remain forever.

The cheapest throughput improvement is an event-ID index:
`Map<String, Journal.Entry>`. A new append becomes a direct lookup instead of a
history scan. The map must be updated only after a successful append, rebuilt
from accepted entries during recovery, and still enforce historical cutoffs by
checking the entry sequence. It preserves the journal as the source of truth
and does not change retry semantics.

That index delays the first performance failure but does not bound memory. The
next structural change would be a segmented journal: keep the current segment
and compact projections in memory, seal older immutable segments into durable
storage, and load them only for historical queries. A projection must retain
both value day and knowledge cutoff. A single cached closing balance is unsafe:
a posting recorded before the checkpoint may have a later value day, while a
posting recorded after it may be backdated into the closed period.

## Value-dated entries in production

The code distinguishes booked day, posting value day and journal sequence. It
does not store the accepted event's receipt timestamp, business date, source or
operator. In production those are separate facts, and all are needed to explain
why a balance changed.

A late value-dated entry can revise more than one number. It may alter prior
closes, overdraft fees, daily interest, available funds, statements, general
ledger reconciliation and downstream monitoring. In this implementation,
reassessment is an explicit command. Between the late posting and that command,
derived results can be stale without being marked stale. Interest has one
freshness check before capitalization, but capitalization itself is final and
there is no later adjustment workflow.

For a UAE-licensed financial institution, the customer-facing record must also
remain explainable. The CBUAE Consumer Protection Standards require detailed
account statements and transaction notifications; their disclosure provisions
include transaction dates and times. When the institution's own error affects a
consumer, clauses 5.1.1.38–5.1.1.42 require correction, refunds of resulting
costs, customer communication and action on the cause, with reporting for a
system-wide error. These rules do not prescribe this ledger's backdating model,
but they make silent revision of a closed period unacceptable.
[CBUAE Article 2: Disclosure and Transparency](https://rulebook.centralbank.ae/en/rulebook/article-2-disclosure-and-transparency)
and
[CBUAE Article 5: Business Conduct](https://rulebook.centralbank.ae/en/rulebook/article-5-business-conduct)
are the relevant primary references.

Before go-live I would add one **closed-period correction control**. Any request
whose value date precedes the latest closed business date would enter a pending
state and require approval from a different authenticated operator. The approval
would be bound to the exact request and retain:

- receipt time, source and authenticated maker;
- reason code and supporting evidence;
- affected account, value date and original event reference;
- authenticated approver and approval time.

Acceptance would atomically append the event, mark affected periods stale and
schedule fee, interest, statement and reconciliation recalculation. Publication
or capitalization of an affected period would remain blocked until those jobs
complete. The original close would stay queryable through its sequence; revised
outputs would carry a new version and correction reason.

## Authorization lifecycle

The model has three states: `APPROVED`, `DECLINED` and `SETTLED`. A matching
settlement moves an approval to `SETTLED`, posts the requested debit and releases
the entire original hold. Settlement amount and available funds do not limit
that debit.

Strictly speaking, the only way an authorization currently ends without a
matching settlement is an initial decline. The other common endings are absent,
which is itself a production decision that must be made explicit.

| Path without a matching approved settlement | Current model | Real-world meaning | Required production behaviour |
| --- | --- | --- | --- |
| Declined at authorization | Records `DECLINED`; no hold is created. Later funds do not change the decision. | Here it means insufficient available funds; a real issuer may also decline for limits, risk or account controls. | Keep the decision immutable and return it for identical retries. Record the decision reason, and require a new authorization identity for a new attempt. |
| Settlement references a declined authorization | Posts the debit once, records `DECLINED_AUTHORIZATION`, and leaves the state `DECLINED`. The reference is then considered used. | A late or forced presentment arrived despite the decline. | If input is a confirmed external movement, record it and open a reconciliation or dispute case. If input is only a capture command, reject it before money moves. The API must distinguish those contracts. |
| Approved authorization is abandoned | Remains `APPROVED` and reserves funds indefinitely. | The merchant never captures, or its completion message is lost. | Store an expiry time and append `EXPIRED` when it passes, releasing the hold. Define how a confirmed late presentment is recorded and investigated. |
| Merchant or customer cancellation | No transition exists. Reversal is rejected because a hold has no posting to compensate. | The purchase was voided before capture. | Append `CANCELLED` with actor, reason and network reference, then release the hold. Cancellation and settlement must race through one atomic transition. |
| Incremental or partial completion | No transition exists. The first settlement is final and releases the whole hold, whatever its amount. | Hospitality, fuel, tips or split shipments change or capture part of the expected amount. | Record adjustments and captures explicitly, enforce product tolerances, track the remaining hold and define the event that makes the authorization final. |

Two related cases are not alternative authorization endings. A settlement with
an unknown reference is rejected and creates no authorization; that is suitable
for a capture command, but a confirmed network presentment must be recorded to a
suspense or reconciliation path. Reversing a settlement compensates its postings
but leaves the authorization `SETTLED`; a refund or chargeback needs its own
lifecycle rather than reopening the original hold.

All new lifecycle transitions should remain append-only. They must be atomic
with their monetary effects, idempotent by external event identity and ordered
so only one terminal transition wins under concurrency.

## What you cut and why

The exercise optimizes for a small, inspectable core. Each omission avoids code
that the six-day scenario cannot validate, while deferring a specific production
risk.

| Simplification | Why it stayed out | Risk deferred to production |
| --- | --- | --- |
| In-memory process; no database or recovery | Persistence was explicitly out of scope. | A restart loses balances, audit history and idempotency knowledge. Durable append, atomic acknowledgement and replay are required together. |
| Single-threaded mutation | The scenario replays one ordered stream. | Concurrent holds, retries and settlements can pass checks against the same state. The whole decide-and-append operation must be serialized or transactional. |
| No web layer, queue or card-network adapter | Only the ledger core was requested. | Authentication, delivery guarantees, malformed payloads and the difference between commands and confirmed movements remain undefined. |
| One-sided account postings | The task asks for customer balances, not a general ledger. | There is no counterpart, trial balance or proof that money is conserved. A production subledger needs GL mapping and reconciliation; a true ledger needs balanced entries. |
| Two fixed accounts and currencies; no FX | The supplied stream contains only AED and BHD. | Account lifecycle, currency metadata, exchange rates and FX gains or losses are absent. BHD can go negative, but closing it fails because no BHD overdraft fee policy exists. |
| Integer Days 1–6 | It keeps the event examples deterministic. | Calendars, UAE business dates, source time zones, holidays and operational cut-off times are not represented. |
| Fixed fee, interest rate, rounding and window | The brief supplies one policy. | Product and regulatory changes cannot be applied by effective date. Historical recalculation needs versioned policy data. |
| Explicit reassessment after late entries | It makes the scenario sequence visible. | A missed command leaves fees and accruals stale. Production needs automatic impact detection, durable jobs and completion status. |
| One interest capitalization with no adjustment | The window ends on Day 6. | A later correction cannot repair capitalized interest. Production needs adjustment entries and period controls. |
| Available balance is the only approval rule; lifecycle has three states | Other issuer controls, expiry, cancellation, adjustment and partial capture were not required. | Account status, limits and risk controls are absent; holds can remain forever and real network messages cannot be represented safely. |
| Final settlement accepts any positive amount | The brief gives a final amount and no tolerance policy. | Under-capture, over-capture and forced presentment are indistinguishable; exposure and reconciliation ownership are undefined. |
| Full monetary reversal only | E9 reverses one complete event. | Partial refunds, chargebacks, fee remediation and authorization reopening have no dedicated identity or workflow. Fees therefore survive reversal unless explicitly compensated. |
| Accepted events lack receipt and actor metadata | The scenario supplies only an event ID and booked day. | Provenance, maker-checker evidence and an investigation timeline cannot be reconstructed. |
| Rejections and report snapshots stay in memory | They exist only to exercise and print the replay. | Failed attempts are not durable, snapshot copies grow, and no operational case or regulatory record survives restart. |
| No customer statements, notifications or regulatory reporting | The required output is a console report. | Corrected closes are not communicated, downstream reports are not versioned and consumer-remediation deadlines are not managed. |
| No access control, encryption, retention or observability | There is no service or stored customer dataset in scope. | A deployed system would lack protection, audit access, alerts, capacity evidence and incident diagnosis. |
| Functional tests without load or failure injection | The deliverable asks for a runnable suite, not a capacity programme. | Throughput, recovery time, partial infrastructure failures and memory limits remain unmeasured. |

The production order follows from those risks: define whether inputs are
commands or confirmed facts; add durability and atomic processing; enforce the
closed-period correction control; complete the authorization lifecycle; then
optimize measured hot paths with indexes, projections and segmented storage.
