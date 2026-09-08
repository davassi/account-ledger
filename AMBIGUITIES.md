# Ambiguities and decisions

The brief left a number of things open. This file records each one and the
choice made, so that a reader can tell a deliberate decision from an accident.

It does not argue the acceptance criteria. That is
[REJECTED.md](REJECTED.md), which covers all eight, including the two that were
refused on interpretation rather than on arithmetic.

## Money and currencies

| Open question | Decision |
| --- | --- |
| Input precision | Refuse any value that would need rounding to be stored. Calculations round on purpose, at their own boundary. |
| Trailing zeros and equality | Every amount is stored at its currency's number of decimals, including amounts passed straight to the constructor. Two amounts that are worth the same are equal. |
| Negative and zero amounts | Allowed. Balances, postings and corrections all need them. Whether a particular request must be positive is the operation's business, not Money's. |
| Mixing currencies | Adding and subtracting require the same currency. Nothing is converted implicitly. |
| Missing values | Currency and amount are both required, reported with the standard Java exceptions. |
| An upper limit on amounts | The brief sets none. Arithmetic stays exact, with nothing configured that would quietly truncate a result. |
| Multiplying and rounding | Take a factor and an explicit rounding mode. Multiply exactly, then round once, to the account currency's precision. There is no default rounding mode; asking for "no rounding" requires a result that already fits. |
| Precision and sign of the factor | The factor is not forced to the currency's precision. Zero and negative factors are valid arithmetic. Deciding which balances earn interest belongs to the interest rules. |
| Asking for the sign | The sign is read from the stored, already-rounded amount. A product that rounds to zero has sign zero. |
| Equal installments | Read as *as equal as the currency allows*. Divide the whole minor units and give the remainder to the earliest installments, so no two differ by more than one minor unit. |
| Splitting zero and negative amounts | Supported. The remainder follows the sign, so splitting the opposite amount cancels the same installments in the same positions. |
| Number of installments | Must be positive. Some may come out as zero when there are fewer minor units than installments. |
| Changing a split afterwards | The returned list cannot be modified, and its parts always add back to the original. |

## The journal

| Open question | Decision |
| --- | --- |
| Account currency and opening balance | An account carries its ID and its opening amount, and the amount already carries the currency. Accounts are fixed when the journal is built. |
| One event, several postings | One entry holds the event ID, the booked day and all the postings. A single sequence number makes them visible together. |
| E10 arrives after E9 but claims Day 5 | Recording order, booked day and value day stay independent. Booked days are not required to increase. |
| Historical balances | A query names the value day and how much history it may see. Both bounds include their endpoint. The opening balance counts once, and old views keep working after new entries arrive. |
| Limits on days | Postings and queries take positive whole days. The six-day limit comes from the interest rules, not the journal. Sequence zero means "before anything was recorded"; a cutoff that is negative or past the end is refused. |
| A value day in the future | Allowed, even later than the booked day. The posting simply takes effect from its own value day. |
| Repeated events | Compare the whole request: ID, booked day, type and every field. An identical request returns the original entry. The same ID with different content is refused before anything is calculated. |
| Concurrency | One thread. Everything is validated and copied before the entry is added, so a failure leaves no partial postings and uses no sequence number. |
| Reading history | Callers get copies they cannot modify, including of the inner lists. Nothing a caller does to what it received can reach the records. |
| An entry with no postings | Allowed. It records what happened and moves the sequence forward without moving any money. Hold decisions, closes with no fee and reassessments with no new fee all look like this. |
| Failures | The journal raises an exception before recording anything. Replay keeps expected business rejections in its own separate list. Accepted-but-odd settlements stay in the journal with a diagnostic. |
| Compensating postings | Reversals looks up and checks the original event, then adds the opposite postings. The plain append stays the lower-level operation and never infers a reversal on its own. |

## Authorizations

| Open question | Decision |
| --- | --- |
| What a decision is based on | Whatever was known when it was made. It is never recalculated. A decline stays a decline after money arrives later. |
| Amount and approval threshold | The amount must be positive and in the account's currency. Approve when applying the hold still leaves availability at zero or above. A decline holds nothing. |
| When a hold takes effect | The caller supplies the current processing day. A hold starts at the moment it is recorded and is never backdated by a value day. |
| Asking for available funds | The query needs both the value day and how much history it may see. The value day filters money movements; holds count from the point they were recorded. |
| Original closes versus later knowledge | To rebuild an original close, use the sequence saved at that close. Pairing an old value day with a recent cutoff gives a different, legitimate view — not the original one. No decision is ever re-run. |
| Authorization identity | An ID is unique within its account, and stays used after a decline or a settlement. Refusing a settlement for an unknown reference does not reserve that reference, so a later authorization may still introduce it. Two accounts may use the same ID. |
| The same authorization sent twice | Same event ID, day, account, authorization ID and amount returns the stored outcome, with no re-evaluation and no second entry. Different content is a conflict. |
| Where decisions live | In the journal entry, alongside the original request. Holds are read back from that history. There is no second store of decisions or balances to fall out of step. |
| What the journal is responsible for | Spotting repeats, and checking accounts, currencies and effects before recording. The operation hands it a calculation that runs only for genuinely new input and must not modify the journal. |
| An invalid request versus a decline | An invalid request raises a rejection before anything is recorded, and Replay logs the attempt. Not enough money is not an error — it is a valid decision, recorded in the journal. |
| How a hold ends | An approved hold stays active until it is settled. Nothing expires on its own. |

