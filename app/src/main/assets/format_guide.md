# Testmond Question Format Guide

This file describes the exact paste formats Testmond understands. You
can hand this whole file to an AI assistant (ChatGPT, Claude, etc.)
along with a request like "generate 20 questions about photosynthesis
using the combined format below", or "clean up and format this
question bank for Testmond" -- the assistant can follow these rules
directly and produce text you paste straight into the app.

Multiple choice and fill-in-the-blank questions can be freely mixed
in the same paste -- there is no separate mode to choose between
them. There are two paste formats (Combined, Separate Answer Key);
pick one per test (or per "Many questions" paste, when adding more
to an existing test from the Edit screen).

---

## The short version (rules that prevent almost every failed paste)

1. **Plain text only.** No title line, no `#` headings, no `---`
   dividers, no comments, no code fence around the block. (If a
   heading on its own line, a `---` line or an `<!-- HTML comment -->`
   line slips in, the app ignores it -- but leave them out.) Separate
   each question from the next with one blank line.
2. **Number every question** as `Q1.`, `Q2.`, `Q3.` ... (keep the
   source's numbers; if it has none, number them 1, 2, 3 ...). A bare
   `Q:` also works, but numbering is safer. The label is stripped off
   when the question is read; it never appears in the test.
3. **Multiple choice:** one option per line, lettered consecutively
   from A (`A.` `B.` `C.` `D.` -- `A)` or `(A)` also work). Never wrap
   an option onto a second line (see "How the app reads a question").
   Finish with `ANSWER:` and the **letter only** -- `ANSWER: C`, not
   `ANSWER: (C)`, `ANSWER: C.` or `ANSWER: C) text`.
4. **Fill-in-the-blank:** the card starts with a lone `*` on its own
   line, then `Qn.`, then the stem containing the blank as `_____`,
   then `ANSWER:` with the plain word(s) or number a person would type.
5. **Labels inside a question** (a list of statements, the columns of a
   match-the-following, an assertion and a reason): never start a line
   with a single letter followed by `.` or `)` -- not `A.`, `(a)`,
   `(A)`. Use Roman numerals `I, II, III, IV` (or `i, ii, iii, iv`)
   instead. Only the real answer options are lettered A, B, C, D.
6. **Math** goes in LaTeX between `$...$` (inline) or `$$...$$`
   (display). Never leave a lone `$` (write a dollar sign as `\$`).
7. **Tables** are Markdown pipe tables: a header row, then a
   `|---|---|` row, then the data rows, with no blank line inside. The
   app draws them to fit the screen. Keep them to about 4-5 columns and
   short cells (see "Tables").
8. **Don't invent.** Keep the source's answer key. If the answer is
   missing or you cannot tell, write `ANSWER: ?` -- the app then skips
   that one question and lists it in the Preview so it can be fixed.
   Don't add explanations, difficulty tags or commentary.

---

## How the app reads a question (why the rules above matter)

- **Question text** is everything from the first line up to the first
  option (or up to `ANSWER:` for a fill-in-the-blank). Lines are joined
  with their line breaks kept, so a multi-line stem, a word bank line
  or a paragraph break inside a question is fine.
- **Options** start at the first line that is lettered A. From there
  each line that looks like `B.` / `C)` / `(D)` is the next option.
  **Any other line after the options have started is ignored** -- so an
  option that wraps onto a second line loses everything after the first
  line. Keep every option on a single line.
- Because a lettered line starts the options, a line inside the stem
  such as `(a) statement one` or `A. Assertion` is taken for the first
  option and scrambles the question. That is why in-stem labels must be
  Roman numerals (rule 5).
- **`ANSWER:`** for multiple choice must be one of the option letters.
  Anything else (`?`, `(C)`, a word) makes the app skip that question
  and report it in the Preview.
- A question needs at least two options (multiple choice) and an
  `ANSWER:` line, otherwise it is skipped and reported in the Preview.

---

## 1. Combined format

Each question, its options (for multiple choice), and ANSWER: all
together in one block.

```
Q1. What is the powerhouse of the cell?
A. Nucleus
B. Mitochondria
C. Ribosome
D. Golgi apparatus
ANSWER: B

*
Q2. (mitochondria, nucleus, ribosome)
The powerhouse of the cell is _____.
ANSWER: mitochondria

Q3. Which gas do plants absorb for photosynthesis?
A. Oxygen
B. Nitrogen
C. Carbon dioxide
D. Hydrogen
ANSWER: C
```

### Fill-in-the-blank cards in detail

```
*
Q2. (word1, word2, word3, word4)
Stem with the blank as _____.
ANSWER: word1
```

- The lone `*` line comes first, then the numbered stem line.
- **Word bank:** if the source gives a bank of words for a set of
  blanks, print the full bank in parentheses on the line right after
  `Qn.` and **before** the stem, and repeat the same bank on every
  question of that set. If there is no bank, leave that line out.
  (It simply becomes the first line of the question text.)
- `ANSWER:` is on its own line after the stem -- never on the same line
  as `Qn.` or the stem -- and holds the filled word(s), number or
  formula. **No option letter.**
- **Write the answer the way you would type it.** Grading compares what
  the student types with the answer text, ignoring capitalization,
  extra spaces and `$` signs -- so `ANSWER: $100$` and `ANSWER: 100`
  both accept a typed `100`. But LaTeX markup a keyboard can't produce
  (`\mathrm{...}`, `^{-1}`, `\frac`) can never be matched, so give
  such answers in plain words or symbols (`mol/L`, `1/2`).
- **Several blanks in one question:** put the answers in source order,
  separated by commas (`ANSWER: hydrogen, oxygen`). The student types
  them in the same order with the same separators. When you can, write
  one blank per question instead.
- The word bank and the stem may use LaTeX freely (that is only
  displayed, never typed).

## 2. Separate answer key

Use this when your source has questions and answers in different
places (e.g. a textbook chapter and its answer key at the back).
Paste the numbered questions in one box and the answer key in a
second box -- they're matched automatically by number.

Questions box (a `*` right after the number marks a fill-in-the-blank
and it has no options):
```
1. What is the powerhouse of the cell?
A) Nucleus
B) Mitochondria
C) Ribosome
D) Golgi apparatus

2. *The powerhouse of the cell is ____.

3. Which gas do plants absorb for photosynthesis?
A) Oxygen
B) Nitrogen
C) Carbon dioxide
D) Hydrogen
```

Answer key box -- one entry per question, holding a letter for
multiple choice or free text for fill-in-the-blank, in any mix (also
accepts "1) B", "1-B", "1: B", or a plain comma-separated list
matched by position if no numbers are found):
```
1. B
2. Mitochondria
3. C
```

In this format a new question starts at any line that begins with a
number and a `.` or `)` (like `12.` or `3)`). A decimal such as `1.5 g
of ...` does not count, but avoid starting a continuation line with a
whole number and a period.

---

## Labels inside a question -- the most common failure

### Lists of statements and match-the-following

A "match the following" question naturally has its own "A) / B) / C) /
D)" labels for one column, which look identical to real MCQ options to
the parser. If pasted as-is, this fails:

