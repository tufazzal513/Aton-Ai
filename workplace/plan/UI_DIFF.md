# UI_DIFF — reference vs current AtonAI

Date: 2026-11-27. The comparison below is evidence-scoped: original image observations come from `index.tsv`; current app structures from source and the three AtonAI capture filenames. There is no same-state device screenshot pair, so visual equivalence is not claimed.

## Implemented in this batch — ঠিক করা হয়েছে, যাচাই বাকি

| Screen | Target evidence | AtonAI source change | Remaining validation |
|---|---|---|---|
| Chat first run | `index.tsv` #6: welcome, suggestions, bottom composer | `ChatScreen.kt`: added three outlined suggestion actions that populate the input | Screenshot on device; compare suggestion count, text wrap and composer placement |
| Chat header | #47: model and Build/Plan selectors | `ChatScreen.kt`: added dropdowns for current session models and Build/Plan; `AppViewModel.kt`: exposes persisted session agent and persists selection | Verify catalog picker, model selection persistence after session reopen, narrow-screen layout |
| Palette documentation | Image samples use broad clusters; exact primary blue remains UNSURE | Replaced stale source-extraction/guessed palette document with screenshot-only evidence classification in `DESIGN_TOKENS.md` | Exact widget-level sampling and paired screenshot comparison remain |

## Known differences / not implemented in this batch

- Composer still has a single message field and send/stop control; attachment actions and token/cost footer are not wired into this UI.
- Header retains session title plus new-session/settings/more actions; target arrangement/geometry needs direct comparison.
- Provider selector is not included in the new header strip; model list availability depends on selected provider/session state.
- Empty-chat logo and exact target copy are not implemented; current welcome copy remains AtonAI-specific.
- Sessions, Settings, permission setup/runtime sheet, pickers, and remaining settings families have not been changed in this batch.
- Exact blue primary, light-mode role colors, warning/success/diff colors, dimensions, typography and motion remain partly UNSURE as recorded in `DESIGN_TOKENS.md`.

## Needed AtonAI captures

Current folder has 3 screenshots (see `MISSING_ATONAI_SCREENSHOTS.md`). For this Chat batch, capture: (1) empty Chat with model/Build selector visible, (2) model picker open and selected model, (3) Plan selected and then a session reopened, (4) suggestion tapped with composer visible, (5) attachment menu and keyboard, (6) streaming/tool result. Capture Sessions empty/populated separately for the next batch.

No screen is marked matched. Status: **ঠিক করা হয়েছে, যাচাই বাকি**.

## CI / delivery

- GitHub Actions run #16: https://github.com/tufazzal513/Aton-Ai/actions/runs/45943564808 — **success**.
- `:app:testDebugUnitTest` and `:app:assembleDebug` completed successfully; uploaded artifact: `atonai-debug` (test report: `atonai-test-report`).
- Final commit on `main`: `4ea528697d91cd0d6c62e152997df2b6f1367446`.