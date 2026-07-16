import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';

/** One selectable subject area (DistributionPrefListInterface.SubjectAreaInfo). */
interface SubjectAreaInfo {
  id?: number;
  label?: string;
}

/** A projected distribution-preference row (SimpleListInterface.Row). */
interface DistPrefRow {
  id?: number;
  cells?: string[];
}

/** Response of the DistributionPrefListRequest bean (DistributionPrefListResponse). */
interface DistributionPrefListResponse {
  title?: string;
  subjectAreaId?: number;
  subjectAreas?: SubjectAreaInfo[];
  columns?: string[];
  rows?: DistPrefRow[];
}

/** Request payload for the DistributionPrefListRequest command bean. */
interface DistributionPrefListRequest {
  subjectAreaId?: number;
}

/**
 * Read-only listing for the legacy distributionPrefs.action (Distribution
 * Preferences) page. Optionally filter by subject area; the backend returns the
 * distribution preferences of the current user's departments (plus their
 * instructor preferences) projected to string rows (Preference, Distribution
 * Type, Owner, Applies To). Served by the new DistributionPrefListBackend command
 * bean; gated by Right.DistributionPreferences for the current academic session.
 * Add/edit/delete and PDF/CSV export remain on the legacy page (deferred).
 */
@Component({
  selector: 'app-distribution-prefs',
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    SelectModule,
    MessageModule,
    ProgressSpinnerModule,
  ],
  templateUrl: './distribution-prefs.html',
})
export class DistributionPrefs implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly data = signal<DistributionPrefListResponse | null>(null);
  protected readonly filter = signal('');

  protected subjectAreaId: number | null = null;

  protected readonly subjectAreaOptions = computed(() => [
    { label: 'All', value: null as number | null },
    ...(this.data()?.subjectAreas ?? []).map((s) => ({ label: s.label ?? '', value: s.id ?? null })),
  ]);

  protected readonly columns = computed<string[]>(() => this.data()?.columns ?? []);

  protected readonly rows = computed<DistPrefRow[]>(() => {
    const all = this.data()?.rows ?? [];
    const f = this.filter().trim().toLowerCase();
    if (!f) return all;
    return all.filter((r) => (r.cells ?? []).join(' ').toLowerCase().includes(f));
  });

  ngOnInit(): void {
    this.page.set('Distribution Preferences');
    this.load();
  }

  onSubjectAreaChange(): void {
    this.load();
  }

  reload(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    const request: DistributionPrefListRequest = {};
    if (this.subjectAreaId != null) request.subjectAreaId = this.subjectAreaId;
    this.rpc.execute<DistributionPrefListResponse>('DistributionPrefListRequest', request).subscribe({
      next: (d) => {
        this.data.set(d);
        this.subjectAreaId = d.subjectAreaId ?? null;
        this.page.set(d.title || 'Distribution Preferences');
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }
}