```
Q4. Match Column I with Column II and mark the appropriate choice.
Column I
Column II
A) Tertiary alcohol
i) Butan-2-ol
B) Allylic alcohol
ii) 2-Methylpropan-2-ol
C) Secondary alcohol
iii) Propan-1-ol
D) Primary alcohol
iv) Prop-2-en-1-ol
A. A-ii, B-iv, C-i, D-iii
B. A-ii, B-i, C-iv, D-iii
C. A-i, B-ii, C-iii, D-iv
D. A-i, B-iv, C-iii, D-ii
ANSWER: C
```

The fix: rewrite the internal labels as Roman numerals so they can't
be mistaken for the real options, and rewrite the combination keys to
match:

```
Q4. Match Column I with Column II and mark the appropriate choice.
Column I
Column II
I) Tertiary alcohol
i) Butan-2-ol
II) Allylic alcohol
ii) 2-Methylpropan-2-ol
III) Secondary alcohol
iii) Propan-1-ol
IV) Primary alcohol
iv) Prop-2-en-1-ol
A. I-ii, II-iv, III-i, IV-iii
B. I-ii, II-i, III-iv, IV-iii
C. I-i, II-ii, III-iii, IV-iv
D. I-i, II-iv, III-iii, IV-ii
ANSWER: C
```

Conversion rules (apply to **every** labelled list inside a question,
not only match columns):

- `A`/`a` -> `I`/`i`, `B`/`b` -> `II`/`ii`, `C`/`c` -> `III`/`iii`,
  `D`/`d` -> `IV`/`iv`. Uppercase stays uppercase, lowercase stays
  lowercase.
