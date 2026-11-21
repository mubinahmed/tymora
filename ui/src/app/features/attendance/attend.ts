import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';
import { MyAttendanceResponse } from './attendance.models';

const DEVICE_KEY = 'tymora.attendance.device';

/**
 * QR check-in landing page (/attend?m=<meeting>&t=<token>): the URL encoded in the rotating classroom QR code.
 * The student is signed in by the route guard (returning here afterwards); the check-in is sent with a
 * per-browser device id so the one-device rule can be enforced. Backed by MyAttendanceRequest CHECKIN.
 */
@Component({
  selector: 'app-attend',
  imports: [RouterLink, ButtonModule, ProgressSpinnerModule],
  template: `
    <div class="att-card">
      @if (busy()) {
        <p-progressSpinner strokeWidth="4" />
        <div>Checking you in…</div>
      } @else if (ok()) {
        <i class="pi pi-check-circle big ok"></i>
        <div class="st">{{ result()?.checkinStatus === 'LATE' ? 'Checked in (late)' : 'Checked in' }}</div>
        <div>{{ result()?.checkinClass }}</div>
        <div class="muted">{{ result()?.message }}</div>
      } @else {
        <i class="pi pi-times-circle big bad"></i>
        <div class="st">Not checked in</div>
        <div class="muted">{{ error() }}</div>
        <div class="muted">Scan the code on the screen again, or tell your lecturer.</div>
      }
      <a routerLink="/my-attendance"><p-button label="My attendance" [text]="true" /></a>
    </div>
  `,
  styles: `
    .att-card { max-width: 26rem; margin: 2rem auto; padding: 2rem 1.5rem; border: 1px solid var(--ut-border); border-radius: 14px; display: grid; gap: 0.6rem; justify-items: center; text-align: center; background: var(--ut-card); }
    .big { font-size: 4rem; } .ok { color: #16a34a; } .bad { color: #c81e14; }
    .st { font-size: 1.3rem; font-weight: 700; color: var(--ut-ink); }
    .muted { color: var(--ut-muted); font-size: 0.85rem; }
  `,
})
export class Attend implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private route = inject(ActivatedRoute);

  protected readonly busy = signal(true);
  protected readonly ok = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly result = signal<MyAttendanceResponse | null>(null);

  ngOnInit(): void {
    this.page.set('Check In');
    const q = this.route.snapshot.queryParamMap;
    const meetingId = Number(q.get('m'));
    const token = q.get('t') ?? '';
    if (!meetingId || !token) { this.busy.set(false); this.error.set('This check-in link is incomplete.'); return; }
    this.rpc.execute<MyAttendanceResponse>('MyAttendanceRequest', { operation: 'CHECKIN', meetingId, token, deviceId: this.deviceId() }).subscribe({
      next: (r) => { this.result.set(r); this.ok.set(true); this.busy.set(false); },
      error: (e: ApiError) => { this.error.set(e.message); this.busy.set(false); },
    });
  }

  private deviceId(): string {
    try {
      let id = localStorage.getItem(DEVICE_KEY);
      if (!id) { id = crypto.randomUUID(); localStorage.setItem(DEVICE_KEY, id); }
      return id;
    } catch {
      return 'nostorage-' + navigator.userAgent.length;
    }
  }
}
