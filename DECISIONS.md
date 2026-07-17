# Notate Canvas, Navigation, Performance, Widget, and Handwriting Decisions

This log records the product decisions for the combined release. There are no unresolved product decisions. Manual BOOX verification remains pending for palm-coordinate stability and perceived highlighter performance.

## Global invariants

- Rotation applies to Canvas Objects only. The Viewport never rotates.
- All ten requested changes ship as one release.
- New user preferences are global unless this document explicitly identifies per-widget or device-local state.

## Zoom and fixed-page gestures

- Zoom to Fit is available in the canvas sidebar and toolbar customization palette, but is never inserted into a default or existing toolbar.
- Fixed-page Zoom to Fit centers the current page. Infinite-canvas Zoom to Fit includes every visible Canvas Object and ignores an active selection.
- Fit uses a 5% inset on every side, clamps to zoom limits, and snaps without animation. An empty infinite canvas resets to 100% at the origin.
- Fixed-page `Allow pinch zoom` and `Allow object rotation` are enabled by default.
- Disabling pinch preserves two-finger panning and explicit Zoom to Fit.
- Disabling object rotation preserves existing angles, move, and scale; it hides rotation controls and ignores twist.
- Two fingers manipulate the canvas or selection. Three fingers are tap-only and never pan, zoom, or rotate.

## Shape correction and palm rejection

- Shape Rotation Correction is separate from selection Angle Snapping, visible only with Shape Perfection, and enabled by default.
- It snaps orientation-bearing detected shapes to 15-degree increments. Tight, Normal, and Loose thresholds are 2, 4, and 6 degrees; Normal is the default.
- Lines rotate around their midpoint and closed shapes around their centroid. Circles are unaffected.
- Disabling correction preserves detected orientation and removes the unconditional rectangle-axis correction.
- Shape correction remains active when manual object rotation is disabled.
- Palm Rejection remains disabled by default. When enabled, all one- and two-finger canvas input is consumed while stylus input remains active.
- The three-finger distraction-free tap remains available with Palm Rejection, except during an active stylus stroke.

## Page Preview Rail

- The existing modal page grid remains available.
- Global rail modes are Off, Auto, and Pinned; Off is the default.
- Auto opens at page-fit zoom or below and closes above 115% of page-fit zoom.
- The rail overlays the canvas and does not change Zoom to Fit calculations.
- Side is user-selectable, defaulting left. Size is Compact 112 dp or Large 168 dp, defaulting Compact.
- Pin/Unpin switches Auto and Pinned. Closing Auto suppresses it until a complete zoom close/reopen cycle; closing Pinned selects Off.
- Distraction-free mode hides the rail temporarily.
- Canvas editing continues outside the rail. Selecting a thumbnail keeps it open and scrolls/highlights the current page.

## Toolbar editing

- A genuine canvas tap outside the edit panel and toolbar dismisses edit mode and is consumed.
- Toolbar drag/remove remains available, including a drag leaving toolbar bounds.
- Toolbar actions stay disabled during editing.
- Settings dismisses edit mode and opens settings in the same tap. Android Back dismisses edit mode before navigation.

## Launcher widget

- Notate provides one configurable RemoteViews widget supporting Automatic recents or one Pinned Notebook.
- New widgets start in Automatic mode with four entries; the count is configurable from 1 through 12.
- Automatic mode can show all projects or one project and defaults to all projects.
- Every entry shows a page preview and Notebook name. There is no title-only privacy mode.
- The header opens Home and an entry opens its Notebook. The widget has no create or navigation buttons.
- A Widget Preview Page is selected only from widget configuration and stored device-locally by Notebook UUID.
- Page one is the fallback when no preview is selected or a selection becomes invalid.
- An unavailable pinned Notebook keeps its configuration and displays a reconfiguration message.
- Widget refresh is event-driven rather than periodic.

## Highlighter

- Optimized finalization is the normal path; Legacy remains a globally persisted Debug-menu option.
- Optimized finalization redraws only safe dirty regions asynchronously and falls back per tile to full regeneration.
- Rendering must preserve intra-stroke blending, inter-stroke darkening, under-ink z-order, and export appearance.
- Starting or rendering the next stroke must not wait for finalization.
- Cheap timing/tile metrics may consume at most 1 ms and live only for the current app process.
- Automated tests gate correctness. Perceived performance ships only after manual BOOX approval; there is no hardware latency number.

## Handwriting recognition

- Handwriting recognition is the provider-neutral product term; OCR refers only to an image-based provider implementation.
- PP-OCR is the default provider. Google ML Kit Digital Ink is the second provider.
- Global recognition mode is Use Default Provider or Compare Installed Providers; Use Default Provider is the default.
- Compare mode always pauses for review. Retry can choose any installed provider and requires confirmation before replacing accepted text.
- One Accepted Transcription represents one horizontal, left-to-right Handwriting Line. Vertical Chinese ordering is not guaranteed.
- Source strokes may belong to multiple accepted lines, and every accepted overlapping line contributes independently to search and PDF export.
- Whole-line transforms preserve validity and update geometry. Partial/content changes make every affected transcription stale.
- Deleting linked ink removes its recognition associations. Undo restores only Canvas Objects.
- Accepted Transcriptions remain valid across provider/model upgrades.
- Stale Transcriptions remain searchable with a warning but are excluded from PDF export until reviewed.
- Automatic recognition includes freehand non-highlighter ink and excludes new Shape Perfection output. Manual selection may explicitly recognize excluded strokes.
- Migrated v3 non-highlighter strokes are treated as freehand because their origin is unknown.
- Selection review requires an explicit choice between keeping handwriting and replacing it with typed text.
- PP-OCR candidates at or above 50% may be accepted automatically. Lower-confidence candidates require manual acceptance.
- Providers without confidence values may auto-accept their top candidate when review is disabled and persist a null confidence.
- Only accepted text and provenance sync inside the Notebook. Rejected debug candidates remain device-local, contain no images or coordinates, are capped at 10 MB, and expire only through oldest-first eviction or the Clear command.

## Notebook format

- Accepted Transcriptions require Notebook format v4 and stable stroke identifiers.
- Existing v3 Notebooks migrate silently and atomically on the next successful save.
- No compatibility copy is retained. Opening or editing v4 in an older Notate release is unsupported.

## PDF export

- `Embed recognized handwriting` is remembered globally and enabled by default.
- It applies to fixed-page and infinite-canvas vector/bitmap exports and recognizes only Notate ink, never imported background pixels.
- Export waits for recognition with cancellable progress. Missing models offer download or export without handwriting.
- Provider failure pauses for Retry, Choose provider, or Continue without handwriting; provider failover is never silent.
- Canceling recognition/review continues the PDF without the handwriting layer and reports the omission.
- English and Chinese embedding are guaranteed with subsetted fonts. Other scripts are best-effort.
- Unsupported invisible characters are omitted without altering visual ink, and completion reports affected lines and characters.
- Typed text uses the same Unicode path and must occur only once in extracted text.
- Valid Handwriting Lines are emitted in page order, then columns left-to-right, then lines top-to-bottom.
- User-edited Accepted Transcriptions are embedded regardless of their original confidence.

## Settings

- Existing settings screens remain: Input & Gestures, Interface, PDF Export, and Text recognition & search.
- Text recognition & search gains a Handwriting recognition section rather than being renamed.
- `Review recognition before export` defaults off.
- Recognition diagnostics are off by default.

## Verification pending

- Reproduce and verify the original palm/finger/stylus coordinate sequence on the user's BOOX device.
- Confirm the optimized highlighter feels sufficiently faster on the user's BOOX device.
