# Notate

Notate is a handwriting-first notebook system whose document, canvas, navigation, recognition, and export concepts share one product language.

## Language

**Notebook**:
A persisted Notate document containing Canvas Objects and, when accepted, handwriting transcriptions.
_Avoid_: Note file, canvas file

**Fixed-page Notebook**:
A Notebook made of equally sized pages arranged in a vertical sequence.
_Avoid_: Fixed size, finite canvas

**Infinite-canvas Notebook**:
A Notebook whose drawing surface has no page boundary.
_Avoid_: Endless page

**Viewport**:
The panned and zoomed window through which a Notebook is viewed. A Viewport never rotates.
_Avoid_: Camera rotation, canvas rotation

**Canvas Object**:
An editable visual item in a Notebook, such as ink, typed text, an image, or a link. Rotation applies to Canvas Objects, never to the Viewport.
_Avoid_: Element, drawable

**Handwriting Line**:
A horizontal spatial group of ink treated as one editable handwriting-recognition unit. A source stroke may belong to more than one Handwriting Line.
_Avoid_: OCR block, paragraph

**Recognition Provider**:
An interchangeable on-device engine that returns Recognition Candidates for a Handwriting Line.
_Avoid_: OCR backend

**Recognition Candidate**:
Provisional text returned by a Recognition Provider before acceptance.
_Avoid_: Recognized text, OCR result

**Accepted Transcription**:
The approved text associated with a Handwriting Line and used as the authoritative value for search and PDF export.
_Avoid_: OCR cache

**Stale Transcription**:
An Accepted Transcription whose linked ink changed and therefore requires review before PDF export.
_Avoid_: Invalid OCR

**Widget Preview Page**:
The device-local representative page selected for a Notebook's launcher-widget preview.
_Avoid_: Cover page

**Page Preview Rail**:
The persistent vertical thumbnail overlay used to navigate a Fixed-page Notebook.
_Avoid_: Page preview sidebar, page drawer
