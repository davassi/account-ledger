# Ambiguities and decisions

Acceptance review correction: criterion 4 is a valid settlement requirement.
The implementation now rejects E6 without a debit. Criterion 6 can describe
net fee restoration via append-only credits, so fee retention is not forced
by the journal model. Its refund interpretation remains open; the current
code retains assessed fees. See [REJECTED.md](REJECTED.md) for the complete audit.

## Applied in the Money component

| Ambiguity | Decision |
| --- | --- |
| Input precision | Reject values that would require rounding. Future calculations will round explicitly at their own boundary. |
| Trailing zeros and equality | Normalize every amount to the currency scale, including when it comes straight from the constructor. |
| Negative and zero amounts | Allowed in Money: they are needed for balances, postings and adjustments. Validating that a request is positive belongs to the operations. |
| Different currencies | Addition and subtraction require the same currency. No implicit conversion. |
| Missing values | Currency and amount are mandatory; use the standard Java exceptions. |
| Maximum amount limit | No business limit is specified; keep BigDecimal arithmetic exact, with no MathContext that would truncate results. |
| Multiplication and rounding | Accept a BigDecimal factor and an explicit RoundingMode. Multiply exactly, then round once to the account currency's scale. There is no implicit rounding default. UNNECESSARY requires an exactly representable result. |
| Factor precision and sign | Do not normalize the factor to the currency scale. Zero and negative factors are valid arithmetic; deciding which balances earn interest belongs to the interest component. |
| Sign queries | Money.signum returns the sign of the stored, already normalized amount. A product rounded to zero has sign zero. |
| Equal installments | Read as as-equal-as-possible: divide whole minor units and assign the remainder to the first installments. The maximum difference is one minor unit. |
| Splitting zero and negative amounts | Supported. The remainder keeps the sign: splitting the opposite amount compensates the same installments, in the same positions. |
| Installment count and zero installments | Require a positive count. Allow zero installments when the available minor units are fewer than the requested installments. |
| Mutability of the installments | Return an immutable list of Money; a computed split preserves its total. |

## Applied in the history

| Ambiguity | Decision |
| --- | --- |
| Account currency and opening balance | Account holds the ID and the opening Money; the currency is already carried by Money. Account definitions are fixed when the journal is constructed. |
| One event with several postings | A single Journal.Entry holds the ID, the booked day and the postings. One sequence makes all the installments visible together. |
| E10 arrives after E9 but declares Day 5 | Keep the recording sequence, the booked day and the value day separate. Do not impose an increasing order on the booked day. |
| Historical balance | The query specifies the value day and the maximum known sequence, both inclusive. The opening balance is included exactly once; historical views stay queryable after new insertions. |
| Time bounds | Journal postings and queries accept positive whole days. The interest policy limits daily closes to the specified six-day window. Sequence zero precedes the first record; cutoffs that are negative or beyond the last record are rejected. |
| Future value day | Allowed even after the booked day: the posting only takes effect from its own value day. |
| Duplicates in the journal | Compare immutable Request values, including ID, booked day, input type and all input fields. Identical requests return the original Entry; conflicting IDs fail before computing effects. New event kinds will extend Request.Data with their full input metadata. |
| Concurrency and atomicity | A single thread. Validate and copy every posting before adding the entry. A failure adds no postings and consumes no sequence. |
| Reading the history | Return immutable copies, including of the internal lists; changes to the caller's collections do not alter accounts or records. |
| Entry with no postings | Allowed: it records the metadata and advances the sequence without changing the balance. Authorization decisions, closes without fees and reassessments without new fees use these entries. |
| Technical rejections | The journal raises exceptions before recording. Replay keeps expected domain rejections in a separate append-only list of attempts. Accepted settlement anomalies stay in the journal. |
| Compensating postings | Reversals resolves and validates the original event, then appends the opposite postings. Direct Journal.append remains the lower-level operation and does not infer reversal references. |

## Applied in authorizations

