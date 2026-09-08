# Numbers

Every constant in the production code, where it lives, and why it has that value. 
Each is either **imposed** by the brief or **chosen**, the chosen ones are the only values open to argument.

## Money

| Constant | Value | Where | Origin | Why this value |
| --- | --- | --- | --- | --- |
| AED scale | 2 decimals | `CurrencyUnit` | Imposed | Fewer would lose the cents. |
| BHD scale | 3 decimals | `CurrencyUnit` | Imposed | Fewer would lose the thousandths. |

Amounts that would need rounding to be stored are refused; calculations round
on purpose, at their own boundary, with an explicit mode. Nothing is converted
between currencies.

## Fees and interest

| Constant | Value | Where | Origin | Why this value |
| --- | --- | --- | --- | --- |
| Overdraft fee | AED 25.00, at most once per account and value day | `DailyClosing.OVERDRAFT_FEE` | Imposed | The posting is -25.00. No BHD fee is defined, so a negative BHD close is refused rather than charged at an invented rate. |
| Fee threshold | closing balance strictly below 0 | `DailyClosing.feesToAssess` | Imposed | Zero is not an overdraft. Holds play no part; only the ledger balance counts. |
| Daily rate | 0.0004 | `Interest.DAILY_RATE` | Imposed | Exactly the required 0.04% a day. |
| Interest window | Days 1 to 6, one capitalization per account | `Interest.FINAL_DAY` | Imposed | The window and the single credit. The journal itself accepts any positive day; the limit is the interest policy's. |
| Interest basis | balances above 0 only | `Interest.calculate` | Imposed | Zero accruals are still recorded as daily facts, and a zero total still closes the window. |
| Rounding | `HALF_EVEN`, at the account's scale | `Interest.calculate` | **Chosen** | Half-even is the unbiased choice. It never decides anything in the six-day scenario, where no daily product is a tie; the 12.50 and 37.50 fixtures are what pin it. |

## Authorizations

| Constant | Value | Where | Origin | Why this value |
| --- | --- | --- | --- | --- |
| Approval boundary | available balance at or above 0 after the hold | `Authorizations.auth` | Imposed | Available funds may land exactly on zero. Requiring more would refuse valid commands; allowing less would permit unfunded holds. |
| Hold amount | strictly above 0 | `Authorization` | **Chosen** | Zero would reserve nothing, and a negative hold would increase availability. There is no minimum above the currency's smallest unit. |

## Journal

| Constant | Value | Where | Origin | Why this value |
| --- | --- | --- | --- | --- |
| First day | 1 | `Command`, `Posting`, `Journal.balance` | Imposed | The brief starts at Day 1. Days are positive whole numbers; there is no calendar. |
| Sequence | starts at 1, steps by 1; 0 is the empty history | `Journal` | **Chosen** | Each accepted entry takes exactly one position, however many postings it carries. Repeats and rejections take none, so sequence gaps cannot occur. |

## Scenario

| Constant | Value | Where | Origin | Why this value |
| --- | --- | --- | --- | --- |
| Accounts | `ACC-001` opening AED 0.00, `ACC-002` opening BHD 0.000 | `Scenario` | Imposed | The brief's two accounts. Each run starts from these, so repeated runs reproduce the same journal. |
| Events E1 to E10 | amounts, booked days and value days | `Scenario.run` | Imposed | The brief's stream, in arrival order. README lists every event and the journal it produces. |
