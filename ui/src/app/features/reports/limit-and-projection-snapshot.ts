import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';

const SERVICE = 'snapshot.gwt';

/**
 * Limit and Projection Snapshot — classic GWT RemoteService screen (snapshot.gwt).
 * Mirrors LimitAndProjectionSnapshotService:
 *   - canTakeSnapshot(): Boolean  — gates the action button (LimitAndProjectionSnapshotSave permission)
 *   - getCurrentSnapshotDate(): Date — the current stored snapshot timestamp for the academic session
 *   - takeSnapshot(): Date — stores a new projected-demand snapshot, returns its timestamp
 * The backend Date serializes to epoch millis (or an ISO string); both are handled.
 */
@Component({
  selector: 'app-limit-and-projection-snapshot',
  imports: [DatePipe, ButtonModule, CardModule, MessageModule, ProgressSpinnerModule],
  templateUrl: './limit-and-projection-snapshot.html',
})
export class LimitAndProjectionSnapshot implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);

  protected readonly loading = signal(true);
  protected readonly taking = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly canTake = signal(false);
  protected readonly snapshotDate = signal<Date | null>(null);

  ngOnInit(): void {
    this.page.set('Limit and Projection Snapshot');
    this.reload();
  }

  private reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.rpc.service<boolean>(SERVICE, 'canTakeSnapshot', []).subscribe({
      next: (can) => this.canTake.set(!!can),
      error: () => this.canTake.set(false),
    });
    this.rpc.service<string | number | null>(SERVICE, 'getCurrentSnapshotDate', []).subscribe({
      next: (d) => {
        this.snapshotDate.set(this.toDate(d));
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  takeSnapshot(): void {
    if (!this.canTake() || this.taking()) return;
    this.taking.set(true);
    this.error.set(null);
    this.rpc.service<string | number | null>(SERVICE, 'takeSnapshot', []).subscribe({
      next: (d) => {
        this.snapshotDate.set(this.toDate(d));
        this.taking.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.taking.set(false);
      },
    });
  }

  private toDate(value: string | number | null): Date | null {
    if (value == null) return null;
    const d = new Date(value);
    return isNaN(d.getTime()) ? null : d;
  }
}