| Ambiguity | Decision |
| --- | --- |
| Authorization decisions | Use the information available at processing time; preserve the outcome across historical revisions. A declined authorization stays declined after new credits. |
| Hold amount and approval threshold | Require a strictly positive amount in the account's currency. Approve when the remaining available balance is zero or positive; declines create no active hold. |
| Authorization timing | The API takes the current processing day, supplied by the caller. A hold starts at its recording sequence and is not backdated by a monetary value day. The event engine will carry the full input metadata. |
| Available balance query | Require the value day and an explicit knowledge cutoff. Both ledger postings and hold decisions use the same prefix. The value day filters monetary postings; holds take effect at their recording sequence. |
| Original closes versus later knowledge | Use the sequence saved at a daily close to reconstruct its available balance and decisions. An earlier value day combined with a later cutoff uses the holds known at that later prefix; it is not the original close. No authorization outcome is recalculated. |
| Authorization identity | The ID is unique within its account, including after a decline or settlement. Rejecting a settlement for an unknown reference does not reserve that reference; a later authorization may introduce it. Different accounts can use the same ID. |
| Repeated authorization event | The same event ID, day, account, authorization ID and amount return the stored outcome without reevaluation or another entry. Different data conflict. |
| Decision storage | Journal.Entry keeps the original Request and immutable Effects containing postings, authorization states, diagnostics and accruals. Read holds from that history; maintain no second store of decisions or balances. |
| Scope of the journal | Own retry detection and validate accounts, currencies and immutable effects before append. The operation supplies a calculation that runs only for new input and must not mutate the journal. |
| Invalid request versus decline | Expected domain failures raise RequestRejectedException before append; Replay records those attempts. Insufficient availability is a valid declined decision, recorded in the journal. |
| Hold lifecycle | Approved holds remain active until final settlement, with no implicit expiry. |

## Applied in settlements

| Ambiguity | Decision |
| --- | --- |
| Settlement without authorization | Reject without a debit or hold change, as criterion 4 requires. Look up the reference within the requested account. Replay records the rejected attempt; no accepted journal entry is created. This replaces the earlier incompatible policy. |
| Settlement after a decline | Accept the debit with DECLINED_AUTHORIZATION. Criterion 4 covers absent references; this reference is present. The declined state remains unchanged and other holds remain active. |
| Final versus partial settlement | Treat each accepted settlement as final and allow one per account and recorded authorization reference. Partial captures are outside this increment; the brief does not specify their lifecycle. |
| Settlement amount versus hold | Charge the actual positive amount and release the whole approved hold, even when the amounts differ. Available funds do not limit settlement amounts. |
| Atomic settlement and stored hold amount | Append the debit and a new SETTLED state together. Preserve the original hold amount in that state; the request and posting contain the settled amount. No earlier record changes. |
| Hold release timing | Release at the settlement's recording sequence; apply the debit at its own value day. Saved earlier cutoffs retain the hold, including for backdated or future-value settlements. |
| Historical authorization states | decisions(cutoff) returns all recorded states; states(cutoff) projects the latest per account and ID, in first-seen order. Only a latest APPROVED state reserves funds. |
| Invalid settlement | Require a nonblank reference, a positive amount, a valid account, matching currency and positive days. A failed request records neither a debit nor a hold release. |
| Settlement retry | Journal compares the complete original Request, including both dates. An identical retry returns its original entry before recalculating state or checking for a second settlement. |
| Authorization arrives after a rejected settlement | An explicit retry may succeed once the matching authorization exists. Preserve the original settlement dates and the earlier rejection; do not retry automatically. |
| Diagnostic context | Store anomaly codes in immutable Effects. Account, event ID, authorization reference and dates are already in the same entry's Request. |

## Applied in reversals

| Ambiguity | Decision |
| --- | --- |
| Reversal amount and currency | Derive both from the original postings. The caller supplies a reference, not a second amount that could disagree. |
| Eligible reference | Require an already recorded event with at least one posting. Reject unknown or self references, hold-only entries and empty entries. The original booked day need not be earlier: recording sequence determines what is known. |
| Account scope | Every original posting must belong to the requested account. Reject multi-account entries as a whole; do not silently reverse only some postings. |
| Installments and partial reversal | Reverse every posting in its original order, preserving exact minor units. Partial amounts are unsupported; all compensations share one sequence. |
| Reversal value day | Apply the requested value day to every compensation. E9 specifies Day 2. A later value day is allowed but will not restore earlier daily balances; reversal is a dated posting, not deletion. |
| One reversal per event | Reject a second full reversal under a new event ID. Recover that fact from recorded Reversal requests, without a mutable flag on the original. |
| Reversal of a reversal | Reject it. The chosen lifecycle supports one compensation of an original monetary event, without chains of reversal references. |
| Reversing credits or settlements | Negate their postings without checking availability. Refund a settlement without reopening its hold, permitting another capture or copying its diagnostic; the original states and anomalies stay in the journal. |
| Fees and authorization decisions | Compensate only postings in the referenced entry. Separate fees and recorded authorization decisions are not recalculated. DailyClosingTest now verifies this with the full event stream and actual fee assessments. |
| Retry and invalid input | Journal compares the original Request before computing effects. Changes to account, reference or either date conflict. Invalid requests append nothing and consume neither the event ID nor the original event's reversal allowance. |
| Reference lookup and historical views | Journal.find shares event lookup with retry detection and requires an explicit cutoff. A later reversal cannot alter an earlier prefix or the original entry. |

## Applied in daily closes and fees

