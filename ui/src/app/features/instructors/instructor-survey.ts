import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { SelectModule } from 'primeng/select';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import {
  AcademicSessionInfo,
  ApiError,
  Course,
  CustomField,
  InstructorSurveyData,
  Preferences,
  Selection,
} from '../../core/models';

/** One editable preference selection line (item + level + optional note). */
interface PrefRow {
  item: number | null;
  level: number | null;
  note: string;
}

/** A preference block (a room-preference category or the distribution block). */
interface PrefGroup {
  id: number;
  type: string;
  isDist: boolean;
  items: { value: number; label: string }[];
  rows: PrefRow[];
}

/** One editable course-requirement line. */
interface CourseRow {
  courseName: string;
  customs: Record<number, string>;
}

/**
 * Instructor Survey (GWT command pattern: InstructorSurveyRequest -> InstructorSurveyData,
 * InstructorSurveySaveRequest -> InstructorSurveyData).
 *
 * Loads the survey for the current user (optionally for a chosen academic session),
 * displays the read-only identity block (name, external id, departments, submitted
 * date), and provides the FUNCTIONAL EDITING CORE:
 *   - contact email + "other preferences" free-text note
 *   - room/distribution preference blocks: per-line item + preference-level + reason
 *   - course-requirement rows with per-course custom fields
 *   - Save / Submit / Unsubmit actions
 *
 * Deferred (see notes): the weekly time-preference grid (RoomSharingWidget), the
 * course autocomplete/validation + offering-detail popups (ListCourseOfferings /
 * RetrieveCourseDetail), the cross-session Copy menu (InstructorSurveyCopyRequest),
 * hard-preference reason hints, and per-item description HTML.
 */
