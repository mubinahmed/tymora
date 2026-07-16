import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { DialogModule } from 'primeng/dialog';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ConfirmationService, MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';

/**
 * Create / Edit for the legacy distributionPrefs.action (Distribution
 * Preferences) page. Sibling to the read-only Distribution Preferences list.
 * Backed by the DistributionPrefEditRequest command bean: LOAD returns the
 * current user's preferences as string rows (plus, when an id is given, the
 * editable detail of one preference with the applicable distribution types and
 * preference levels), SAVE updates the distribution type and preference level of
 * an existing preference, DELETE removes one. Gated by
 * Right.DistributionPreferences (plus per-preference edit / delete rights).
 *
 * Functional core: only the distribution TYPE and preference LEVEL of an
 * existing preference are edited (owners / applies-to are shown read-only).
 * Creating new preferences and editing owners remain on the legacy page.
 */

// --- request / response DTOs (inline; match Gson field naming iField -> field) ---
type Operation = 'LOAD' | 'SAVE' | 'DELETE';

interface SubjectAreaInfo {
  id?: number;
  label?: string;
}

interface DistributionTypeInfo {
  id?: number;
  label?: string;
  allowed?: string;
}

interface PrefLevelInfo {
  id?: number;
  name?: string;
  char?: string;
}

interface DistPrefRow {
  id?: number;
  cells?: string[];
}

interface DistributionPrefRecord {
  id?: number | null;
  distributionTypeId?: number | null;
  prefLevelId?: number | null;
  typeLabel?: string;
  ownerLabel?: string;
  appliesTo?: string;
}

interface DistributionPrefEditRequest {
  operation: Operation;
  subjectAreaId?: number;
  id?: number;
  record?: DistributionPrefRecord;
}

interface DistributionPrefEditResponse {
  title?: string;
  editable?: boolean;
  deletable?: boolean;
  subjectAreaId?: number;
  subjectAreas?: SubjectAreaInfo[];
  columns?: string[];
  rows?: DistPrefRow[];
  record?: DistributionPrefRecord;
  distributionTypes?: DistributionTypeInfo[];
  prefLevels?: PrefLevelInfo[];
}

interface Editing {
  id: number;
  typeLabel: string;
  ownerLabel: string;
  appliesTo: string;
  distributionTypeId: number | null;
  prefLevelId: number | null;
}

