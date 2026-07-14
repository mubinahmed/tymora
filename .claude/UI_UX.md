You are working on the UniTime project located in the current project root directory.

Your task is to review and improve the project’s UI and UX while preserving all existing functionality, backend behavior, routes, integrations, data handling, and business logic.

## Primary objective

Modernize and improve the UniTime user interface and user experience so that it is:

* Clearer and easier to navigate
* Visually consistent
* Responsive across desktop, tablet, and mobile
* Accessible
* Easier to understand for first-time and frequent users
* Consistent with the project’s existing architecture and technology stack. Introduce new UI library if needed.
* Maintainable and reusable

Focus on practical improvements rather than unnecessary redesigns.

## Operating rules

Follow these rules strictly.

### Project boundary

* Treat the current working directory as the project root.
* You may only read, inspect, search, create, or modify files located inside the project root.
* Do not read files from the user’s home directory, parent directories, system directories, global configuration directories, SSH directories, credential stores, browser profiles, environment-specific directories, or any path outside the project root.
* Do not run commands that inspect files outside the project root.
* Do not follow symlinks that resolve outside the project root.
* Do not use external local files as input.
* Before using any path, ensure that its resolved path remains within the project root.

### No deletion

* Do not delete any file or directory.
* Do not run `rm`, `rmdir`, `unlink`, `git clean`, or any equivalent deletion command.
* Do not overwrite a file with empty content as a substitute for deletion.
* Do not remove existing assets, source files, tests, configuration files, generated files, or documentation.
* Do not rename or move files when doing so would effectively remove them from their current location unless absolutely necessary.
* Prefer editing files in place or adding new files.
* If obsolete code must be disabled, preserve it safely and explain the change.

Deletion is the only category of action for which you must stop and ask the user for explicit approval. Unless deletion is essential, do not propose it.

### Command execution and approvals

* Do not ask the user for approval before running non-destructive commands inside the project root.
* Proceed autonomously with project inspection, searches, builds, tests, formatting, linting, dependency inspection, and code modifications.
* Do not pause to ask whether you should continue.
* Do not ask for confirmation between implementation steps.
* Make reasonable decisions based on the existing codebase and continue until the task is complete.
* Do not request permission for commands that only read, create, or modify files within the project root.
* Never attempt to bypass or weaken platform security controls.
* If the execution environment itself requires a mandatory approval that cannot be avoided, explain exactly which command is blocked and continue with all other work that does not require that approval.

### Temporary files

* If temporary files or directories are required, create them inside the project root.

* Use a clearly named directory such as:

  `.claude-uiux-temp/`

* Do not create temporary files in `/tmp`, the home directory, parent directories, or elsewhere outside the project root.

* Do not delete the temporary directory or its contents when finished.

* At the end, report the exact relative path of every temporary file or directory created.

* Ask the user to delete those temporary files manually after reviewing the work.

### Preserve functionality

* Do not break existing features.
* Do not alter backend APIs unless a UI change genuinely requires it.
* Do not change database schemas or migrations unless strictly necessary.
* Do not change authentication, authorization, scheduling logic, data integrity rules, or security-sensitive behavior unless required to fix an existing UI-related defect.
* Preserve existing routes, request parameters, form behavior, and integrations.
* Avoid large framework migrations.
* Work within the project’s current frontend stack and conventions.
* Reuse existing components, styles, utilities, tokens, and dependencies where practical.
* Do not add new dependencies unless they provide clear value and are compatible with the existing stack.
* When adding a dependency, document why it was needed.

## Initial inspection

Begin immediately. Do not ask the user where to start.

Inspect the project to determine:

* Frontend framework and rendering approach
* Main UI entry points
* Layout and navigation structure
* Styling system
* Shared components
* Page templates
* Forms and validation
* Tables, calendars, dialogs, menus, and notifications
* Responsive behavior
* Existing accessibility patterns
* Build, lint, formatting, and test commands
* Existing design tokens, themes, icons, and assets
* Areas with duplicated or inconsistent UI code

Read relevant documentation and configuration files located inside the project root.

Do not inspect anything outside the project root.

## UI/UX review

Evaluate the current interface for:

