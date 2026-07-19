import { Component, input } from '@angular/core';

/**
 * Scaffold notice shown on migrated-but-not-yet-backed screens. These offering/
 * class detail & edit screens (Wave 4) have their Angular UI shells in place, but
 * the additive JSON command bean that will feed real data is not wired yet — this
 * banner keeps that honest to the user instead of showing fake data as real.
 */
@Component({
  selector: 'app-wip-banner',
  template: `
    <div class="wip-banner">
      <i class="pi pi-info-circle"></i>
      <div>
        <b>UI scaffold.</b> This screen's layout is migrated to Angular, but its backend
        command bean @if (backend()) {(<code>{{ backend() }}</code>)} is not wired yet — the
        values below are placeholders until the additive facade endpoint lands.
      </div>
    </div>
  `,
})
export class WipBanner {
  /** Planned backend request name, surfaced so the wiring target is discoverable. */
  readonly backend = input<string>('');
}