| Ambiguity | Decision |
| --- | --- |
| Close granularity and order | Close one account at a time, in consecutive days starting at Day 1. The caller supplies explicit event IDs. The scenario closes both accounts after E2, E3, E4, E6, E8 and E10, for days 1–6. |
| Storage of closes | Request.DayClose identifies the account and its booked day is the day closed. Record the marker even when no fee is due. Its entry sequence preserves the original close, including fees and accruals. Close and fee-reassessment entries contain only fee monetary postings; interest capitalization uses a separate entry. |
| Revised closes | Reassessment adds a Request.FeeReassessment entry; it does not replace the original close or advance the last closed day. At least one day must already be closed. |
| Processing date versus booked date of an input | The caller supplies the actual processing day to reassess. It must not precede the last closed day. E10 keeps booked Day 5 in its own entry, but its reassessment occurs on Day 6. |
| When to reassess | The caller invokes reassess after a backdated posting and before the next event. The current open day is assessed only when closed. There are no hidden callbacks that modify the journal during an append. |
| Range and order of reassessment | Recheck all closed days from Day 1, in chronological order. For this small window, a full scan avoids additional range-tracking state and also catches missed earlier assessments when closing a new day. |
| Fee basis and cascading effects | Use the ledger balance at the current cutoff, plus newly calculated earlier fees in the same operation. Existing fees already enter the ledger balance. Active holds do not determine fee eligibility. |
| Once-per-day rule | Read assessed value days from the fee postings in close and reassessment entries. An explicit refund leaves the original assessment present, so that day cannot be charged twice. |
| Atomic fee calculation | Calculate all new fees and the resulting accruals locally, then append them with the close or reassessment marker in one immutable entry. A failed calculation records no partial fees or accruals and consumes no event ID. |
| Date and amount of historical fees | Debit AED 25.00 per negative value day, booked on the processing day when discovered. The Day 2 fee leaves Day 3 at positive AED 5.00 after E7, so no Day 3 fee is due. |
| Fees after a reversal | Current behavior retains fees already assessed; E9 compensates E7 only. Audit finding: criterion 6 can require restoration of net fees through new credits. Such refunds respect append-only. The policy must explicitly resolve net economic restoration versus preserving historical assessment facts before claiming compliance. |
| Snapshot and fee queries | Use the saved close sequence to reconstruct the original balance and authorization states. fees(accountId, cutoff) returns an immutable list of original fee postings for that account, excluding later compensating credits. |
| Repeated close or reassessment | Identical requests return the original entry without recalculating. A new request may reassess a closed day, but may not close it again. Changed account, day or request type conflicts with a reused event ID. |
| BHD overdraft fee | Unspecified. A negative BHD assessment fails atomically; zero and positive BHD balances close normally. This component deliberately has no invented BHD rate or currency conversion. |
| Deliberately failing test | FeeTimingCounterexample runs explicitly with -Dtest=FeeTimingCounterexample and fails on AED 0.00 versus -25.00. It exposes how retaining assessed fees makes reversal timing matter; the ordinary suite excludes it by name. |

## Applied in interest

| Ambiguity | Decision |
| --- | --- |
| Interest rounding | HALF_EVEN, at the account's precision. The choice matters in the half-way cases, which the brief does not define. |
| Interest basis | Positive closing ledger balances after fees, excluding holds. Include newly calculated fees only from their value day. Day 6 interest is computed before capitalization. |
| When accruals are recorded | DailyClosing.close and reassess calculate fees first, then accruals, and append both atomically. No separate accrual command or mutable cache is needed. |
| Backdated interest | Append a complete set of accruals for all closed days on every close or reassessment. Include zero amounts and unchanged days. For six days, a complete snapshot is simpler than merging per-day corrections or summing signed deltas. Earlier sets remain unchanged. |
| Historical accrual query | Interest.accruals(accountId, cutoff) returns the last complete set at the explicit cutoff, in day order, or an empty list before the first close. Each set belongs to exactly one account. Do not sum sets from different revisions. |
| Spendability | Accrual is a separate fact in Effects, validated against the account's currency. Only capitalization creates a posting; accruing interest never changes ledger or available balance. |
| Capitalized total | Sum the six rounded daily accruals exactly. Do not round the sum of unrounded products or discard a difference. Each account receives one posting booked and valued on Day 6. |
| Zero total | Record a zero posting and the capitalization marker. This explicitly finalizes the window even when no interest was earned. |
| Preconditions for capitalization | Require all six daily closes and an up-to-date set of accruals. A later posting affecting the account on or before Day 6 requires reassessment first, even if its rounded interest effect would be zero. Other accounts and future value days do not invalidate the set. |
| Finalization and later events | Capitalize only after the input stream, including E10. Freeze accruals afterward: further closes or reassessments fail. The low-level journal can still accept postings, but this component does not reopen finalized interest or correct it automatically. Post-capitalization adjustments are outside the specified window. |
| Capitalization retries and reversal | Journal handles retries before finalization checks. Another event ID cannot capitalize the same account again. Explicit reversal compensates a capitalization posting without deleting its marker or permitting another capitalization. |
| Window scope | This policy supports days 1–6 and one capitalization per account. Multiple periods and configurable schedules are outside the brief; the journal itself retains generic value days. |