@Component({
  selector: 'app-distribution-prefs-edit',
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    SelectModule,
    DialogModule,
    MessageModule,
    ProgressSpinnerModule,
    ConfirmDialogModule,
  ],
  providers: [ConfirmationService],
  templateUrl: './distribution-prefs-edit.html',
})
export class DistributionPrefsEdit implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private confirm = inject(ConfirmationService);
  private messages = inject(MessageService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly data = signal<DistributionPrefEditResponse | null>(null);
  protected readonly filter = signal('');
  protected readonly editable = signal(false);
  protected readonly deletable = signal(false);

  protected readonly distributionTypes = signal<DistributionTypeInfo[]>([]);
  protected readonly prefLevels = signal<PrefLevelInfo[]>([]);

  protected readonly dialogVisible = signal(false);
  protected editing: Editing | null = null;

  protected subjectAreaId: number | null = null;

  protected readonly subjectAreaOptions = computed(() => [
    { label: 'All', value: null as number | null },
    ...(this.data()?.subjectAreas ?? []).map((s) => ({ label: s.label ?? '', value: s.id ?? null })),
  ]);

  protected readonly typeOptions = computed(() =>
    this.distributionTypes().map((t) => ({ label: t.label ?? '', value: t.id ?? null })),
  );

  /** Preference levels allowed by the currently selected distribution type. */
  protected readonly levelOptions = computed(() => {
    const typeId = this.editing?.distributionTypeId ?? null;
    const type = this.distributionTypes().find((t) => t.id === typeId);
    const allowed = type?.allowed ?? '';
    return this.prefLevels()
      .filter((l) => !allowed || (l.char != null && allowed.indexOf(l.char) >= 0))
      .map((l) => ({ label: l.name ?? '', value: l.id ?? null }));
  });

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

  private applyResponse(d: DistributionPrefEditResponse): void {
    this.data.set(d);
    this.subjectAreaId = d.subjectAreaId ?? null;
    this.editable.set(!!d.editable);
    this.deletable.set(!!d.deletable);
    this.distributionTypes.set(d.distributionTypes ?? []);
    this.prefLevels.set(d.prefLevels ?? []);
    this.page.set(d.title || 'Distribution Preferences');
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    const request: DistributionPrefEditRequest = { operation: 'LOAD' };
    if (this.subjectAreaId != null) request.subjectAreaId = this.subjectAreaId;
    this.rpc.execute<DistributionPrefEditResponse>('DistributionPrefEditRequest', request).subscribe({
      next: (d) => {
        this.applyResponse(d);
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  edit(row: DistPrefRow): void {
    if (row.id == null) return;
    const request: DistributionPrefEditRequest = { operation: 'LOAD', id: row.id };
    if (this.subjectAreaId != null) request.subjectAreaId = this.subjectAreaId;
    this.rpc.execute<DistributionPrefEditResponse>('DistributionPrefEditRequest', request).subscribe({
      next: (d) => {
        this.applyResponse(d);
        const rec = d.record;
        if (!rec || rec.id == null) {
          this.messages.add({ severity: 'error', summary: 'Load failed', detail: 'The preference could not be loaded.' });
          return;
        }
        this.editing = {
          id: rec.id,
          typeLabel: rec.typeLabel ?? '',
          ownerLabel: rec.ownerLabel ?? '',
          appliesTo: rec.appliesTo ?? '',
          distributionTypeId: rec.distributionTypeId ?? null,
          prefLevelId: rec.prefLevelId ?? null,
        };
        this.dialogVisible.set(true);
      },
      error: (e: ApiError) => this.messages.add({ severity: 'error', summary: 'Load failed', detail: e.message }),
    });
  }

  onTypeChange(): void {
    // If the current level is no longer allowed by the chosen type, clear it.
    const allowedIds = new Set(this.levelOptions().map((o) => o.value));
    if (this.editing && !allowedIds.has(this.editing.prefLevelId)) this.editing.prefLevelId = null;
  }

  cancel(): void {
    this.dialogVisible.set(false);
    this.editing = null;
  }

  submit(): void {
    if (!this.editing) return;
    if (this.editing.distributionTypeId == null) {
      this.messages.add({ severity: 'error', summary: 'Validation', detail: 'Distribution type is required.' });
      return;
    }
    if (this.editing.prefLevelId == null) {
      this.messages.add({ severity: 'error', summary: 'Validation', detail: 'Preference level is required.' });
      return;
    }
    const record: DistributionPrefRecord = {
      id: this.editing.id,
      distributionTypeId: this.editing.distributionTypeId,
      prefLevelId: this.editing.prefLevelId,
    };
    this.saving.set(true);
    const request: DistributionPrefEditRequest = { operation: 'SAVE', record };
    if (this.subjectAreaId != null) request.subjectAreaId = this.subjectAreaId;
    this.rpc.execute<DistributionPrefEditResponse>('DistributionPrefEditRequest', request).subscribe({
      next: (d) => {
        this.applyResponse(d);
        this.saving.set(false);
        this.dialogVisible.set(false);
        this.editing = null;
        this.messages.add({ severity: 'success', summary: 'Saved', detail: 'Distribution preference updated.' });
      },
      error: (e: ApiError) => {
        this.saving.set(false);
        this.messages.add({ severity: 'error', summary: 'Save failed', detail: e.message });
      },
    });
  }

  confirmDelete(row: DistPrefRow): void {
    if (row.id == null) return;
    const label = (row.cells ?? []).slice(0, 3).filter((c) => c).join(' — ');
    this.confirm.confirm({
      header: 'Delete distribution preference',
      message: `Delete "${label || 'this preference'}"? This cannot be undone.`,
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => this.doDelete(row.id!),
    });
  }

  private doDelete(id: number): void {
    const request: DistributionPrefEditRequest = { operation: 'DELETE', record: { id } };
    if (this.subjectAreaId != null) request.subjectAreaId = this.subjectAreaId;
    this.rpc.execute<DistributionPrefEditResponse>('DistributionPrefEditRequest', request).subscribe({
      next: (d) => {
        this.applyResponse(d);
        this.messages.add({ severity: 'success', summary: 'Deleted', detail: 'Distribution preference deleted.' });
      },
      error: (e: ApiError) => this.messages.add({ severity: 'error', summary: 'Delete failed', detail: e.message }),
    });
  }
}