## Settlements

| Open question | Decision |
| --- | --- |
| Settlement with no authorization | Refuse it, with no debit and no change to any hold, as criterion 4 requires. The reference is looked up within the requested account. Replay logs the attempt; no entry is created. |
| Settlement after a decline | Accept the debit and attach a `DECLINED_AUTHORIZATION` note. Criterion 4 is about references that are absent; this one is present. The decline stands, and other holds stay active. |
| Final or partial | Every accepted settlement is final, and one is allowed per account and reference. Partial captures are out of scope — the brief does not describe their lifecycle. |
| Amount different from the hold | Charge the actual amount and release the whole hold, even when the two differ. Available funds do not cap a settlement. |
| Recording the debit and the release | Both go in together, in one entry. The new state keeps the *original* hold amount; the request and the posting carry the settled amount. No earlier record changes. |
| When the hold is released | At the moment the settlement is recorded, while the debit lands on its own value day. Earlier saved views still show the hold, including for backdated or future-dated settlements. |
| Reading past states | One query returns every recorded state; another keeps only the latest per account and reference, in the order first seen. Only a latest *approved* state reserves money. |
| Invalid settlements | Require a non-blank reference, a positive amount, a real account, a matching currency and positive days. A failed request records neither a debit nor a release. |
| The same settlement sent twice | The journal compares the whole original request, both dates included. An identical repeat returns its original entry before any state is recalculated or any second-settlement check runs. |
| The authorization turns up later | An explicit retry may then succeed. The original settlement dates and the earlier rejection are both kept. Nothing retries by itself. |
| Context for a diagnostic | The code is stored with the effects. The account, event ID, reference and dates are already on the same entry's request, so they are not copied. |

## Reversals

| Open question | Decision |
| --- | --- |
| Amount and currency | Both come from the original postings. The caller gives a reference, not a second amount that could disagree with the first. |
| What can be reversed | An already recorded event with at least one posting. Unknown references, self-references, hold-only entries and empty entries are all refused. The original booked day need not be earlier — what matters is what was already recorded. |
| Which account | Every posting in the original must belong to the requested account. A multi-account entry is refused as a whole rather than partly reversed. |
| Installments and partial reversal | Every posting is reversed, in its original order, to the exact minor unit. Partial amounts are not supported, and all the compensating postings share one sequence. |
| The value day of a reversal | The requested value day applies to every compensating posting. E9 asks for Day 2. A later day is allowed but will not restore earlier daily balances: a reversal is a dated posting, not a deletion. |
| Reversing twice | A second full reversal under a new event ID is refused. That fact is recovered from the recorded reversal requests, with no flag set on the original entry. |
| Reversing a reversal | Refused. The chosen lifecycle allows one compensation of an original event, without chains. |
| Reversing credits or settlements | Their postings are negated with no availability check. Refunding a settlement does not reopen its hold, allow another capture, or copy its diagnostic. The original states and notes stay where they are. |
| Fees and hold decisions | Only the postings in the referenced entry are compensated. Separately assessed fees and recorded decisions are left alone. |
| Repeats and invalid input | The journal compares the original request before calculating anything. A changed account, reference or date is a conflict. An invalid request records nothing and uses up neither the event ID nor the original event's one allowed reversal. |
| Looking up the reference | The same lookup used for repeat detection, with an explicit cutoff. A later reversal cannot change an earlier view or the original entry. |

## Daily closes and fees