## Applied in rejected attempts

| Ambiguity | Decision |
| --- | --- |
| Where to record a rejection | Replay owns a private append-only list of Rejection(processingDay, request, reason). The journal continues to contain accepted effects only, so failed settlement or reversal requests cannot affect lifecycle projections. |
| Processing date | Record the caller-supplied positive processing day separately from Request.bookedDay. Dispatch preserves the request's dates and fields. The caller supplies the appropriate dates for holds, closes and reassessments; Replay does not schedule them or rewrite input metadata. |
| Expected failure versus programming error | Catch only RequestRejectedException, a subtype of IllegalArgumentException used by domain checks. Do not broadly catch runtime exceptions. For example, a null hold amount still raises NullPointerException and stops processing. |
| Input boundary | Accept an already constructed Request. Invalid top-level IDs, dates, money precision or null lists can fail during construction before process is called; raw-input parsing and recording those failures are outside this typed API. An invalid processing day or null request is a caller error and is not added to the rejection log. |
| Successful handling | process returns true for an accepted request or identical accepted retry, including authorization declines and accepted settlement diagnostics. It returns false after recording an expected rejection, allowing the caller to continue. |
| Retry after rejection | A failed attempt reserves neither an event ID nor a journal sequence. Repeating it records another failure if still invalid; it can succeed if corrected or if the missing prerequisite arrives. The prior attempts stay immutable. |
| Conflicting event ID | Preserve the accepted event and record each conflicting submission separately. Do not overwrite it or duplicate Journal's request comparison. A later identical retry of the accepted request still succeeds. |
| Failure context | Keep the original Request and the exception's explanatory message. No stack trace, mutable Throwable or second copy of monetary data is stored. |
| Scope and snapshots | Keep one Replay instance per run. rejections() returns an immutable copy in attempt order. Save that copy with each close's journal cutoff; rejected attempts do not advance journal sequences, so a cutoff alone cannot reconstruct their history. A new Replay instance starts a new rejection log. |
| Shared currency validation | Authorizations uses Journal's account-currency check before arithmetic so a wrong-currency hold raises the same expected rejection as other operations. Money remains a general arithmetic value type. |
| Capitalization input date | The dispatcher passes the submitted booked day to Interest, which validates Day 6 inside Journal's lazy calculation. Rejected or conflicting requests retain their actual input date; the public convenience method still supplies Day 6. |

## Applied in the executable scenario

| Ambiguity | Decision |
| --- | --- |
| Scenario representation | Keep the ten typed input requests in Scenario.run, grouped by actual processing day. Keep the close, reassessment and capitalization calls explicit beside the events. No sorting by booked day or generic scheduling rules are introduced. |
| E10 and the day boundary | E10 is processed on Day 6 after E9, with booked and value Day 5. Reassess ACC-002 on Day 6 before closing that day and capitalizing. |
| Close and capitalization IDs | Generate CLOSE-account-day and INTEREST-account. Retain E7-FEES, E9-FEES and E10-FEES for reassessments. These derived IDs are distinct from E1–E10 and stable across runs. |
| Executable state | Each invocation creates the two accounts, a new Journal and one Replay. Component instances used for dispatch read the same journal; rejected attempts remain in that run's Replay. |
| Output in this increment | Print final ledger balances after capitalization, latest authorization states, accepted diagnostics and rejected attempts. Preserve currency precision through Money's decimal representation. A per-day report remains separate work. |
| Rejections while driving the scenario | Replay records expected failures and continues; Scenario prints their processing day, event ID and reason at the end. E6 is the sole rejected attempt. Auth-B's decline is an accepted decision. The suite verifies numerical results and ordering. |
| Runnable command | run.sh changes to the project directory, compiles with Maven offline and invokes the Java main class. No new build plugin or runtime dependency is required. Prime the dependency cache with mvn test when needed. |
| One complete scenario definition | Move the complete input sequence out of DailyClosingTest into Scenario. The test now uses recorded event and close cutoffs from that same executable scenario, retaining its assertions without duplicating the stream. |

## Working decisions for the later components

| Ambiguity | Decision |
| --- | --- |
| Historical report | Distinguish original closes from revisions, stating the journal cutoff and saving the rejection snapshot. Include the separate capitalization entry in the final Day 6 snapshot. |
| Insufficient availability | The check applies to authorizations. Charges and settlements may drive the balance negative. |
