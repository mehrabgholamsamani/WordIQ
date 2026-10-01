# WordIQ

> Finnish vocabulary practice that adapts to how you remember, not how often you tap a rating button.

WordIQ is a native Android vocabulary trainer built primarily for Persian-speaking learners of Finnish. It combines Finnish, Persian, and English vocabulary with short adaptive practice, listening, spelling, context, spoken Finnish, and important inflected forms—all in an offline-first app designed to feel calm and simple.

It is not a generic flashcard app with a spaced-repetition label. The interesting work happens beneath the practice card: WordIQ infers difficulty from correctness, response time, hints, reveals, typos, and audio replays; maintains independent memory state for each learning skill; introduces morphology gradually; and keeps manual study separate from scheduled learning so casual review cannot distort the long-term plan.

The normal path stays deliberately small:

> Open the app → tap **Practice** → answer → continue.

Users never need to understand intervals, retention probabilities, ease factors, or **Hard / Good / Easy** buttons.

## Product highlights

- Native Kotlin and Jetpack Compose interface with a restrained, custom Material 3 design system.
- Finnish → Persian, Persian → Finnish, Finnish → English, and English → Finnish study modes.
- Three-step onboarding for explanation language and a 5, 10, 15, or 20-minute daily pace.
- Independent flashcard sets plus optional collections for books, courses, or personal themes.
- One durable vocabulary identity across every set, with one shared learning history.
- Smart daily sessions sized from the time budget and observed response speed.
- Behavior-based FSRS-style scheduling without visible self-rating controls.
- Recognition, production, free recall, spelling, listening, context, morphology, and spoken-Finnish exercises.
- Progressive hints, typo-aware grading, Finnish quantity feedback, and trouble-word Rescue cards.
- Structured word forms with gradual unlocking and an inspectable Word X-Ray.
- Finnish Text-to-Speech behind a replaceable audio boundary and initialized only on first use.
- Search across Finnish lemmas, Persian and English translations, and saved inflected forms.
- Progress derived from real review history rather than XP, streak pressure, or decorative scores.
- CSV import/export and complete JSON backup/restore through Android's document picker.
- Fully local core experience with no account, server, or network dependency.

## Why this project exists

Vocabulary apps often make the learner manage the learning algorithm. They expose card states, ask for subjective ratings after every answer, duplicate the same word across decks, and treat recognizing a translation as proof that the learner can also spell, hear, or produce the word.

That model is especially weak for Finnish. Remembering a lemma does not mean recognizing an inflected form. Understanding written Finnish does not imply understanding speech. A Persian-speaking learner also needs mixed-direction text to remain readable without the Latin and RTL content interfering with each other.

WordIQ is a focused answer to those problems:

- How can scheduling adapt without making the learner operate the scheduler?
- How can one word belong to several sets without fragmenting its memory history?
- How can recognition, recall, spelling, listening, context, and morphology improve independently?
- How can Finnish forms be taught progressively without turning practice into a grammar textbook?
- How can Persian remain properly RTL inside an otherwise LTR application?
- How can users own and move all of their learning data without creating an account?

The product keeps those decisions in the learning and data layers, leaving the screen focused on the next useful retrieval.

## Demo workflow

1. Complete onboarding and choose Persian, English, or both as explanation languages.
2. Create an independent set or group several sets inside a collection.
3. Add a Finnish lemma, translations, notes, spoken Finnish, an example, and any important forms.
4. Tap **Practice** from Home for a session built around the daily time budget.
5. Answer recognition, production, listening, spelling, context, and form exercises as they become useful.
6. Use a hint, replay audio, make a typo, or reveal an answer; WordIQ records the behavior and infers an internal rating.
7. Open **Progress** to see learning, strong, weak, skill-evidence, and weekly-consistency measures calculated from actual review history.
8. Search the library by Finnish, Persian, English, or an inflected form.
9. Export a CSV for editing or create a complete JSON backup before moving to another device.

## Architecture at a glance

