import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { DialogModule } from 'primeng/dialog';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { TooltipModule } from 'primeng/tooltip';
import { ConfirmationService, MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';

/**
 * Create / Edit for the legacy distributionPrefs.action (Distribution
 * Preferences) page. Sibling to the read-only Distribution Preferences list.
 * Backed by the DistributionPrefEditRequest command bean:
 *   - LOAD returns the current user's preferences as string rows (plus, when an
 *     id is given, the editable detail of one preference: type, structure, level
 *     and the ordered class lines), the applicable distribution types, the
 *     structures and the preference levels;
 *   - COURSES / SUBPARTS / CLASSES drive the Subject -> Course -> Subpart ->
 *     Class (or All) cascade of each line;
 *   - SAVE creates a preference (no id) or updates one (type, structure, level
 *     and lines; instructor preferences only change type and level);
 *   - DELETE removes one.
 * Gated by Right.DistributionPreferences (+ DistributionPreferenceAdd / Edit /
 * Delete). Opening with ?add=1 starts the "Add Distribution Preference" dialog.
 */

// --- request / response DTOs (inline; match Gson field naming iField -> field) ---
type Operation = 'LOAD' | 'SAVE' | 'DELETE' | 'COURSES' | 'SUBPARTS' | 'CLASSES';

interface SubjectAreaInfo {
  id?: number;
  label?: string;
}

interface IdLabel {
  id?: number;
  label?: string;
}

interface StructureInfo {
  id?: string;
  label?: string;
  description?: string;
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

interface ClassLine {
  subjectAreaId?: number | null;
  courseId?: number | null;
  subpartId?: number | null;
  classId?: number | null;
}

interface DistributionPrefRecord {
  id?: number | null;
  distributionTypeId?: number | null;
  prefLevelId?: number | null;
  typeLabel?: string;
  ownerLabel?: string;
  appliesTo?: string;
  structure?: string | null;
  lines?: ClassLine[] | null;
}

interface DistributionPrefEditRequest {
  operation: Operation;
  subjectAreaId?: number;
  id?: number;
  lookupId?: number;
  record?: DistributionPrefRecord;
}

interface DistributionPrefEditResponse {
  title?: string;
  editable?: boolean;
  deletable?: boolean;
  canAdd?: boolean;
  subjectAreaId?: number;
  subjectAreas?: SubjectAreaInfo[];
  columns?: string[];
  rows?: DistPrefRow[];
  record?: DistributionPrefRecord;
  distributionTypes?: DistributionTypeInfo[];
  prefLevels?: PrefLevelInfo[];
  structures?: StructureInfo[];
  options?: IdLabel[];
  savedId?: number;
}

type Option = { label: string; value: number | null };

/** A class line being edited — carries its own cascade option lists. */
interface EditLine {
  subjectAreaId: number | null;
  courseId: number | null;
  subpartId: number | null;
  classId: number | null;
  courseOptions: Option[];
  subpartOptions: Option[];
  classOptions: Option[];
}

interface Editing {
  id: number | null;
  ownerLabel: string;
  appliesTo: string;
  distributionTypeId: number | null;
  structure: string | null;
  prefLevelId: number | null;
  /** false = instructor preference (or no lines): only type / level are editable. */
  linesEditable: boolean;
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
    TooltipModule,
  ],
  providers: [ConfirmationService],
  templateUrl: './distribution-prefs-edit.html',
})
export class DistributionPrefsEdit implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private confirm = inject(ConfirmationService);
  private messages = inject(MessageService);

  /** Query param: when set, open the Add Distribution Preference dialog after load. */
  readonly add = input<string>();

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly data = signal<DistributionPrefEditResponse | null>(null);
  protected readonly filter = signal('');
  protected readonly editable = signal(false);
  protected readonly deletable = signal(false);
  protected readonly canAdd = signal(false);

  protected readonly distributionTypes = signal<DistributionTypeInfo[]>([]);
  protected readonly prefLevels = signal<PrefLevelInfo[]>([]);
  protected readonly structures = signal<StructureInfo[]>([]);

  protected readonly dialogVisible = signal(false);
  protected editing: Editing | null = null;
  /** Class lines of the dialog; a signal so async cascade updates re-render (zoneless). */
  protected readonly editLines = signal<EditLine[]>([]);
  private autoAddDone = false;

  protected subjectAreaId: number | null = null;

  protected readonly subjectAreaOptions = computed(() => [
    { label: 'All', value: null as number | null },
    ...(this.data()?.subjectAreas ?? []).map((s) => ({ label: s.label ?? '', value: s.id ?? null })),
  ]);

  /** Subjects for the class lines (no "All"). */
  protected readonly lineSubjectOptions = computed<Option[]>(() =>
    (this.data()?.subjectAreas ?? []).map((s) => ({ label: s.label ?? '', value: s.id ?? null })),
  );

  protected readonly typeOptions = computed(() =>
    this.distributionTypes().map((t) => ({ label: t.label ?? '', value: t.id ?? null })),
  );

  protected readonly structureOptions = computed(() =>
    this.structures().map((s) => ({ label: s.label ?? s.id ?? '', value: s.id ?? null })),
  );

  protected readonly columns = computed<string[]>(() => this.data()?.columns ?? []);

  protected readonly rows = computed<DistPrefRow[]>(() => {
    const all = this.data()?.rows ?? [];
    const f = this.filter().trim().toLowerCase();
    if (!f) return all;
    return all.filter((r) => (r.cells ?? []).join(' ').toLowerCase().includes(f));
  });

  /** Preference levels allowed by the currently selected distribution type. */
  levelOptions(): Option[] {
    const typeId = this.editing?.distributionTypeId ?? null;
    const type = this.distributionTypes().find((t) => t.id === typeId);
    const allowed = type?.allowed ?? '';
    return this.prefLevels()
      .filter((l) => !allowed || (l.char != null && allowed.indexOf(l.char) >= 0))
      .map((l) => ({ label: l.name ?? '', value: l.id ?? null }));
  }

  structureDescription(): string {
    const id = this.editing?.structure ?? null;
    return this.structures().find((s) => s.id === id)?.description ?? '';
  }

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
    this.canAdd.set(!!d.canAdd);
    this.distributionTypes.set(d.distributionTypes ?? []);
    this.prefLevels.set(d.prefLevels ?? []);
    this.structures.set(d.structures ?? []);
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
        if (this.add() && !this.autoAddDone) {
          this.autoAddDone = true;
          if (d.canAdd) this.openAdd();
        }
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  // ------------------------------------------------------------------ dialog

  private blankLine(subjectAreaId: number | null = null): EditLine {
    return {
      subjectAreaId,
      courseId: null,
      subpartId: null,
      classId: null,
      courseOptions: [],
      subpartOptions: [],
      classOptions: [],
    };
  }

  /** Default subject for a new line: the list filter, or the only subject the user has. */
  private defaultSubject(): number | null {
    if (this.subjectAreaId != null) return this.subjectAreaId;
    const subjects = this.lineSubjectOptions();
    return subjects.length === 1 ? subjects[0].value : null;
  }

  openAdd(): void {
    this.editing = {
      id: null,
      ownerLabel: '',
      appliesTo: '',
      distributionTypeId: null,
      structure: 'AllClasses',
      prefLevelId: null,
      linesEditable: true,
    };
    const line = this.blankLine(this.defaultSubject());
    this.editLines.set([line]);
    if (line.subjectAreaId != null) this.fetchCourses(line);
    this.dialogVisible.set(true);
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
        const lines = rec.lines ?? [];
        this.editing = {
          id: rec.id,
          ownerLabel: rec.ownerLabel ?? '',
          appliesTo: rec.appliesTo ?? '',
          distributionTypeId: rec.distributionTypeId ?? null,
          structure: rec.structure ?? 'AllClasses',
          prefLevelId: rec.prefLevelId ?? null,
          linesEditable: lines.length > 0,
        };
        this.editLines.set(lines.map((l) => {
          const line: EditLine = {
            ...this.blankLine(l.subjectAreaId ?? null),
            courseId: l.courseId ?? null,
            subpartId: l.subpartId ?? null,
            classId: l.classId ?? null,
          };
          if (line.subjectAreaId != null) this.fetchCourses(line, true);
          if (line.courseId != null) this.fetchSubparts(line, true);
          if (line.subpartId != null) this.fetchClasses(line, true);
          return line;
        }));
        this.dialogVisible.set(true);
      },
      error: (e: ApiError) => this.messages.add({ severity: 'error', summary: 'Load failed', detail: e.message }),
    });
  }

  addLine(): void {
    const current = this.editLines();
    const last = current.length ? current[current.length - 1] : null;
    const line = this.blankLine(last?.subjectAreaId ?? this.defaultSubject());
    // Mirror the previous line's course for faster entry (e.g. Lec 1 then Lab All).
    if (last && last.courseId != null && line.subjectAreaId === last.subjectAreaId) {
      line.courseOptions = last.courseOptions;
      line.courseId = last.courseId;
      line.subpartOptions = last.subpartOptions;
    } else if (line.subjectAreaId != null) {
      this.fetchCourses(line);
    }
    this.editLines.set([...current, line]);
  }

  removeLine(i: number): void {
    const remaining = this.editLines().filter((_, idx) => idx !== i);
    this.editLines.set(remaining.length ? remaining : [this.blankLine(this.defaultSubject())]);
  }

  moveLine(i: number, delta: number): void {
    const j = i + delta;
    const copy = [...this.editLines()];
    if (j < 0 || j >= copy.length) return;
    [copy[i], copy[j]] = [copy[j], copy[i]];
    this.editLines.set(copy);
  }

  onTypeChange(): void {
    if (!this.editing) return;
    const options = this.levelOptions();
    const allowedIds = new Set(options.map((o) => o.value));
    if (!allowedIds.has(this.editing.prefLevelId)) this.editing.prefLevelId = null;
    // Legacy: when only one level is allowed (e.g. required-only types), select it.
    if (options.length === 1) this.editing.prefLevelId = options[0].value;
  }

  onLineSubjectChange(line: EditLine): void {
    line.courseId = null;
    line.subpartId = null;
    line.classId = null;
    line.courseOptions = [];
    line.subpartOptions = [];
    line.classOptions = [];
    if (line.subjectAreaId != null) this.fetchCourses(line);
  }

  onLineCourseChange(line: EditLine): void {
    line.subpartId = null;
    line.classId = null;
    line.subpartOptions = [];
    line.classOptions = [];
    if (line.courseId != null) this.fetchSubparts(line);
  }

  onLineSubpartChange(line: EditLine): void {
    line.classId = null;
    line.classOptions = [];
    if (line.subpartId != null) this.fetchClasses(line);
  }

  private lookup(operation: Operation, lookupId: number, next: (options: Option[]) => void): void {
    const request: DistributionPrefEditRequest = { operation, lookupId };
    this.rpc.execute<DistributionPrefEditResponse>('DistributionPrefEditRequest', request).subscribe({
      next: (d) => {
        next((d.options ?? []).map((o) => ({ label: o.label ?? '', value: o.id ?? null })));
        this.refreshLines();
      },
      error: (e: ApiError) => this.messages.add({ severity: 'error', summary: 'Load failed', detail: e.message }),
    });
  }

  /** Re-emit the lines array so the template picks up in-place (async) mutations. */
  private refreshLines(): void {
    this.editLines.set([...this.editLines()]);
  }

  /** keep = true when restoring an existing line (do not auto-select). */
  private fetchCourses(line: EditLine, keep = false): void {
    this.lookup('COURSES', line.subjectAreaId!, (options) => {
      line.courseOptions = options;
      if (!keep && options.length === 1 && line.courseId == null) {
        line.courseId = options[0].value;
        this.fetchSubparts(line);
      }
    });
  }

  private fetchSubparts(line: EditLine, keep = false): void {
    this.lookup('SUBPARTS', line.courseId!, (options) => {
      line.subpartOptions = options;
      if (!keep && options.length === 1 && line.subpartId == null) {
        line.subpartId = options[0].value;
        this.fetchClasses(line);
      }
    });
  }

  private fetchClasses(line: EditLine, keep = false): void {
    this.lookup('CLASSES', line.subpartId!, (options) => {
      line.classOptions = options;
      // Legacy: a freshly picked subpart defaults to "All" (-1).
      if (!keep && line.classId == null && options.length) line.classId = -1;
    });
  }

  cancel(): void {
    this.dialogVisible.set(false);
    this.editing = null;
  }

  submit(): void {
    const e = this.editing;
    if (!e) return;
    if (e.distributionTypeId == null) {
      this.messages.add({ severity: 'error', summary: 'Validation', detail: 'Distribution type is required.' });
      return;
    }
    if (e.prefLevelId == null) {
      this.messages.add({ severity: 'error', summary: 'Validation', detail: 'Preference level is required.' });
      return;
    }
    const record: DistributionPrefRecord = {
      id: e.id,
      distributionTypeId: e.distributionTypeId,
      prefLevelId: e.prefLevelId,
    };
    if (e.linesEditable) {
      const lines: ClassLine[] = this.editLines()
        .filter((l) => l.subjectAreaId != null || l.courseId != null || l.subpartId != null)
        .map((l) => ({
          subjectAreaId: l.subjectAreaId,
          courseId: l.courseId,
          subpartId: l.subpartId,
          classId: l.classId == null ? -1 : l.classId,
        }));
      if (lines.some((l) => l.subpartId == null)) {
        this.messages.add({ severity: 'error', summary: 'Validation', detail: 'Select a subpart (and a class or All) on every line.' });
        return;
      }
      if (lines.length === 0 || (lines.length === 1 && lines[0].classId !== -1)) {
        this.messages.add({
          severity: 'error',
          summary: 'Validation',
          detail: 'Select at least two classes, or one subpart with All classes.',
        });
        return;
      }
      record.structure = e.structure ?? 'AllClasses';
      record.lines = lines;
    }
    this.saving.set(true);
    const request: DistributionPrefEditRequest = { operation: 'SAVE', record };
    if (this.subjectAreaId != null) request.subjectAreaId = this.subjectAreaId;
    this.rpc.execute<DistributionPrefEditResponse>('DistributionPrefEditRequest', request).subscribe({
      next: (d) => {
        this.applyResponse(d);
        this.saving.set(false);
        this.dialogVisible.set(false);
        this.editing = null;
        this.messages.add({
          severity: 'success',
          summary: 'Saved',
          detail: e.id == null ? 'Distribution preference created.' : 'Distribution preference updated.',
        });
      },
      error: (err: ApiError) => {
        this.saving.set(false);
        this.messages.add({ severity: 'error', summary: 'Save failed', detail: err.message });
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
