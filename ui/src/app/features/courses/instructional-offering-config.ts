import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { SelectModule } from 'primeng/select';
import { CheckboxModule } from 'primeng/checkbox';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ConfirmationService, MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';
import {
  InstrOfferingConfigInterface,
  InstrOfferingConfigInterface_Reference as Reference,
  SubpartLine,
} from '../../core/generated/models.generated';

/** Parent dropdown option (a subpart row of this configuration). */
interface ParentOption {
  id: number;
  label: string;
}

/**
 * Instructional Offering Config (legacy instructionalOfferingConfigEdit.action) — the
 * configuration structure editor, reusing the existing GWT InstrOfferingConfigBackend
 * unchanged (operation LOAD/SAVE/DELETE on `InstrOfferingConfigInterface`). Edits the
 * config's scheduling subparts (instructional type, parent subpart, number of classes,
 * minutes/week, limits, rooms, department) and the config name / limit / unlimited flag /
 * instructional method / duration type.
 *
 * Routes:
 *  - /instructional-offering-config/:configId — edit an existing configuration;
 *  - /instructional-offering-config/new?offering=<instrOfferingId> — add a configuration
 *    (LOAD/SAVE without a configId, gated by Right.InstrOfferingConfigAdd on the offering).
 *    The backend creates the InstrOfferingConfig, its SchedulingSubparts and Class_ objects
 *    exactly like the GWT page did (section numbers via computeLabels, default date pattern,
 *    managing department = the line's department or the offering's department).
 *
 * New subpart rows get negative temporary ids (the backend treats id < 0 as new) and the
 * rows are sent parent-first in hierarchy order, as the backend resolves parents in order.
 * Validation mirrors InstrOfferingConfigPage.validate() of the GWT client.
 */