```text
                                      Jetpack Compose UI
                         onboarding / library / practice / progress
                                                │
                                       ViewModel + Flow
                                                │
                ┌───────────────────────────────┼───────────────────────────────┐
                │                               │                               │
                ▼                               ▼                               ▼
         WordRepository                 PracticeRepository          UserPreferencesRepository
                │                               │                               │
                │                    ┌──────────┼──────────┐                    │
                │                    │          │          │                    │
                │                    ▼          ▼          ▼                    │
                │               queue builder  grader  scheduler                │
                │                    │          │          │                    │
                └────────────────────┴──────────┴──────────┘                    │
                                     │                                          │
                                     ▼                                          ▼
                              Room / SQLite                            Preferences DataStore
                                     │
                       ┌─────────────┴─────────────┐
                       ▼                           ▼
                CSV interchange             JSON backup/restore

          PronunciationPlayer ──────── Android Finnish Text-to-Speech
          FinnishMorphologyService ─── trusted, manually structured forms
          RescueContentProvider ────── local learner-owned vocabulary content
```

WordIQ is a single-module Android application with explicit boundaries around scheduling, morphology, audio, Rescue content, persistence, and portability. Those boundaries keep the current implementation understandable while allowing a future scheduler, morphology analyzer, recorded-audio provider, or Rescue provider to be introduced without rewriting the practice UI.

| Area | Implementation |
| --- | --- |
| Interface | Kotlin, Jetpack Compose, custom Material 3 foundations, Navigation Compose |
| State | ViewModel, Kotlin coroutines, `Flow`, lifecycle-aware collection |
| Vocabulary | Room entities, relations, DAO queries, repositories, explicit migrations |
| Preferences | Preferences DataStore |
| Learning | FSRS-style scheduler, behavior-to-rating mapper, adaptive queue, per-skill state |
| Language | Unicode-aware grading, Persian RTL isolation, Finnish morphology and quantity checks |
| Audio | Android Text-to-Speech through `PronunciationPlayer` |
| Portability | RFC 4180-style CSV plus complete versioned JSON backup |
| Quality | JVM tests, Room/emulator tests, Android lint, debug and release builds |

## Engineering decisions that matter

### The learner answers; the application rates

WordIQ needs an internal result similar to Again, Hard, Good, or Easy, but it does not ask the learner to choose one. `BehaviorRatingMapper` derives that signal from observable evidence:

- a wrong or revealed answer becomes **Again**;
- a partial answer or heavy hint use becomes **Hard**;
- a small typo, one hint, or a normal correct answer becomes **Good**;
- a fast exact answer becomes **Easy** only after earlier clean recalls provide enough evidence.

The latency expectation changes with the exercise. Selecting a meaning should be faster than typing Finnish or completing free recall. Audio replay also matters: the first play is free, one replay prevents an Easy result, and repeated replay can lower confidence in an otherwise correct listening answer.

This keeps the interaction honest. The learner performs the skill; the application interprets the evidence.

### One word has one identity

`VocabularyConcept` is the lexical identity. `SetMembership` is a many-to-many link between a concept and a flashcard set, so adding the same Finnish word to several sets does not duplicate its translations, forms, examples, or learning history.

```text
Collection ── contains ──> FlashcardSet
                                │
                                ▼
                         SetMembership
                                │
                                ▼
VocabularyConcept ── owns ──> forms / examples / learning states / review logs
```

Collections are optional. A set such as `Work Finnish` can stand alone, while course material can be organized as `Suomen mestari 3 → Chapter 1, Chapter 2, ...`.

### Memory is tracked per skill

Knowing a translation is not the same as being able to produce, spell, hear, or inflect the word. `LearningState` therefore uses `(conceptId, skill)` as its key and tracks meaning recognition, Finnish production, spelling, listening, context, morphology, and spoken-Finnish recognition independently.

The adaptive queue considers due time, failed recalls, lapses, hints, replays, unstarted skills, and the most recent exercise. It can emphasize production when production is weak, typing when spelling is weak, or audio when listening needs work.

### Short sessions are a real constraint

The selected 5–20 minute daily pace is not decorative. The queue estimates session capacity from the learner's average successful response time, prioritizes due work, limits new material when review debt is high, and fills remaining capacity with useful skill expansion.

New words start with a compact progression across introduction, recognition, production, listening, spelling, and context. Under a heavy review load, the new-word allowance can fall to zero rather than allowing overdue work to accumulate invisibly.

### Manual study cannot corrupt scheduling

Scheduled practice updates stability, difficulty, next review, recall counts, and lapses. Manual study still writes a `ReviewLog` for audit and later analysis, but uses `scheduled=false` and leaves long-term scheduling state unchanged.

This separation makes it safe to browse a set, rehearse forms, or run a focused study mode without accidentally convincing the scheduler that the material was independently recalled at the right time.

### Finnish morphology is explicit and gradual