* Visual hierarchy
* Typography
* Spacing
* Alignment
* Color contrast
* Component consistency
* Navigation clarity
* Form usability
* Validation and error states
* Empty states
* Loading states
* Success feedback
* Destructive-action clarity
* Table readability
* Calendar and scheduling usability
* Mobile responsiveness
* Keyboard navigation
* Focus visibility
* Semantic markup
* Screen-reader labels
* Modal and dialog behavior
* Content density
* Long text handling
* Overflow behavior
* Touch target sizes
* Repeated workflows
* User effort and unnecessary steps

Prioritize issues that affect usability, accessibility, consistency, and common workflows.

## Implementation requirements

Implement meaningful UI/UX improvements directly in the codebase.

Where appropriate:

* Improve page structure and visual hierarchy.
* Standardize spacing, sizing, typography, controls, and states.
* Improve navigation labels and active states.
* Improve responsive layouts.
* Improve forms, field labels, helper text, validation messages, and submit states.
* Improve tables with readable headers, spacing, overflow handling, and clear actions.
* Improve dialogs, confirmations, notifications, loading indicators, and empty states.
* Add visible keyboard focus styles.
* Improve semantic HTML and ARIA attributes where needed.
* Ensure icon-only actions have accessible labels.
* Ensure color is not the only indicator of status.
* Respect reduced-motion preferences where animation exists.
* Reuse components instead of duplicating markup.
* Avoid excessive animation, visual clutter, and cosmetic complexity.
* Keep the design appropriate for an academic scheduling and administration system.

Do not introduce placeholder content or fake production data.

## Design direction

Use a restrained, professional design suitable for universities, administrators, instructors, students, and scheduling staff.

Prefer:

* Clear information hierarchy
* Calm and readable surfaces
* Consistent spacing
* Accessible contrast
* Predictable interactions
* Compact but readable data displays
* Strong form usability
* Clear scheduling and calendar states
* Responsive navigation
* Reusable components
* Progressive disclosure for complex controls

Avoid:

* Trend-driven redesigns that reduce usability
* Excessive gradients
* Excessive animation
* Oversized decorative elements
* Hidden controls
* Unclear icons
* Low-contrast text
* Unnecessary modal dialogs
* Breaking established user workflows without a strong reason

## Validation

After making changes, run the relevant commands available inside the project, such as:

* Formatting
* Linting
* Type checking
* Frontend tests
* Unit tests
* Build
* Relevant integration tests

Run commands from within the project root.

Do not run commands that access or inspect paths outside the project root.

Fix issues caused by your changes. Do not delete failing tests. Do not weaken tests merely to make them pass.

If the full test suite cannot run because of an existing environmental problem, run the largest relevant subset possible and clearly report the limitation.

## Git and repository safety

* Do not use destructive Git commands.
* Do not run `git reset --hard`.
* Do not run `git clean`.
* Do not discard unrelated user changes.
* Do not revert files you did not intentionally modify.
* Review the working tree before and after implementation.
* Keep changes focused on UI/UX.
* Do not commit, push, create branches, or open pull requests unless the user explicitly requests it.
* Do not delete untracked files.

## Autonomous decision-making

Do not ask broad planning questions.

When multiple reasonable options exist:

1. Inspect the existing project patterns.
2. Choose the option most consistent with the current architecture.
3. Prefer the least disruptive implementation.
4. Implement it.
5. Document the decision in the final report.

Only stop for user input when:

* A deletion is genuinely required.
* A mandatory platform restriction blocks an essential action.
* Required information cannot be inferred from files inside the project root.

Do not stop merely because a design choice is subjective. Make a sensible, conservative decision and continue.

## Final response

When finished, provide:

1. A concise summary of the UI/UX improvements.
2. A list of files created or modified.
3. The main usability and accessibility issues addressed.
4. Commands and tests run.
5. Test, lint, type-check, and build results.
6. Any remaining limitations or recommended follow-up work.
7. Any dependencies added and the reason for each.
8. The exact relative path of any temporary directory or file created.

Do not delete temporary files.

End with a clear instruction such as:

“Temporary working files were preserved at `<relative-path>`. Please review them and delete that directory manually when it is no longer needed.”

Begin by inspecting the project root and then proceed directly with the UI/UX review and implementation.