| Open question | Decision |
| --- | --- |
| What a close covers, and in what order | One account at a time, on consecutive days from Day 1, with the caller supplying the event IDs. The scenario closes both accounts after E2, E3, E4, E6, E8 and E10. |
| How closes are stored | A close names its account, and its booked day is the day being closed. The marker is recorded even when no fee is due, and its sequence preserves that close along with its fees and accruals. Closes and reassessments carry fee postings only; capitalization gets its own entry. |
| Revised closes | A reassessment is a new entry. It does not replace the original close and does not move the last-closed day forward. At least one day must already be closed. |
| Processing day versus booked day | The caller passes the real processing day to a reassessment, and it may not be earlier than the last closed day. E10 keeps its booked Day 5, but the reassessment it triggers happens on Day 6. |
| When to reassess | The caller asks for it after a backdated posting and before the next event. The current open day is only assessed when it closes. Nothing modifies the journal behind the caller's back during an append. |
| How far back to check | Every closed day from Day 1, in order. Over a six-day window a full pass avoids keeping extra range-tracking state, and it also catches anything missed earlier. |
| What a fee is based on | The ledger balance at the current cutoff, plus any earlier fees calculated in the same operation. Fees already recorded are part of the balance. Holds play no part. |
| Once per day | Assessed days are read back from the fee postings already in close and reassessment entries. An explicit refund leaves the original assessment in place, so that day still cannot be charged twice. |
| Calculating fees safely | All new fees and the resulting accruals are worked out first, then recorded together with the marker in one entry. A failed calculation leaves no partial fees and uses no event ID. |
| Date and amount | AED 25.00 for each negative value day, booked on the day it was discovered. After E7, the Day 2 fee leaves Day 3 at positive AED 5.00, so Day 3 is not charged. |
| Fees after a reversal | Already assessed fees stay. E9 compensates E7's principal and nothing else. Explicit refunds would sit comfortably with an append-only design, but the brief gives no automatic refund policy and none is invented here. |
| Reading fees back | Use the saved close sequence to rebuild the original balance and states. The fee query returns the original fee postings for an account, and does not net off later compensating credits. |
| Repeating a close or reassessment | An identical request returns the original entry without recalculating. A new request may reassess a closed day, but may not close it a second time. A changed account, day or type conflicts with a reused ID. |
| No dinar overdraft fee | The brief does not give one. A negative BHD balance that would need a fee fails the close, as a whole, changing nothing. Zero and positive BHD balances close normally. No rate is invented and nothing is converted. |

## Interest

| Open question | Decision |
| --- | --- |
| Rounding | Half-even, at the account's precision. It only matters for exact halves, which the brief does not address. |
| What earns interest | Positive closing balances after fees, ignoring holds. New fees count from their own value day. Day 6 interest is worked out before capitalization. |
| When accruals are recorded | Every close and reassessment calculates fees first, then accruals, and records both together. There is no separate accrual command and no cache to go stale. |
| Backdated interest | Each close or reassessment records a complete set of accruals for every closed day, zero amounts and unchanged days included. Over six days a full snapshot is simpler than merging per-day corrections. Earlier sets are left untouched. |
| Reading accruals back | The query returns the most recent complete set at the given cutoff, in day order, or nothing before the first close. Each set belongs to one account. Sets from different revisions must never be added together. |
| Can accrued interest be spent | No. An accrual is a recorded fact, checked against the account's currency, and it changes no balance. Only capitalization creates an actual posting. |
| The capitalized total | Exactly the sum of the six rounded daily accruals. The unrounded products are not re-added and re-rounded, and no difference is discarded. Each account gets one posting, booked and valued on Day 6. |
| A total of zero | Still recorded, as a zero posting plus the marker, so the window is explicitly finished even when nothing was earned. |
| What must be true first | All six days closed, and an up-to-date set of accruals. A later posting affecting the account on or before Day 6 forces a reassessment first, even when the interest effect would round to zero. Other accounts and later value days do not invalidate the set. |
| After capitalization | Accruals are frozen: further closes and reassessments fail. The journal underneath still accepts postings, but this component will not reopen finished interest or correct it by itself. Corrections after the window are out of scope. |
| Repeats and reversal | Repeat detection runs before the finality checks. A different event ID cannot capitalize the same account twice. An explicit reversal can compensate the posting without erasing the marker or unlocking another capitalization. |
| How wide the policy is | Days 1 to 6, one capitalization per account. Multiple periods and configurable schedules are outside the brief; the journal itself keeps ordinary value days. |

## Rejected attempts