WordIQ stores trusted structured forms rather than inventing grammar. A `WordForm` can contain the surface form, grammatical label, English and Persian meaning, decomposition, a compact Persian contrast note, and learner-controlled order.

Practice initially unlocks one form and exposes another after every two successful morphology recalls. Exercises cover form-to-lemma recognition, contextual meaning, typed form production, and inflected-form completion inside a stored sentence.

If the expected answer is `kaupasta` and the learner enters `kauppa`, the application can distinguish remembering the lemma from producing the requested form. Lexical production receives positive evidence, morphology receives a failure signal, and the review log records that the lemma was remembered.

### Finnish quantity errors receive useful feedback

The grader detects a single doubled-vowel or doubled-consonant contrast when the remaining letter sequence matches. Errors such as `tuli` / `tuuli` and `kaupasa` / `kaupassa` remain recoverable typos, but feedback identifies the long vowel or double consonant instead of reducing the result to a generic wrong answer.

A separate sound-pair service supports listening contrasts when a stored minimal pair is available.

### Trouble words receive Rescue, not punishment

`TroubleWordDetector` identifies repeated failure in one skill or failure accumulated across skills. Before the due exercise, WordIQ can insert a Rescue card assembled from trusted local material: translations, an example, spelling chunks, notes, an important form, pronunciation, and a nearby comparison word.

The Rescue card does not modify scheduling by itself. It provides support, then lets the following retrieval produce the evidence.

### Progress reports learning evidence

WordIQ avoids fake XP and opaque mastery percentages. Progress is computed from Room learning states and scheduled review logs:

| Measure | Definition |
| --- | --- |
| Learning | Reviewed concepts that have not reached the strong threshold |
| Strong | Recognition and production both have successful evidence and mean stability is at least 14 days |
| Weak | Any skill has at least two failed recalls or two lapses |
| Consistency | Distinct days with scheduled practice during the last seven days |
| Set readiness | Evidence derived from the set's primary recognition and production states |

### Persian stays RTL without turning the whole screen around

Persian content is rendered in isolated RTL containers while Finnish, English, controls, and navigation retain their expected direction. The same separation is used in editors, cards, word details, morphology explanations, and scalable text layouts.

### The user owns the data

WordIQ stores core data locally in Room and preferences in DataStore. It requires no account and no server for learning. Android's system document picker handles import, export, backup, and restore, so the user chooses where files are read and written.

CSV is the human-editable interchange format. JSON is the lossless WordIQ backup format.

## Practice flow

```mermaid
flowchart LR
  Start[Daily time budget] --> Load[Load concepts and per-skill states]
  Load --> Due[Prioritize due and weak skills]
  Due --> Capacity[Estimate capacity from response speed]
  Capacity --> Queue[Build adaptive exercise queue]
  Queue --> Prompt[Show one focused prompt]
  Prompt --> Evidence[Collect correctness, latency, hints, reveal, replays]
  Evidence --> Rating[Infer internal rating]
  Rating --> Schedule[Update only the practiced skill]
  Schedule --> Log[(Persist review log and learning state)]
  Log --> Next[Continue or finish]
```

## Data portability

### CSV interchange

CSV accepts reordered columns, quoted commas, quotes, CRLF, and multiline values. `Finnish` plus either `Persian` or `English` is required for each usable row.

Supported columns:

```text
Finnish, Persian, English, Set, Collection, Example,
Part of speech, Notes, Spoken Finnish,
Meaning stability, Production stability, Listening stability,
Last review, Next review
```

Blank set names are imported into an independent `Imported` set. Repeated normalized Finnish lemmas merge into one concept and may create several memberships rather than duplicate learning history. Imports reject empty files, missing Finnish headers, malformed quoted fields, and files larger than 10 MiB.

### JSON backup

The versioned JSON backup includes collections, sets, vocabulary concepts, forms, examples, memberships, learning states, and review logs. Restore reads at most 25 MiB, validates and parses the complete backup before mutation, asks for confirmation, and replaces the Room-owned dataset in one transaction.

Resetting progress is a separate confirmed operation. It clears review logs and scheduler state while preserving the vocabulary library.

## Repository layout

