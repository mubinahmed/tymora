# UniTime Full Modernization — Plan & Progress

Goal: modernize all pages (user chose "full page-by-page"), preserving all functionality,
routes, and backend behavior. No running app available to verify visually (Docker build not up),
so all changes are **additive and behavior-preserving**; risky visual redesigns are deferred to
a verification pass.

## Codebase reality (why the strategy is shared-first)
- 86 JSPs: admin/29, user/30, exam/11, root/13, tt/2. Most are **Tiles body fragments** rendered
  inside `layouts/layout-struts2.jsp`.
- Markup is largely **table-layout + Struts/`tt:` tag-generated controls** (`<s:checkbox>`, etc.),
  not hand-written HTML. `.unitime-MainTable` is the shared container in **72 of 86** pages.
- Therefore the highest-leverage + safest modernization is in the **shared CSS** (lifts all pages
  at once) plus safe per-page semantic/ARIA fixes. Blind per-file visual rewrites would regress
  (proven: `.gwt-SuggestBox-readonly` transparent border would be clobbered by a global input rule).
- `* { box-sizing: border-box }` is global → padding/border changes don't shift footprints.
- `:where()` (zero specificity) is used for modern *defaults* so existing rules always win.

## Phases
- [x] **Phase 0 — Accessibility foundation** (done earlier)
      focus-visible ring, reduced-motion, skip links, ARIA landmarks, lang, login autocomplete, tokens.
      Files: unitime.css (appended), gwt.jsp, layout-struts2.jsp, login2.jsp.
- [ ] **Phase A — Shared visual modernization (CSS)**
      Modern system font stack + smoothing + line-height (typography is the biggest safe uplift);
      calm default styling for plain inputs/select/textarea and classic buttons via `:where()`.
      Applies to all pages. Additive, appended to unitime.css.
- [ ] **Phase B — Per-page semantic / ARIA batches** (safe, no visual verification needed)
      - [ ] B1 root/standalone host pages: index, login (legacy), gwt2, error, loginRequired,
            selectPrimaryRole, logout, close  (lang, landmarks, skip where applicable)
      - [ ] B2 admin/ (29)
      - [ ] B3 user/ (30)
      - [ ] B4 exam/ (11)
      - [ ] B5 tt/ (2) + main.jsp
      Per-page checklist: img `alt`; icon-only actions `aria-label`; data-table `<th scope>` /
      `<caption>`; heading order; associate labels where a raw label + control id exist; `type`
      on buttons; obvious contrast/inline-style fixes.
- [ ] **Phase C — Verification pass** (requires app running)
      Fix the Docker build, deploy, then visually verify Phases A/B and iterate on deeper visual
      modernization (tables, forms, spacing) that can't be safely done blind.

## Progress log
- (init) Inventory + strategy established.
- Phase A DONE: appended "Shared Visual Modernization" to unitime.css — system font stack,
  line-height, font smoothing; calm default input/select/textarea + classic button styling via
  :where() (zero-specificity, so no regressions). Lifts all pages.
- Phase B1 DONE: `lang` added to standalone host pages index/login/logout/close (gwt/login2 already
  done; gwt2/loginRequired/selectPrimaryRole are fragments that inherit the layout's landmarks).
- Phase B a11y sweep (in progress): images missing `alt` — 42 found across ~15 files.
  - Fixed (decorative → alt=""): distributionPrefs (2), examDistributionPrefs (2), header-struts2 (1).
  - Fixed (interactive icon → alt mirrors localized title): instructionalOfferingModify (6),
    classInstructorAssignment (2), crossListsModify (1).
  - STILL TODO (need judgment / message keys, so deferred to avoid hardcoding English into a
    localized app): status icons accept.png/cross.png (yes/no meaning) in departmentEdit,
    instructionalOfferingModify, instructionalOfferingModify; calendar.png preview icons in
    classDetail/classEdit/schedulingSubpartDetail/schedulingSubpartEdit (some use JS-concat, no
    MSG); cancel.png error markers; expand/collapse gifs (instructorDetail); chart img (stats.jsp);
    solverSettings img.

## Remaining Phase B categories (all pages) — mechanical + safe, not yet swept
- Remaining `alt` items above.
- Data-table semantics: `<th scope>` / `<caption>` on genuine data tables (vs the layout tables).
- Label association for `<s:checkbox>`/`<s:select>` where a visible `<loc:message>` label exists.
- Heading order; `type` on `<button>`; deprecated attrs (align/vspace/bgcolor) — cosmetic, low risk.

## Menu modernization (Bootstrap 5 / PrimeNG p-menubar style) — DONE (CSS only)
- Target: GWT `MenuBar` top navigation (`.unitime-Menu` / `.gwt-MenuBar`) + dropdown popups
  (`.gwt-MenuBarPopup`, a GWT DecoratedPopupPanel). Confirmed the widget in `UniTimeMenuBar.java`.
- Validated every selector against the deployed theme: `target/gwt/unitime/gwt/standard/standard.css`.
- Appended a "Modern Menu" block to unitime.css: light surface navbar, blue hover/active items,
  white card dropdowns with shadow/radius, thin dividers. Legacy 9-cell decorator gradient/corner
  images neutralised (`[class*="menuPopup"] { background-image:none }`, applies to td AND inner div)
  and edge cells collapsed; content cell (MiddleCenter) kept.
- To switch to a solid brand-colour navbar: change `.unitime-Menu` background to #2952A3 and the two
  item `color` values to #fff (inline comments mark the spots).
- NOT restyled: the alternative side menus (Stack/Tree disclosure panels, `unitime-MenuHeader*`)
  and the mobile hamburger menu — different widgets; the default config is the top navbar.
- LIMITATION: done blind (no running app). Selectors are theme-validated, but the exact pixel
  result should be visually verified once the app runs.

## Key limitation (why Phase C matters)
Deep visual modernization (table→responsive layout, spacing, form redesign) and confirmation of
Phases A/B CANNOT be done safely without a running app to verify. Standing up the app (fixing the
Docker build) is the prerequisite for the verified deep pass — and also the user's original goal.