@Component({
  selector: 'app-instructor-survey',
  imports: [
    FormsModule,
    ButtonModule,
    InputTextModule,
    TextareaModule,
    SelectModule,
    MessageModule,
    ProgressSpinnerModule,
  ],
  templateUrl: './instructor-survey.html',
  styles: [
    `
      .is-page { max-width: 1100px; margin: 0 auto; padding: 0.5rem 1rem; }
      .is-toolbar { display: flex; gap: 0.5rem; align-items: center; flex-wrap: wrap; margin-bottom: 1rem; }
      .is-toolbar .spacer { flex: 1; }
      .is-title { font-size: 1.2rem; font-weight: 600; }
      .is-section { font-size: 1.05rem; font-weight: 600; margin: 1.25rem 0 0.5rem; border-bottom: 1px solid var(--p-content-border-color, #ddd); padding-bottom: 0.25rem; }
      .is-field { display: flex; gap: 0.5rem; align-items: baseline; margin: 0.35rem 0; }
      .is-field > label { min-width: 150px; font-weight: 600; }
      .is-pref-line { display: flex; gap: 0.5rem; align-items: center; margin: 0.3rem 0; flex-wrap: wrap; }
      .is-pref-line .note { flex: 1; min-width: 220px; }
      .full { width: 100%; box-sizing: border-box; }
      table.is-courses { border-collapse: collapse; width: 100%; }
      table.is-courses th, table.is-courses td { border: 1px solid var(--p-content-border-color, #ddd); padding: 0.35rem 0.5rem; text-align: left; vertical-align: top; }
      .hint { color: var(--p-text-muted-color, #777); }
      .center { display: flex; justify-content: center; padding: 2rem; }
    `,
  ],
})
export class InstructorSurvey implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);

  protected readonly loading = signal(false);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly info = signal<string | null>(null);

  protected readonly survey = signal<InstructorSurveyData | null>(null);
  protected readonly prefGroups = signal<PrefGroup[]>([]);
  protected readonly courseRows = signal<CourseRow[]>([]);

  protected email = '';
  protected note = '';
  protected sessionId: number | null = null;

  protected readonly editable = computed(() => this.survey()?.editable ?? false);
  protected readonly prefLevelOptions = computed(() =>
    (this.survey()?.prefLevels ?? []).map((p) => ({ value: p.id!, label: p.label ?? p.title ?? '' })),
  );

  get sessions(): AcademicSessionInfo[] {
    return this.survey()?.sessions ?? [];
  }
  get customFields(): CustomField[] {
    return this.survey()?.customFields ?? [];
  }
  get departmentLabels(): string[] {
    return (this.survey()?.departments ?? []).map(
      (d) => (d.label ?? '') + (d.position?.label ? ` (${d.position.label})` : ''),
    );
  }
  get canUnsubmit(): boolean {
    const s = this.survey();
    return !!(s && s.editable && s.admin && s.submitted);
  }

  ngOnInit(): void {
    this.page.set('Instructor Survey');
    this.load(null);
  }

  private load(session: string | null): void {
    this.loading.set(true);
    this.error.set(null);
    this.info.set(null);
    this.rpc
      .execute<InstructorSurveyData>('InstructorSurveyRequest', { externalId: '', session: session ?? undefined })
      .subscribe({
        next: (data) => {
          this.setValue(data);
          this.loading.set(false);
        },
        error: (e: ApiError) => {
          this.error.set(e.message);
          this.loading.set(false);
        },
      });
  }

  onSessionChange(): void {
    if (this.sessionId != null) this.load(String(this.sessionId));
  }

  private setValue(data: InstructorSurveyData): void {
    this.survey.set(data);
    this.sessionId = data.sessionId ?? null;
    this.email = data.email ?? '';
    this.note = data.note ?? '';

    const groups: PrefGroup[] = [];
    for (const p of data.roomPrefs ?? []) groups.push(this.toGroup(p, false));
    if (data.distPrefs) groups.push(this.toGroup(data.distPrefs, true));
    this.prefGroups.set(groups);

    const rows: CourseRow[] = [];
    for (const c of data.courses ?? []) {
      rows.push({ courseName: c.courseName ?? '', customs: { ...(c.customs ?? {}) } as Record<number, string> });
    }
    // Give the editor a couple of blank rows to add course requirements.
    if (data.editable) for (let i = 0; i < 2; i++) rows.push({ courseName: '', customs: {} });
    this.courseRows.set(rows);
  }

  private toGroup(p: Preferences, isDist: boolean): PrefGroup {
    const rows: PrefRow[] = (p.selections ?? [])
      .filter((s) => s.item != null && s.level != null)
      .map((s) => ({ item: s.item ?? null, level: s.level ?? null, note: s.note ?? '' }));
    return {
      id: p.id ?? 0,
      type: p.type ?? '',
      isDist,
      items: (p.items ?? []).map((it) => ({ value: it.id!, label: it.label ?? '' })),
      rows,
    };
  }

  addPrefRow(g: PrefGroup): void {
    g.rows.push({ item: null, level: null, note: '' });
    this.prefGroups.set([...this.prefGroups()]);
  }
  removePrefRow(g: PrefGroup, i: number): void {
    g.rows.splice(i, 1);
    this.prefGroups.set([...this.prefGroups()]);
  }
  addCourseRow(): void {
    this.courseRows.set([...this.courseRows(), { courseName: '', customs: {} }]);
  }

  customValue(row: CourseRow, id: number): string {
    return row.customs[id] ?? '';
  }
  setCustomValue(row: CourseRow, id: number, value: string): void {
    row.customs[id] = value;
  }

  /** Reassemble an InstructorSurveyData for the save/submit RPCs from the editor state. */
  private buildData(): InstructorSurveyData {
    const base = this.survey()!;
    const roomPrefs: Preferences[] = [];
    let distPrefs: Preferences | undefined;
    for (const g of this.prefGroups()) {
      const selections: Selection[] = g.rows
        .filter((r) => r.item != null && r.level != null)
        .map((r) => ({ item: r.item!, level: r.level!, note: r.note || undefined }));
      const original = g.isDist ? base.distPrefs : (base.roomPrefs ?? []).find((p) => p.id === g.id);
      const pref: Preferences = { id: g.id, type: g.type, items: original?.items, selections };
      if (g.isDist) distPrefs = pref;
      else roomPrefs.push(pref);
    }

    const courses: Course[] = [];
    for (const r of this.courseRows()) {
      const customs: Record<number, string> = {};
      for (const [k, v] of Object.entries(r.customs)) if (v) customs[Number(k)] = v;
      const hasCustoms = Object.keys(customs).length > 0;
      if (r.courseName.trim() || hasCustoms) {
        courses.push({ courseName: r.courseName.trim(), customs: hasCustoms ? customs : undefined });
      }
    }

    return {
      ...base,
      email: this.email,
      note: this.note,
      roomPrefs: roomPrefs.length ? roomPrefs : base.roomPrefs,
      distPrefs: distPrefs ?? base.distPrefs,
      courses,
    };
  }

  save(): void {
    this.run({ data: this.buildData(), submit: false });
  }

  submit(): void {
    if (!window.confirm('Submit your instructor survey? You may no longer be able to edit it afterwards.')) return;
    this.run({ data: this.buildData(), submit: true });
  }

  unsubmit(): void {
    if (!window.confirm('Unsubmit this instructor survey?')) return;
    this.run({ data: this.buildData(), submit: false, unsubmit: true });
  }

  private run(request: { data: InstructorSurveyData; submit: boolean; unsubmit?: boolean }): void {
    this.saving.set(true);
    this.error.set(null);
    this.info.set(null);
    this.rpc.execute<InstructorSurveyData>('InstructorSurveySaveRequest', request).subscribe({
      next: (data) => {
        this.setValue(data);
        this.info.set(data.popupMessage || 'Instructor survey updated.');
        this.saving.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.saving.set(false);
      },
    });
  }
}