| Open question | Decision |
| --- | --- |
| Where a rejection goes | Replay keeps its own append-only list of the processing day, the request and the reason. The journal holds accepted effects only, so a failed settlement or reversal can never influence a later lifecycle check. |
| Which date is recorded | The caller's processing day, kept separate from the request's booked day. Replay passes dates and fields through unchanged; it does not schedule anything or rewrite input. |
| Expected failure versus programming mistake | Only the domain rejection type is caught. Runtime faults are not swallowed — a null amount, for example, still stops processing. |
| Where input checking stops | Replay takes an already-built request. Bad IDs, dates, precision or null lists can fail while the request is being built, before Replay sees it. Parsing raw input, and logging failures there, is outside this boundary. An invalid processing day or a null request is a caller mistake and is not logged as a rejection. |
| What counts as handled | Processing reports success for an accepted request or an identical accepted repeat, including declines and accepted diagnostics. It reports failure after logging an expected rejection, so the caller can continue. |
| Retrying after a rejection | A failed attempt claims neither an event ID nor a sequence. Repeating it logs another failure while it stays invalid, and it can succeed once corrected or once the missing prerequisite arrives. Earlier attempts are never altered. |
| A conflicting event ID | The accepted event stands, and each conflicting submission is logged separately. The journal's comparison is not duplicated here. A later identical repeat of the accepted request still succeeds. |
| What is kept about a failure | The original request and the explanatory message. No stack trace, no mutable exception, no second copy of the amounts. |
| Scope and snapshots | One Replay per run. The rejection list is handed out as an unmodifiable copy in attempt order. Save that copy alongside each close's cutoff: rejected attempts do not move the journal forward, so a cutoff alone cannot reconstruct them. A new Replay starts a new log. |
| Checking currency in one place | Authorizations uses the journal's account-currency check before doing arithmetic, so a wrong-currency hold fails the same way as everything else. Money stays a general-purpose value type. |
| The date on a capitalization | The dispatcher passes the submitted booked day through, and the Day 6 rule is checked inside the calculation. A rejected or conflicting request keeps its actual date; the convenience method still supplies Day 6. |

## The runnable scenario

| Open question | Decision |
| --- | --- |
| How the scenario is written | The ten typed requests sit in one method, grouped by the day they are actually processed, with reassessments written out explicitly. Ending a day closes both accounts, capitalizes after the Day 6 closes, and saves the report boundary. Nothing is sorted by booked day. |
| E10 and the day boundary | E10 is processed on Day 6, after E9, with booked and value Day 5. ACC-002 is reassessed on Day 6 before that day closes and interest is capitalized. |
| Generated event IDs | Closes and capitalizations get predictable generated IDs, and the reassessments keep theirs. All are distinct from E1–E10 and stable between runs. |
| State per run | Every run builds the two accounts, one journal and one Replay. The operations all read that journal, and rejected attempts stay with that run. |
| What is printed each day | Both accounts, every day: the original closing and available balances, the revised closing balance, newly assessed fees with their value days, the hold states as they stood, errors and accepted diagnostics — at full currency precision. |
| Rejections during the run | Replay logs the failure and carries on. Each failed attempt is shown once, on the day it arrived, with its ID and reason. E6 is the only rejection; Auth-B's decline is a valid decision, not an error. |
| The runnable command | The script builds offline with Maven and runs the main class. No extra build plugin or runtime dependency is needed; run the test suite once to populate the dependency cache. |
| One definition of the scenario | The scenario is defined once and used by both the command and the test. Financial assertions reuse its recorded cutoffs rather than restating the stream. Individual close and fee rules are covered by their own test. |

## Daily reporting

| Open question | Decision |
| --- | --- |
| Saving a day | After each day's work, save one journal cutoff and a copy of the running rejection list. Days are saved in order, from one journal and one Replay. The report reads those saved points and keeps no balance model of its own. |
| Original versus revised | Closing and available balances use that day's saved cutoff. The revised closing balance uses the last saved cutoff for the same value day. Both cutoffs are printed, so later postings cannot silently change either view. |
| Day 6 | Saved only after both accounts have closed and been capitalized, so its balances include interest while earlier days do not. |
| Showing fees | Show what was added since the previous saved point, as a positive charge with its value day. E7's three fees appear on processing Day 5 with value days 2, 4 and 5, and are not copied back into earlier closes. |
| Errors and repeated attempts | Take the difference between consecutive saved rejection lists, keeping repeats. Booked day plays no part in deciding which report day an attempt belongs to. |
| Accepted diagnostics | Show those from entries added since the previous cutoff, kept separate from rejections, in arrival order even for backdated requests. |
| Hold states | Show them as they stood at each original close. Auth-A is approved on Days 2 and 3, then settled; Auth-B stays declined after E9. Decisions are never re-evaluated against revised balances. |
| Table layout | One ASCII table per day, one row per account. Currency applies to every amount in the row, including fees. Columns fit the full contents and balance columns align right. Errors and diagnostics appear below the table. |
| Where output goes | To a stream supplied by the caller, so the command uses standard output and tests can capture it without touching global state. Printing never writes to the journal, and an empty report prints nothing. |

## Package layout

| Open question | Decision |
| --- | --- |
| Boundaries | `domain` holds monetary values, `core` holds the ledger and request processing, and `scenario` holds the runnable stream and report. `domain` stands alone, `core` uses it, `scenario` uses both. |
| Keeping internals internal | The journal and the accounting operations share a package, so their shared methods stay package-private. Replay sits in `core` because it processes arbitrary requests. |
| Tests | Mirror the production packages. Whole-stream financial assertions live with the scenario, so the core tests neither depend on it nor force extra methods open. |
