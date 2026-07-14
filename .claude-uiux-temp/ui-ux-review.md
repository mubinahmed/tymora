# UniTime UI/UX Review — Working Notes

Scope: accessibility + UX improvements to the shared front-end layer, preserving all
functionality, routes, and backend behavior. See `.claude/UI_UX.md` for the brief.

## Stack observed
- Server-rendered **JSP** pages (86 files) using custom taglibs (`tt:`, `loc:`, Struts `s:`, Tiles).
- **GWT 2.12** client (Java → JS) mounted into host JSPs via DOM ids (`UniTimeGWT:*`).
- Styling: hand-written CSS, **no design tokens**. `unitime.css` (~8k lines) is the main
  sheet, `unitime-mobile.css` handles responsiveness (breakpoints at 800px / 400px),
  plus `-rtl`, `-ie`, `timetabling.css`.
- Host pages: `gwt.jsp` (GWT app), `layouts/layout-struts2.jsp` (classic pages), `login2.jsp`.
- Layout uses `<span>` elements with CSS display overrides (not semantic tags) and targets
  ids from GWT — so tag names / classes / ids MUST NOT change. Only additive attributes are safe.

## Key issues found (evidence)
1. **Keyboard focus suppressed** — 12 `outline:none`/`outline:0` rules in `unitime.css`
   (menus, disclosure panels, filter boxes, date selector, image buttons, textareas).
   Keyboard users had no visible focus. (WCAG 2.4.7)
2. **Zoom disabled** — `gwt.jsp` viewport had `maximum-scale=1`, blocking pinch-zoom. (WCAG 1.4.4)
3. **No reduced-motion support** — transitions/animations always run. (WCAG 2.3.3)
4. **No landmarks / skip link** — screen-reader/keyboard users must tab through the whole
   menu on every page; regions not identified. (WCAG 2.4.1, 1.3.1)
5. **Missing `lang`** on `<html>` across host pages. (WCAG 3.1.1)
6. **Login**: no `autocomplete` (password-manager/autofill), no `lang`, no main landmark.
7. **No design tokens** — colours hard-coded ~hundreds of times.

## Decisions
- Made all CSS changes as ONE appended, clearly-delimited, removable block at the end of
  `unitime.css` (loads last, augments, no risk to existing rules; avoids editing 8k lines and
  avoids adding a new stylesheet link to every host page).
- Used `:focus-visible` (not `:focus`) so mouse interaction is visually unchanged; `!important`
  to override the legacy `outline:none`.
- Colours drawn from existing palette: `#0066CC` (link/accent), `#2952A3`, `#9CB0CE`, `#333333`.
- ARIA landmarks added as attributes only (role=banner/navigation/main/contentinfo) — no tag
  or class changes, so GWT id targeting and CSS selectors are unaffected.
- Skip-link text is hardcoded English (see follow-ups) to avoid adding message-bundle keys
  across 5 locale files + generated interfaces (heavier, higher risk).

## Known tradeoffs / follow-ups
- Two `navigation` landmarks (top-menu + sidebar) exist; only one is populated per menu-style
  config, so the other renders as an empty labeled nav. Follow-up: make `role` conditional on
  the `unitime.menu.style` property.
- Localize the skip-link label (add a `GwtMessages`/`CourseMessages` key).
- Longer-term: migrate hard-coded colours to the new `:root` tokens for consistency and theming.
- Consider `<label for>` association on the login fields (kept `aria-label` to avoid a
  Label-in-Name mismatch, WCAG 2.5.3, since visible text and aria text differ).

## Validation notes
- Changes are CSS + JSP only; no Java/GWT source changed. JSPs compile at deploy (Tomcat),
  CSS needs no compilation, and both are copied into the WAR verbatim — a Maven/GWT build does
  not exercise them. Validated instead by: CSS brace-balance + structure review, and reviewing
  every JSP diff (additive attributes on existing tags; no structural changes).