@Component({
  selector: 'app-instructional-offering-config',
  imports: [
    FormsModule,
    RouterLink,
    ButtonModule,
    InputTextModule,
    InputNumberModule,
    SelectModule,
    CheckboxModule,
    TagModule,
    MessageModule,
    ProgressSpinnerModule,
    ConfirmDialogModule,
  ],
  providers: [ConfirmationService],
  templateUrl: './instructional-offering-config.html',
})
export class InstructionalOfferingConfig implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private messages = inject(MessageService);
  private confirm = inject(ConfirmationService);
  private router = inject(Router);

  /** InstrOfferingConfig id, or 'new' */
  readonly id = input<string>();
  /** create mode: instructional offering id (query param) */
  readonly offering = input<string>();

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly errors = signal<string[]>([]);
  protected readonly data = signal<InstrOfferingConfigInterface | null>(null);
  protected readonly createMode = signal(false);

  protected readonly offeringId = computed(() => this.data()?.offeringId ?? null);
  /** controlling course offering id — the key of /offering-detail/:id */
  protected readonly courseId = computed(() => this.data()?.courseId ?? null);
  protected readonly lines = computed<SubpartLine[]>(() => this.data()?.subpartLines ?? []);
  protected readonly itypeOptions = computed<Reference[]>(() => this.data()?.instructionalTypes ?? []);
  protected readonly deptOptions = computed<Reference[]>(() => this.data()?.departments ?? []);
  protected readonly imOptions = computed<Reference[]>(() => this.data()?.instructionalMethods ?? []);
  protected readonly dtOptions = computed<Reference[]>(() => this.data()?.durationTypes ?? []);

  /** parent dropdown options per row (keyed by subpart id), recomputed when the rows change */
  protected readonly parentChoices = computed(() => {
    const map = new Map<number, ParentOption[]>();
    for (const line of this.lines()) if (line.subpartId != null) map.set(line.subpartId, this.parentOptions(line));
    return map;
  });

  /** temporary (negative) ids for new subpart rows */
  private lastTmpId = 0;

  ngOnInit(): void {
    this.page.set('Instructional Offering Configuration');
    const cid = this.id();
    if (cid === 'new') {
      const oid = this.offering();
      if (oid == null || !Number.isFinite(Number(oid))) {
        this.error.set('No instructional offering was specified.');
        this.loading.set(false);
        return;
      }
      this.createMode.set(true);
      this.load({ operation: 'LOAD', offeringId: Number(oid) });
      return;
    }
    if (cid == null || !Number.isFinite(Number(cid))) {
      this.error.set('No instructional offering configuration was specified.');
      this.loading.set(false);
      return;
    }
    this.load({ operation: 'LOAD', configId: Number(cid) });
  }

  private load(request: InstrOfferingConfigInterface): void {
    this.loading.set(true);
    this.errors.set([]);
    this.rpc.execute<InstrOfferingConfigInterface>('InstrOfferingConfigInterface', request).subscribe({
      next: (d) => {
        if (d.unlimited == null) d.unlimited = false;
        if (d.limit == null) d.limit = 0;
        if (!d.subpartLines) d.subpartLines = [];
        this.lastTmpId = Math.min(0, d.lastGeneratedId ?? 0, ...d.subpartLines.map((l) => l.subpartId ?? 0));
        this.data.set(d);
        if (this.createMode()) this.page.set('Add Configuration · ' + (d.courseName ?? ''));
        else if (d.courseName) this.page.set(d.courseName + (d.configName ? ' [' + d.configName + ']' : ''));
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  protected itypeLabel(itype: number | undefined): string {
    return this.itypeOptions().find((r) => r.id === itype)?.label ?? '';
  }

  protected lineLabel(line: SubpartLine): string {
    return this.itypeLabel(line.iType) || line.label || '(subpart)';
  }

  /** Parent choices for a row: any other row that is not this row's descendant (cleared = no parent). */
  protected parentOptions(line: SubpartLine): ParentOption[] {
    const all = this.lines();
    const options: ParentOption[] = [];
    for (const other of all) {
      if (other === line || other.subpartId == null) continue;
      if (this.isDescendant(other, line, all)) continue;
      options.push({ id: other.subpartId, label: this.lineLabel(other) });
    }
    return options;
  }

  /** true when `candidate` is (transitively) a child of `ancestor` */
  private isDescendant(candidate: SubpartLine, ancestor: SubpartLine, all: SubpartLine[]): boolean {
    let p = candidate.parentId;
    const seen = new Set<number>();
    while (p != null && !seen.has(p)) {
      if (p === ancestor.subpartId) return true;
      seen.add(p);
      p = all.find((l) => l.subpartId === p)?.parentId;
    }
    return false;
  }

  private defaultDepartmentId(): number | undefined {
    const d = this.data();
    // departments[0] is the non-selectable "multiple managers" (-1) entry, then the controlling department.
    return d?.departments?.find((r) => r.selectable !== false && (r.id ?? -1) >= 0)?.id;
  }

  addSubpart(): void {
    const d = this.data();
    if (!d) return;
    const lines = d.subpartLines ?? (d.subpartLines = []);
    const template = lines[0];
    const itype = this.itypeOptions().find((r) => r.selectable !== false) ?? this.itypeOptions()[0];
    const limit = d.unlimited ? 0 : (d.limit ?? 0);
    const line: SubpartLine = {
      subpartId: --this.lastTmpId,
      iType: itype?.id,
      numberOfClasses: 1,
      minutesPerWeek: template?.minutesPerWeek ?? 0,
      minClassLimit: limit,
      maxClassLimit: limit,
      numberOfRooms: 1,
      roomRatio: 1,
      departmentId: template?.departmentId != null && template.departmentId >= 0 ? template.departmentId : this.defaultDepartmentId(),
      splitAttendance: false,
      editable: true,
      canDelete: true,
      locked: false,
      indent: 0,
      label: itype?.label ?? '(new subpart)',
    };
    lines.push(line);
    this.refresh();
  }

  deleteSubpart(line: SubpartLine): void {
    const d = this.data();
    if (!d?.subpartLines) return;
    // Children of a removed row are removed with it (as on the GWT page).
    const remove = new Set<SubpartLine>([line]);
    for (const l of d.subpartLines) if (this.isDescendant(l, line, d.subpartLines)) remove.add(l);
    d.subpartLines = d.subpartLines.filter((l) => !remove.has(l));
    this.refresh();
  }

  onParentChange(line: SubpartLine, parentId: number | null | undefined): void {
    line.parentId = parentId ?? undefined;
    this.refresh();
  }

  onItypeChange(line: SubpartLine): void {
    line.label = this.itypeLabel(line.iType) || line.label;
    this.refresh();
  }

  /** Re-order rows parent-first (depth-first), recompute indents and publish the change. */
  protected refresh(): void {
    const d = this.data();
    if (!d) return;
    d.subpartLines = this.hierarchyOrder(d.subpartLines ?? []);
    this.data.set({ ...d });
  }

  private hierarchyOrder(lines: SubpartLine[]): SubpartLine[] {
    const ids = new Set(lines.map((l) => l.subpartId));
    const ordered: SubpartLine[] = [];
    const visit = (parentId: number | undefined, indent: number) => {
      for (const l of lines) {
        const pid = l.parentId != null && ids.has(l.parentId) ? l.parentId : undefined;
        if (pid !== parentId || ordered.includes(l)) continue;
        if (pid === undefined) l.parentId = undefined;
        l.indent = indent;
        ordered.push(l);
        visit(l.subpartId, indent + 1);
      }
    };
    visit(undefined, 0);
    // anything left (should not happen — a cycle) goes to the top level
    for (const l of lines)
      if (!ordered.includes(l)) {
        l.parentId = undefined;
        l.indent = 0;
        ordered.push(l);
      }
    return ordered;
  }

  /** InstrOfferingConfigPage.validate() of the GWT client (plain-text messages). */
  private validate(d: InstrOfferingConfigInterface): string[] {
    const errors: string[] = [];
    const unlimited = !!d.unlimited;
    const lines = d.subpartLines ?? [];
    if (!d.configName || !d.configName.trim()) {
      errors.push('Configuration Name is required.');
    } else {
      for (const c of d.configs ?? [])
        if ((c.label ?? '').toLowerCase() === d.configName.trim().toLowerCase() && c.id !== d.configId)
          errors.push('A configuration with this name already exists in this offering. Use a unique name.');
    }
    if (!unlimited && d.limit == null) errors.push('Configuration Limit is required.');
    if (!lines.length) errors.push('At least one scheduling subpart is required.');
    const lblMax = d.displayOptionForMaxLimit ? 'Max limit' : 'Limit';
    for (const line of lines) {
      line.error = undefined;
      const label = this.lineLabel(line);
      const add = (e: string) => {
        errors.push(e);
        line.error = line.error ? line.error + '\n' + e : e;
      };
      if (!line.editable) continue;
      if (line.iType == null) add(`Instructional type is required.`);
      if (line.numberOfClasses == null || line.numberOfClasses <= 0) add(`Number of classes for ${label} must have a value greater than 0.`);
      else if (d.maxNumberOfClasses != null && line.numberOfClasses > d.maxNumberOfClasses)
        add(`Number of classes for ${label} must be less than or equal to ${d.maxNumberOfClasses}.`);
      if (!unlimited && (line.minClassLimit == null || line.maxClassLimit == null || line.minClassLimit < 0 || line.maxClassLimit < 0))
        add(`${lblMax} per class for ${label} must be greater than or equal to 0.`);
      if (!unlimited && (line.numberOfRooms == null || line.numberOfRooms < 0)) add(`Number of rooms for ${label} must be greater than or equal to 0.`);
      if (line.minutesPerWeek == null || line.minutesPerWeek < 0) add(`Minutes per week for ${label} must be greater than or equal to 0.`);
      else if (!unlimited && line.minutesPerWeek === 0 && (line.numberOfRooms ?? 0) > 0)
        add(`Minutes per week for ${label} can be 0 only if number of rooms is 0.`);
      if (!unlimited && (line.roomRatio == null || line.roomRatio < 0)) add(`Room ratio for ${label} must be greater than or equal to 0.`);

      if (!unlimited && d.checkLimits && d.limit != null && !line.error) {
        const max = line.maxClassLimit ?? 0;
        const n = line.numberOfClasses ?? 0;
        if (n === 1 && max !== d.limit) add(`${lblMax} per class for ${label} must be equal to Configuration limit of ${d.limit}.`);
        if (n > 1 && max * n < d.limit) add(`Sum of class limits ${label} must be greater than or equal to Configuration limit of ${d.limit}.`);
        if (n > 1 && max > d.limit) add(`${lblMax} per class of ${max} for ${label} must be less than or equal to Configuration limit of ${d.limit}.`);
      }

      const parent = line.parentId == null ? undefined : lines.find((l) => l.subpartId === line.parentId);
      if (parent && !line.error && !parent.error) {
        const pn = parent.numberOfClasses ?? 0;
        const n = line.numberOfClasses ?? 0;
        const plabel = this.lineLabel(parent);
        if (pn > 0 && n % pn !== 0) add(`Subparts that are grouped under ${plabel} must have number of classes that is a multiple of ${pn}.`);
        else if (!unlimited && pn > 0) {
          const max = line.maxClassLimit ?? 0;
          const pmax = parent.maxClassLimit ?? 0;
          if (max > pmax) add(`Subparts that are grouped under ${plabel} must have a ${lblMax} per class <= ${lblMax} per class of ${pmax}.`);
          if (n * max < pn * pmax || max * Math.floor(n / pn) < pmax)
            add(`Subparts that are grouped under ${plabel} must not accomodate lesser number of students.`);
        }
      }
    }
    return [...new Set(errors)];
  }

  save(): void {
    const d = this.data();
    if (!d) return;
    d.subpartLines = this.hierarchyOrder(d.subpartLines ?? []);
    for (const line of d.subpartLines) {
      if (line.subpartId == null) line.subpartId = --this.lastTmpId;
      // Without the variable-limits option the single "Limit" column drives both limits.
      if (!d.displayOptionForMaxLimit) line.maxClassLimit = line.minClassLimit;
      if (line.roomRatio == null) line.roomRatio = 1;
      if (line.numberOfRooms == null) line.numberOfRooms = 1;
      if (line.splitAttendance == null) line.splitAttendance = false;
      if (!line.label) line.label = this.lineLabel(line);
    }
    if (d.limit == null && d.unlimited) d.limit = 0;
    if (d.configName) d.configName = d.configName.trim();
    const errors = this.validate(d);
    this.errors.set(errors);
    this.data.set({ ...d });
    if (errors.length) return;

    this.saving.set(true);
    const creating = d.configId == null;
    const payload: InstrOfferingConfigInterface = { ...d, operation: 'SAVE' };
    this.rpc.execute<InstrOfferingConfigInterface>('InstrOfferingConfigInterface', payload).subscribe({
      next: (res) => {
        this.saving.set(false);
        if (creating) {
          this.messages.add({ severity: 'success', summary: 'Created', detail: `Configuration ${d.configName ?? ''} created.` });
          if (d.courseId != null) this.router.navigate(['/offering-detail', d.courseId]);
          else if (res.configId != null) this.router.navigate(['/instructional-offering-config', res.configId]);
          return;
        }
        this.messages.add({ severity: 'success', summary: 'Saved', detail: 'Configuration updated.' });
        this.load({ operation: 'LOAD', configId: res.configId ?? d.configId }); // the SAVE response is minimal (ids only)
      },
      error: (e: ApiError) => {
        this.saving.set(false);
        this.messages.add({ severity: 'error', summary: 'Save failed', detail: e.message });
      },
    });
  }

  askDelete(): void {
    this.confirm.confirm({
      header: 'Delete configuration',
      message: 'Delete this entire configuration and all its subparts and classes?',
      icon: 'pi pi-exclamation-triangle',
      accept: () => this.doDelete(),
    });
  }

  private doDelete(): void {
    const d = this.data();
    if (!d) return;
    this.saving.set(true);
    this.rpc
      .execute<InstrOfferingConfigInterface>('InstrOfferingConfigInterface', {
        operation: 'DELETE',
        configId: d.configId,
        offeringId: d.offeringId,
      })
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.messages.add({ severity: 'success', summary: 'Deleted', detail: 'Configuration deleted.' });
          if (d.courseId != null) this.router.navigate(['/offering-detail', d.courseId]);
          else this.router.navigate(['/offerings']);
        },
        error: (e: ApiError) => {
          this.saving.set(false);
          this.messages.add({ severity: 'error', summary: 'Delete failed', detail: e.message });
        },
      });
  }
}