- If the other column is already `i, ii, iii`, convert only the letter
  column (`I, II, III`) so the two columns stay different.
- Keep the same pairing in the combination options and the key (for
  example `A-ii B-i C-iii` becomes `I-ii II-i III-iii`).
- The four answer options themselves stay `A.` `B.` `C.` `D.` and the
  answer is still a letter (`ANSWER: B`). The Roman numerals apply
  **only** to labels inside the stem.
- An inline layout such as `A: (I) first, (II) second` is fine on one
  line as long as the line does not begin with `A.`, `A)` or `(A)`.

### Assertion and reason

Write the two statements with the words first, never as `(A)` / `(R)`
at the start of a line, then the usual four options:

```
Q5. Assertion (A): Water is a good solvent for ionic compounds.
Reason (R): Water molecules are polar.
A. Both A and R are true and R explains A
B. Both A and R are true but R does not explain A
C. A is true but R is false
D. A is false but R is true
ANSWER: A
```

### Steps or mechanisms

Keep step labels outside the math, one step per line, with Roman
numerals:

```
Q6. For the mechanism
(i) $\mathrm{O_3} \rightleftharpoons \mathrm{O_2} + \mathrm{O}$ (fast)
(ii) $\mathrm{O} + \mathrm{O_3} \rightarrow 2\mathrm{O_2}$ (slow)
the rate law is
A. ...
```

(`(i)` lines are safe in the stem; only a line starting with `A`/`a`
in one of the forms above is taken for an option.)

---

## LaTeX

Question text, options, answers and solutions can include LaTeX using
`$...$`, `$$...$$`, `\(...\)` or `\[...\]`. Prefer `$...$` for inline
math and `$$...$$` only for a standalone display equation.

- A formula may span several lines (a matrix, an aligned block) --
  wrap the whole thing in `$$ ... $$` and do not put a blank line
  inside it.
- Never leave a lone `$`. A literal dollar sign is `\$`.
- Wrap every formula, chemical formula and quantity every time it
  appears, in the stem, options, word bank and answers:

| Source text | Write |
|---|---|
| H2O, H2O2, NH3, KClO3 | `$\mathrm{H_2O}$`, `$\mathrm{H_2O_2}$`, `$\mathrm{NH_3}$`, `$\mathrm{KClO_3}$` |
| [A], [R0], [R]0 | `$[A]$`, `$[R_0]$`, `$[R]_0$` |
| t1/2, t½ | `$t_{1/2}$` |
| Ea, ΔH | `$E_a$`, `$\Delta H$` |
| Rate = k[A]^2[B] | `$\mathrm{Rate} = k[A]^2[B]$` |
| mol L-1 s-1, M s-1, atm S⁻¹ | `$\mathrm{mol\,L^{-1}\,s^{-1}}$`, `$\mathrm{M\,s^{-1}}$`, `$\mathrm{atm\,s^{-1}}$` |
| 10^-3, 4.606 × 10-3 S-1 | `$10^{-3}$`, `$4.606 \times 10^{-3}\,\mathrm{s^{-1}}$` |
| log, ln | `$\log$`, `$\ln$` |
| 1/2 A + 3B | `$\frac{1}{2}A + 3B$` |

- Use `\mathrm{}` for chemical formulas and units, and `\,` between a
  number and its unit and between unit parts.
- Commands that render: `\frac{}{}`, `\dfrac{}{}`, `\rightarrow`,
  `\xrightarrow{}`, `\rightleftharpoons`, `\propto`, `\times`, `\ge`,
  `\le`, `\Delta`, `\ln`, `\log`, `\mathrm{}`, `\text{}`, `^{}`, `_{}`
  (and anything else standard in KaTeX).
- Rewrite, don't copy: bare `\2NH_3` or `\Rate = K [NH_3]^0\` (stray
  backslashes, missing braces); `$d[C]/4 dt$` (write
  `$\dfrac{1}{4}\dfrac{d[C]}{dt}$`); Unicode superscripts such as `s⁻¹`
  (write `s^{-1}`, lowercase `s` for seconds); a `$` nested inside
  math.

### Tables

Write a table as a Markdown pipe table, on consecutive lines with no
blank line inside it:

