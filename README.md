<p align="center">
  <img src="app/src/main/res/drawable-nodpi/app_logo.png" alt="Testmond logo" width="160">
</p>

<h1 align="center">Testmond</h1>

<p align="center">
  <a href="https://github.com/karthikhegde25/Testmond/releases/download/1.0/Testmond.apk">
    <img src="get-it-on-github.png" alt="Get it on GitHub" height="80">
  </a>
</p>

<p align="center">
  Version 1.0 &middot; Android 7.0+ &middot; Free and open source (GPL-3.0)
</p>

Native Android app (Kotlin + Jetpack Compose) for pasting question sets,
saving them as local test files, and quizzing yourself with auto-grading --
including timed exams. Multiple-choice and fill-in-the-blank questions can be
mixed freely in one test, with optional images, LaTeX and per-question
solutions. Open source, no server, no network access -- everything lives
on-device.

## Download

Tap the **Get it on GitHub** button at the top of this page, or use this
direct link: <https://github.com/karthikhegde25/Testmond/releases/download/1.0/Testmond.apk>
-- all releases are on the [Releases page](https://github.com/karthikhegde25/Testmond/releases).

To install:

1. Download the APK on your Android phone (or on a computer, then copy it
   over).
2. Open the downloaded `Testmond.apk`. If Android asks, allow installs from
   your browser or file manager ("Install unknown apps") -- this is normal for an
   app that isn't installed from the Play Store.
3. Tap **Install**, then open **Testmond**.

It needs Android 7.0 (API 24) or newer, asks for no permissions and has no
internet access -- your tests never leave the phone.

## Website

`index.html` at the root of the repository is the project's landing page (features, download link and licenses). To publish it, open **Settings -> Pages**, choose
**Deploy from a branch**, pick `main` and `/ (root)`, and save; it is then served at
`https://karthikhegde25.github.io/Testmond/`. It is a single self-contained file (its logo and
badge are embedded), so it needs nothing else from the repository.

## What it does

- **Create tests** by pasting questions (multiple choice and fill-in-the-blank,
  mixed freely), with images, LaTeX and Markdown tables; organise them in
  **folders**; **share** a test or a whole folder as one file and import it on
  another phone (with a preview of what's inside before anything is saved).
- **Take tests** with Quiz or Practice mode, saved progress and attempt history,
  and a review where you can tap any question to see it in full with its
  solution.
- **Exam Mode**: timed exams with a countdown, answers that stay editable until you
  finish, no answers revealed while it runs, a saved review of every attempt and
  optional **custom marking** (for example +4 / -1 / 0).
- **Solutions** with text and images, **image preview** with pinch-to-zoom, an
  offline math renderer (KaTeX), and a downloadable guide you can hand to an AI
  assistant to generate questions in the right format.

## Building

No wrapper jar is checked in (offline environment). To build:

1. Open the project folder in Android Studio (it will offer to generate
   the Gradle wrapper automatically), **or**
2. Run `gradle wrapper --gradle-version 8.7` once from the project root,
   then `./gradlew :app:assembleDebug`, **or**
3. Push to GitHub -- `.github/workflows/build.yml` builds a debug APK via
   Actions and uploads it as a workflow artifact.

## The `.mcqz` file format

Plain JSON (currently `"version": 2`), so it's easy to inspect or hand-edit.
Version 1 files (multiple choice only, no `type` field) still load -- a
missing `type` means multiple choice. `imageBase64`, `solution` and
`solutionImageBase64` (an image attached to the solution) are optional.

```json
{
  "version": 2,
  "title": "Biology Chapter 4",
  "questions": [
    {
      "question": "What is the powerhouse of the cell?",
      "options": [
        { "letter": "A", "text": "Nucleus" },
        { "letter": "B", "text": "Mitochondria" }
      ],
      "answer": "B",
      "solution": "Mitochondria produce most of the cell's ATP."
    },
    {
      "type": "FILL_BLANK",
      "question": "Water boils at ____ degrees Celsius.",
      "answer": "100"
    }
  ]
}
```

Registered as a `VIEW` intent filter for `*.mcqz` and `*.mcqzf`, so tapping a
file from Drive, WhatsApp, email, or a file manager offers to open it in this
app directly -- that's how a friend imports a test you've shared. A folder is
exported as a single `.mcqzf` file.

## Pasting questions

Multiple choice and fill-in-the-blank questions can be mixed in the same
paste; there is no per-test type. Anything pasted normally is multiple
choice. To mark a question as fill-in-the-blank:

- **Combined format:** put a lone `*` on its own line right before it.
- **Separate-key format:** put `*` right after the question's number, before
  the question text, with no options following.

### Combined format

Each question, its options (for multiple choice) and `ANSWER:` together,
blocks separated by a blank line:

```
Q: What is the powerhouse of the cell?
A) Nucleus
B) Mitochondria
C) Ribosome
D) Golgi apparatus
ANSWER: B

*
Q: The powerhouse of the cell is ____.
ANSWER: Mitochondria
```

### Separate answer key

For when your source has questions and answers in different places (e.g. a
textbook's end-of-chapter key). Numbered questions go in one box and the
answer key in a second box; they are matched by number (or by position if no
numbers are found in the key).

Questions box:
```
1. What is the powerhouse of the cell?
A) Nucleus
B) Mitochondria
C) Ribosome
D) Golgi apparatus

2. *The powerhouse of the cell is ____.
```

Answer key box (also accepts `1) B`, `1-B`, `1: B`, or a plain
comma-separated list like `B, C`):
```
1. B
2. Mitochondria
```

Fill-in-the-blank grading ignores capitalisation, leading/trailing spaces,
runs of spaces and LaTeX `$` signs, so "mitochondria" and "Mitochondria " both
count as correct, and an answer key written as `$100$` accepts a typed `100`.

### Parsing rules worth knowing

- Number the questions (`Q1.`, `Q2.`, ...); a bare `Q:` also works. Only a real
  label is stripped from the first line -- a stem such as "Quantum numbers of
  ..." or "100 mL of ..." or "Q: 2 moles of ..." keeps its first word or
  quantity (previously the leading "Q", or the number, was eaten), and a decimal
  like `1.5 g` never starts a new numbered question.
- Options may be written `A)`, `A.`, `(A)` or in lowercase; they must run on
  from A, one per line. After the options start, any line that is not the next
  option or `ANSWER:` is ignored, so an option must not wrap onto a second line.
- Pasting a whole Markdown file is safe: standalone `# heading` lines, `---` /
  `***` rules and `<!-- comment -->` lines are dropped before parsing.
- In a stem, never begin a line with `A.`, `A)`, `(A)` or `(a)` -- it is taken
  for the first option. Use Roman numerals for lists and match columns, and
  "Assertion (A): ..." / "Reason (R): ..." wording.
- `ANSWER:` for multiple choice is the letter only; an unknown one (`ANSWER: ?`)
  skips that question and reports it in the Preview.

- A question can span multiple lines before its first option (or its
  `ANSWER:` line). Everything up to that point is joined as the question
  text, with line breaks -- including an intentional blank line for a
  paragraph break -- preserved exactly as typed.
- For multiple choice, a line is only treated as an option once a genuine
  `A)` is found, so a Roman-numeral statement list inside a question
  ("I. Current flows...", "II. Anode is...") is kept as question text
  instead of being mistaken for an option. The first option must be `A)`.
- Match-the-following questions have their own `A) / B) / C) / D)` column
  labels, which look like real options; rewrite those internal labels as
  Roman numerals.
- Creating a test is two steps: **Preview** lists how many questions were
  detected and exactly which were skipped and why, then **Create test**
  saves it.

The in-app *How to use* screen has a downloadable format guide
(`app/src/main/assets/format_guide.md`) that can be handed to an AI assistant
to generate or clean up pasteable questions. It covers numbering, option and
`ANSWER:` rules, fill-in-the-blank cards with word banks, Roman-numeral labels
for in-stem lists, LaTeX conventions, Markdown tables and how missing figures or
answers are handled.

## LaTeX support

Question text, options, fill-in-the-blank answers and solutions can include
LaTeX using `$...$`, `$$...$$`, `\(...\)` or `\[...\]`. Plain text renders
instantly as normal; text containing a math delimiter renders through KaTeX.

Formulas may span several lines (a `\begin{vmatrix} ... \\ ... \end{vmatrix}`
matrix, `aligned` blocks, anything pasted with line breaks). The app splits the
text into plain and math parts itself (`toMathHtml` in `MathText.kt`) and
renders each math part with `katex.render`, rather than using KaTeX's
auto-render add-on: auto-render only matches a delimiter pair inside a single
text node, so the `<br>` inserted for each line break used to cut a multi-line
formula in pieces and it was shown as raw text. A display formula wider than the
screen is scaled down to fit (the WebView can't be scrolled sideways).
Tall parts of a formula (fractions, superscripts on big symbols, display-math
margins) can extend past the first line; the page measures everything it drew
(`tmHeight()` in `MathText.kt`), pushes the content down by whatever sticks out
above the top edge, and reports the height to Kotlin whenever it changes, so
the question / option box always grows to fit and nothing is clipped. `\$`
gives a literal dollar sign, and an unmatched `$` stays plain text.

KaTeX is fully bundled offline in `app/src/main/assets/katex/`
(`katex.min.js`, `katex.min.css` and all font families; the unused
`contrib/auto-render.min.js` is still shipped). No network access is needed anywhere in the app -- the `INTERNET`
permission isn't even declared in the manifest. `KATEX_LICENSE.txt` (MIT,
Khan Academy and contributors) is bundled alongside per KaTeX's license.

## Tables

A Markdown pipe table in question text (or a solution) is drawn as a real table
that fits the screen -- the app can't scroll sideways:

```
| Trial | $[A]$ (M) | Initial rate |
|:--|:-:|--:|
| 1 | 0.10 | $2.0\times10^{-3}$ |
| 2 | 0.20 | $4.0\times10^{-3}$ |
```

- A header line, then a `|---|---|` separator line (`:--` left, `:-:` centre,
  `--:` right), then the rows; the table ends at a blank line or a line without
  a pipe. A pipe inside `$...$` math is not a column break; write a literal one
  as `\|`.
- Columns share the width and long text wraps inside its cell. If unbreakable
  content still makes the table wider than the screen it is scaled down; when
  that would make the text too small (below about 65%) it switches to tighter
  text with words broken inside cells instead. So about 4-5 columns with short
  cells work best.
- Implemented in `MathText.kt` (`extractTables` / `renderTable` build the HTML,
  the page script fits it); text containing a table always goes through the
  WebView even if it has no math.

## Home screen

Three tabs -- swipe left/right or tap the labels:

- **Tests** -- every test you've created or imported. In-progress tests float
  to the top. The + button offers Import or Create test.
- **Folders** -- optional groupings of tests. A folder only references
  existing tests, so deleting a folder never deletes the tests inside it.
  The + button offers Import or Create folder.
- **Exam Mode** -- timed exams; see below. The + button adds tests to it.

Tests have a three-dot menu (Share / Rename / Edit question / Add or Edit
solution / Attempt history / Delete); folders have Share / Rename / Delete.
Deleting always asks for confirmation. Inside a folder, each test has the
same menu as in the Tests tab; long-press a test to multi-select and remove
tests from the folder (the tests themselves are untouched). Search (top bar)
looks across tests and folders.

Deleting a test also removes it from every folder that held it, and a folder's
"N tests" count only counts tests that still exist (a folder that still names a
test deleted some other way simply skips it).

Exporting a folder produces one portable `.mcqzf` file holding the folder name
and the full content of every test in it. Importing it recreates the folder
and saves each test into the Tests tab too. "Import" and the file-open intent
auto-detect which kind of file they were given.

**Importing asks first.** After a file is picked (or opened from another app),
nothing is saved yet: a confirmation dialog shows how many questions the test
has, or -- for a folder -- how many tests it contains, the total number of
questions, and each test's own question count. **Import** saves it; Cancel
discards it. Tests with no questions can't be opened, so a lone empty test
can't be imported and an empty test inside a folder is skipped (the dialog
says so).

## Taking a test

- A row of question numbers at the top jumps to any question.
- A small stopwatch on the right of the "Question X of Y" line times each question. It
  starts at zero when the question opens, stops the moment you press Submit and stays
  on screen as that question's time. Leave an unsubmitted question and come back and it
  simply starts again from zero. It only counts while Testmond is in the foreground, and
  the times of submitted questions are saved with your progress. In the review, an
  opened question shows "Time taken" beside its number. (Exams have their own
  countdown and don't use it.)
- Selecting an option (or typing a fill-in-the-blank answer) only stages a
  draft; the Submit button between the prev/next arrows locks it in.
- **Quiz mode** (Settings) reveals correct/incorrect the moment you submit;
  **Practice mode** defers all of that to the review after Finish. Either way
  the review shows Correct/Wrong/Skipped counts and a list of every question.
  **Tap a question** to open it in full: the question and image, every option
  with the correct one and your own pick highlighted, and its solution (text
  and image) below when one is attached. Arrows at the bottom step through the
  other questions; back returns to the list.
- A question's solution -- its text and, if attached, its image below -- appears
  right after you submit it (and on the review page).
- Images (on a question or in a solution) carry a small magnifier badge on
  their bottom-right corner. Tapping it, or the image, opens a full-screen
  preview (`ImagePreviewDialog`): pinch to zoom up to 6x, drag to pan, double-tap
  to zoom in or out, and an X in the top-right corner closes it. It works
  wherever an image is shown -- while taking a test, in reviews (including Exam
  Mode) and in the editors.
- Leaving mid-test asks for confirmation and saves your progress; reopening
  the test resumes at the exact question, including an unsubmitted draft.
- Reset (top bar) clears all answers after confirmation. Finish asks for
  confirmation and warns if questions are unanswered.

## Exam Mode

The third home tab, for timed exams.

1. Tap **+** (bottom-right) to add tests you already have. They stay in the
   Tests tab too.
2. Tap a test, **set the time** in minutes (1-600), then confirm **Start**.
   The clock starts only once Start is tapped.
3. The test looks like a normal one, but the **countdown timer replaces the
   test name** in the top bar (turning red in the last minute). When it
   reaches zero the exam is **submitted automatically**; Finish submits early.
4. **There is no Submit button.** Tap an option (or type a fill-in-the-blank
   answer) and it is recorded straight away; change it as often as you like
   until you finish. Tap the picked option again -- or empty the box -- to
   clear an answer. The number strip marks which questions have an answer.
5. Correct answers are **never shown during an exam** -- no colours, no
   solutions. There is no Reset, and no resume: pressing back asks whether
   you want to leave (progress will be lost) and nothing is saved if you do.
6. When it ends, the result loads with the score, timing and the review list
   (right / wrong / skipped); tap any question to open it in full with the
   correct answer and its solution below, exactly as in a normal test's review.
   The attempt is saved.

Each test in the tab has a three-dot menu:

- **Review** -- every exam ever taken on that test, newest first. Tap one to
  reopen exactly which questions were right and wrong, at any time, and tap a
  question to open it with its solution. Each
  attempt keeps a text snapshot of its questions, so the review stays correct
  even if the test is edited afterwards (images are borrowed from the live
  test when the question is unchanged).
- **Custom rules** -- three boxes for the marks per **correct**, **wrong** and
  **skipped** question, e.g. `4`, `-1`, `0` (negative and decimal values are
  fine; a blank box counts as 0). The review then shows your score as marks --
  `47 / 80` for 13 correct, 5 wrong and 2 skipped -- in the exam result, in the
  Review history list and on each attempt, together with a "Marking: +4
  correct · -1 wrong · 0 skipped" line. Scores are calculated when a review is
  displayed, so changing the rules re-scores every past attempt of that test.
  Saving with all three boxes blank or 0 clears the rules and the score goes
  back to the normal "correct / total". The maximum is what an all-correct
  exam would earn (shown only when correct answers score positive marks).
- **Remove** -- takes the test out of the Exam Mode tab, after confirmation.
  The test itself and its exam history are kept, and reappear if it's added
  back. Deleting the test from the Tests tab removes its exam history too.

The running exam is held in memory (`ExamSessionHolder`), so a screen
rotation doesn't reset it; the countdown is anchored to a fixed end time.

## Editing a test

**Edit question** opens a screen with question numbers down the left; tap one
to load it into the editor. Question text, options (checkmark marks the
correct one) and the answer (plain text for fill-in-the-blank) are separately
editable, and **Save** in the top-right corner writes everything back.

- **Add image** (under the question text) embeds a photo in the test file,
  downscaled and compressed automatically. It shows wherever the question
  appears, including reviews.
- **+** at the bottom of the number column offers: one multiple-choice
  question, one fill-in-the-blank question, or **Many questions** (the same
  paste box and two-step flow as creating a test: **Preview** shows how many
  questions were detected and which were skipped and why, then **Add N questions**
  appends them after the existing ones; **Edit before adding** goes back to the
  text, and changing the text discards the preview).
- Long-press and drag a number to reorder. Its label deliberately doesn't
  renumber until you save and reopen Edit.
- The trash icon next to "Question X of Y" deletes the current question
  (confirmation first; disabled for the last one). Saved in-progress answers
  are carried over correctly through deletes, reorders and additions.

## Solutions

Each question can optionally carry a solution/explanation, stored inside the
question itself so it travels with shared tests. **Add solution** (shown when a
test has none) opens one paste box with a solution per question, identified by
number (`1.` or `Q1.`). Once any exist, the menu item becomes **Edit
solution**: a list of questions that have one, a + to add one for another
question, a delete for the selected one, Save, and a remove-all icon
(confirmation first). Each solution can also have an **image attached**
("Attach image" under its text, an x to remove it); it is downscaled and
embedded in the test file like question images and shown below the solution
text. Solutions support LaTeX like everything else.

## Settings and other screens

- Appearance: System default, Light or Dark.
- Question mode: Quiz or Practice (applies to normal tests; exams always
  hide answers).
- *How to use* and *About* are in the home screen's three-dot menu.

## App icon

The launcher icon is an adaptive icon (black background + the crystal logo).
Launchers mask adaptive icons to a shape and only guarantee the centre ~66% is
visible, so the foreground and monochrome layers are inset by 20% in
`mipmap-anydpi-v26/ic_launcher*.xml` -- the whole logo stays inside the safe zone
instead of having its edges cropped. The large logo PNGs live in `drawable-nodpi`
so Android doesn't scale them up by screen density. The same artwork is shown
on the About screen (`drawable-nodpi/app_logo.png`).

## Where data lives

Everything is under the app's private storage (`filesDir`):

| Path | Contents |
|------|----------|
| `mcq_sets/*.mcqz` | the tests |
| `folders/*.tfolder` | folder groupings (test filenames) |
| `progress/` | in-progress state of normal tests |
| `attempts/` | normal attempt history (date + score) |
| `exam_mode.json` | which tests are in the Exam Mode tab |
| `exam_attempts/` | exam history, one file per test |
| `exam_rules.json` | custom marking rules for the tests that have them |

The About screen lists KaTeX under "Open source licenses" (MIT, with the bundled
license text viewable offline). Settings are in SharedPreferences (`testmond_settings`; values saved under the
older `testanium_settings` name are migrated automatically on first launch).

## Implementation notes

- The math WebView disables scroll anchoring (`overflow-anchor: none`) and
  resets its scroll to 0 after each measurement: while KaTeX grew the content
  above the visible line the browser used to scroll the page to keep its place,
  leaving the top of the question cut off.
- The three home lists (Tests, Folders, Exam Mode) stay smooth under fast scrolling
  because their cards never read files: attempts, "in progress", whether a test has
  solutions, folder contents, exam history and rules are loaded on a background
  thread (`HomeData.kt`) into an in-memory map, which is also kept in
  `HomeCardCache` so the lists appear instantly when you come back to the home
  screen and then refresh. Previously each card parsed its files on the main
  thread as it scrolled into view (the Tests tab even parsed the whole test,
  images included, to see whether it has solutions). The lists also use stable
  item keys, and each tab's scroll position is kept when you swipe between tabs
  or return from another screen.
- Navigation taps go through `safeNavigate` / `safePopBackStack` (`NavExt.kt`).
  `safeNavigate` ignores a tap while the current screen is still transitioning,
  so a quick double tap on a test no longer opens the screen twice.
- Back arrows / Done buttons go through `NavHostController.safePopBackStack()`
  (`NavExt.kt`), which ignores a tap while the screen is already animating away
  and never pops the start destination. Plain `popBackStack()` popped one more
  screen per extra tap, so mashing the back arrow emptied the back stack and left
  a black screen.

- MCQ options are drawn with a click-detecting overlay on top of their
  content rather than a button whose child could be a math WebView -- a
  WebView's own touch handling can otherwise swallow taps meant for the
  option underneath.
- KaTeX-rendered options are sized to the space actually remaining beside the
  option letter, and long unbroken words wrap instead of being clipped.
- Test names use only a minimal filename filter (path separators and
  filesystem-reserved characters), so any language or symbol works in titles.