```text
app/
  schemas/                  exported Room schema history
  src/main/java/com/wordiq/app/
    audio/                  replaceable pronunciation boundary and Android TTS
    data/                   repositories, preferences, and portability
    data/local/             Room entities, relations, DAO, and migrations
    data/portability/       CSV parsing and encoding
    learning/               scheduling, grading, adaptive queues, morphology, Rescue
    ui/components/          shared Compose components and motion behavior
    ui/screens/             onboarding, library, practice, progress, word and set flows
    ui/theme/               color, type, shape, and spacing system
  src/test/                 JVM behavior tests
  src/androidTest/          Room, migration, and persistence tests
docs/                       stage-by-stage completion and verification notes
gradle/                     Gradle wrapper and daemon configuration
```

## Run it locally

### Prerequisites

- A current Android Studio release
- JDK 17 or the JDK bundled with Android Studio
- Android SDK 37
- An Android 7.0+ device or emulator (`minSdk 24`)

### Android Studio

1. Clone the repository.
2. Open the repository root in Android Studio.
3. Allow Gradle sync to finish and install SDK 37 if prompted.
4. Start an emulator or connect a USB-debugging-enabled Android phone.
5. Select the `app` run configuration and target device.
6. Click **Run**.

All vocabulary, search, scheduling, import, export, and backup features work offline. Pronunciation uses an installed Finnish (`fi-FI`) Text-to-Speech voice and reports clearly when one is unavailable.

### Command line

From PowerShell in the repository root:

```powershell
.\gradlew.bat assembleDebug
```

The installable debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

The release task produces an unsigned artifact because the repository intentionally contains no private signing configuration:

```powershell
.\gradlew.bat assembleRelease
```

Configure a protected signing setup before distributing a release build. Never commit a keystore, signing password, or generated `local.properties` file.

## Verification

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat connectedDebugAndroidTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

The final Stage 5 handoff recorded:

- 31 passing JVM tests;
- 19 passing Android/Room emulator tests;
- debug lint with zero issues;
- successful debug and unsigned release assembly;
- successful debug APK installation on an API 37 emulator;
- a successful cold launch with no Android runtime crash.

Tests focus on behavior: scheduling, rating inference, queue capacity, typo and quantity grading, morphology, Rescue selection, replay effects, multilingual CSV round-trips, word validation, progress derivation, Room relationships, scope selection, persistence, non-destructive manual study, database migrations, and progress reset.

## Privacy and reliability posture

- Core learning data remains on the device in Room and DataStore.
- No account, analytics service, advertising SDK, or application server is required.
- TTS is initialized lazily and released with the application composition.
- Database migrations from versions 1 through 4 are explicit and covered by emulator tests.
- Backup restore validates the complete payload before changing the database and performs replacement transactionally.
- Destructive restore and progress-reset actions require confirmation.
- Search is debounced and limited at the database boundary.
- Lists use lazy rendering and stable keys where identity is available.
- Database work and document I/O run away from the main thread.
- Persian content uses dedicated RTL layout rather than relying on accidental bidirectional rendering.

Android platform backup remains enabled. The explicit JSON export provides an additional portable copy controlled by the user.

## Tradeoffs and what I would do next

| Decision | Why | Tradeoff / next step |
| --- | --- | --- |
| Manual structured morphology | Accurate, inspectable, offline, and learner-controlled | Add an optional deterministic analyzer such as Omorfi behind the existing interface |
| Android TTS | Small, local, and available without bundling audio | Add recorded native audio through `PronunciationPlayer` where quality matters |
| FSRS-style implementation | Behavior-driven and isolated behind a scheduler contract | Calibrate and version weights against longer real-world learning histories |
| Single-device local data | Private, fast, and account-free | Add optional end-to-end encrypted sync without weakening offline ownership |
| CSV plus JSON | Human-editable interchange and complete lossless backup | Add a preview/mapping screen for large or unfamiliar imports |
| One Android module | Easy to navigate at the current size | Split feature or core modules only when build time or ownership justifies it |
| No automatic form generation | Prevents plausible but incorrect Finnish data | Offer reviewed dictionaries or deterministic linguistic tooling as opt-in sources |

## Known limitations

- Word forms, spoken Finnish, and example content are learner-entered or imported; the app does not claim automatic linguistic correctness.
- Finnish pronunciation depends on a suitable TTS voice installed on the Android device.
- There is no cloud synchronization, web client, account system, or shared classroom library.
- The scheduler is FSRS-style rather than a drop-in implementation of a specific published FSRS release.
- Light mode is the polished target; a complete custom dark theme is not yet part of the product scope.
- The release build is unsigned until a private signing configuration is supplied outside the repository.
- Verification records cover the tested API 37 emulator configuration, not the full Android device ecosystem.

## License

WordIQ is available under the [MIT License](LICENSE).