```
Q7. The data for a reaction are given below.
| Trial | $[A]$ (M) | $[B]$ (M) | Initial rate ($\mathrm{M\,s^{-1}}$) |
|:--|:-:|:-:|--:|
| 1 | 0.10 | 0.10 | $2.0\times10^{-3}$ |
| 2 | 0.20 | 0.10 | $4.0\times10^{-3}$ |
| 3 | 0.20 | 0.20 | $1.6\times10^{-2}$ |
The order with respect to A is
A. 0
B. 1
C. 2
D. 3
ANSWER: B
```

- The first line is the header row, the second is the separator row
  (`|---|---|`, one cell per column; `:--` left, `:-:` centre, `--:`
  right alignment), then one line per data row. Start every row with a
  `|` so it can never be mistaken for an option.
- Name the real columns in the header (`$[A]$`, `$[B]$`, initial rate).
  Keep every cell on one line, and put math in `$...$` as usual. A
  pipe inside `$...$` (like `$|x|$`) is fine; a literal `|` in plain
  text is written `\|`.
- **Built for a phone screen.** The app fits a table to the screen
  width: columns share the space and long text wraps inside its cell.
  A table wider than the screen is scaled down, or -- when it is very
  wide -- drawn in smaller text with words broken. So keep tables to
  about 4-5 columns with short cells; for more, split it into two
  tables or list the rows as text.
- For a purely mathematical grid (a matrix, a determinant) use a
  display formula instead, e.g. `$$\begin{vmatrix} a & b \\ c & d
  \end{vmatrix}$$` (spread over lines, no blank line inside).

---

## Images and missing figures

Images can't be pasted in; add them per-question afterwards using the
Edit screen's "Add image" button. When the source refers to a figure
you can't include, replace `[Image]` with one line describing it (for
example "The plot of $[R]$ against $t$ is a straight line with a
negative slope."), and keep four normal text options.

---

## Repairing a messy source

- Reconstruct four options if the source cut off after option A: use
  the standard set for that stem.
- Keep the source's answer letter or answer text. Only change a key if
  its option is empty, duplicated or contradicts itself -- and never
  invent a new correct option and silently retarget the key.
- No marked answer at all: write `ANSWER: ?` (the app will flag that
  one question) instead of guessing.
- Only fix format and LaTeX unless asked for more: don't rewrite the
  content of a question to what you think is better.
- Don't turn fill-in-the-blanks into four made-up options.
- Don't strip option text down to bare letters.

---

## Adding more questions to an existing test

From a test's Edit screen, the + button offers "Many questions" --
the same paste box described above, with the same Preview step (how
many questions were detected, which were skipped and why) before you
tap Add, and the new questions are appended after whatever questions
the test already has. Any numbers in a separate-key paste
are only used to match questions to their answers during parsing --
the final question numbers in the test simply continue on from
wherever it left off, regardless of what numbers appeared in the
pasted text.

---

## Instructions for an AI assistant generating or cleaning questions for this app

If you are an AI reading this to produce a question set:

1. Ask (or infer from context) which format (combined or separate
   answer key) the person wants, whether they want multiple choice,
   fill-in-the-blank, or a mix, and how many questions. Default to the
   combined format.
2. Follow the exact punctuation shown -- `Q1.`, `A.` `B.` `C.` `D.`,
   `ANSWER: C`, the lone `*` line for fill-in-the-blanks, one blank
   line between questions -- since the app's parser matches these
   patterns literally.
3. Number every question. One option per line, never wrapped. Start at
   A and use consecutive letters. `ANSWER:` is the letter only.
4. Mark every fill-in-the-blank with the lone `*` line -- without it
   the question is read as multiple choice. Put the word bank (if any)
   on the line after `Qn.`, the stem after it, and a plain typeable
   answer in `ANSWER:`.
5. Inside a question, use Roman numerals for any list, match column or
   statement label; use "Assertion (A):" / "Reason (R):" wording for
   assertion-reason questions. Never begin a stem line with `A.`, `A)`,
   `(A)` or `(a)`.
6. Wrap all mathematical notation, chemical formulas and units in
   LaTeX per the section above. Write tables as Markdown pipe tables
   (header row, `|---|---|` row, data rows; about 4-5 columns at most).
   No HTML.
7. Keep the source answer key; use `ANSWER: ?` when unknown. Add no
   explanations, tags or commentary.
8. Output the result as a single plain-text block ready to paste
   directly into the app -- no title, headings, dividers or
   commentary mixed into it.
