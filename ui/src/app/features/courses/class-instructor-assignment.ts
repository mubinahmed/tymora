import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { CheckboxModule } from 'primeng/checkbox';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';
import { ACI, AciRecord, AssignClassInstructorsData, ClassGroup, ListItem } from './class-instructor-assignment.models';

const RPC = {
  load: 'org.unitime.timetable.gwt.shared.AssignClassInstructorsInterface$LoadDataRpcRequest',
  save: 'org.unitime.timetable.gwt.shared.AssignClassInstructorsInterface$SaveDataRpcRequest',
};

/**
 * Class Instructor Assignment (legacy classInstructorAssignment.action) — reuses
 * the existing GWT-RPC AssignClassInstructors backend (Load/SaveDataRpcRequest)
 * unchanged; this is the Angular UI over that generic instructor grid. Keyed by
 * InstrOfferingConfig id (reached per-config from the offering detail).
 */
@Component({
  selector: 'app-class-instructor-assignment',
  imports: [
    FormsModule,
    RouterLink,
    ButtonModule,
    InputTextModule,
    SelectModule,
    CheckboxModule,
    MessageModule,
    ProgressSpinnerModule,
  ],
  templateUrl: './class-instructor-assignment.html',
})
export class ClassInstructorAssignment implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private messages = inject(MessageService);

  /** InstrOfferingConfig id from the route. */
  readonly id = input<string>();
  protected readonly ACI = ACI;

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly data = signal<AssignClassInstructorsData | null>(null);
  protected readonly groups = signal<ClassGroup[]>([]);

  protected readonly offeringId = computed(() => this.data()?.offeringId ?? null);

  ngOnInit(): void {
    this.page.set('Assign Instructors');
    const cid = this.id();
    if (cid == null || !Number.isFinite(Number(cid))) {
      this.error.set('No instructional offering configuration was specified.');
      this.loading.set(false);
      return;
    }
    this.rpc.execute<AssignClassInstructorsData>(RPC.load, { configIdStr: String(cid) }).subscribe({
      next: (d) => this.apply(d),
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  private apply(d: AssignClassInstructorsData): void {
    this.data.set(d);
    if (d.courseName) this.page.set('Assign Instructors — ' + d.courseName);
    this.build(d);
    this.loading.set(false);
  }

  /** Group the flat record list into class blocks (contiguous by CLASS_UID). */
  private build(d: AssignClassInstructorsData): void {
    const groups: ClassGroup[] = [];
    let cur: ClassGroup | null = null;
    for (const r of d.records ?? []) {
      const cuid = (r.values?.[ACI.CLASS_UID] ?? '') as string;
      if (!cur || cur.classUid !== cuid) {
        cur = {
          classUid: cuid,
          nameHtml: (r.values?.[ACI.CLASS_NAME] ?? '') as string,
          timeHtml: (r.values?.[ACI.TIME] ?? '') as string,
          roomHtml: (r.values?.[ACI.ROOM] ?? '') as string,
          head: r,
          records: [],
        };
        groups.push(cur);
      }
      cur.records.push(r);
    }
    this.groups.set(groups);
  }

  // ---- cell helpers -------------------------------------------------------
  val(r: AciRecord, col: number): string {
    return (r.values?.[col] ?? '') as string;
  }
  setVal(r: AciRecord, col: number, v: string | null): void {
    if (!r.values) r.values = [];
    r.values[col] = v ?? '';
  }
  checked(r: AciRecord, col: number): boolean {
    return this.val(r, col) === 'true';
  }
  setToggle(r: AciRecord, col: number, on: boolean): void {
    this.setVal(r, col, on ? 'true' : 'false');
  }
  canEdit(r: AciRecord, col: number): boolean {
    return (this.data()?.editable ?? false) && (r.editable?.[col] ?? false);
  }
  options(col: number): ListItem[] {
    return this.data()?.fields?.[col]?.values ?? [];
  }

  // ---- row mutations ------------------------------------------------------
  addInstructor(group: ClassGroup): void {
    const d = this.data();
    if (!d) return;
    const head = group.head;
    const n = d.fields?.length ?? (head.values?.length ?? 0);
    const values = Array.from({ length: n }, (_, i) => (head.values?.[i] ?? null));
    values[ACI.INSTR] = '';
    values[ACI.PCT_SHARE] = '100';
    values[ACI.LEAD] = 'true';
    values[ACI.RESPONSIBILITY] = (head.values?.[ACI.RESPONSIBILITY] ?? '') as string;
    values[ACI.HAS_ERROR] = 'false';
    values[ACI.IS_FIRST] = 'false';
    const rec: AciRecord = {
      uniqueId: null,
      values,
      editable: Array.from({ length: n }, () => true),
      visible: Array.from({ length: n }, () => true),
      deletable: true,
    };
    // Insert into the flat record list right after this class' last record.
    const recs = d.records ?? (d.records = []);
    const last = group.records[group.records.length - 1];
    const at = recs.indexOf(last);
    recs.splice(at < 0 ? recs.length : at + 1, 0, rec);
    this.build(d);
  }

  deleteInstructor(group: ClassGroup, r: AciRecord): void {
    const d = this.data();
    if (!d?.records) return;
    const at = d.records.indexOf(r);
    if (at >= 0) d.records.splice(at, 1);
    this.build(d);
  }

  save(): void {
    const d = this.data();
    if (!d) return;
    this.saving.set(true);
    this.rpc.execute<AssignClassInstructorsData>(RPC.save, { data: d }).subscribe({
      next: (res) => {
        this.saving.set(false);
        if (res.saveSuccessful === false) {
          this.apply(res);
          this.messages.add({ severity: 'error', summary: 'Not saved', detail: res.errors || 'Please fix the highlighted errors.' });
        } else {
          this.apply(res);
          this.messages.add({ severity: 'success', summary: 'Saved', detail: 'Instructor assignments updated.' });
        }
      },
      error: (e: ApiError) => {
        this.saving.set(false);
        this.messages.add({ severity: 'error', summary: 'Save failed', detail: e.message });
      },
    });
  }
}
