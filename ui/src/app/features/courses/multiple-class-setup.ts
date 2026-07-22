import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputNumberModule } from 'primeng/inputnumber';
import { SelectModule } from 'primeng/select';
import { CheckboxModule } from 'primeng/checkbox';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';
import { ClassLine, ClassSetupInterface, Reference, Subpart } from '../../core/generated/models.generated';

/** A subpart with the class lines that belong to it (contiguous in classLines). */
interface SubpartGroup {
  subpart: Subpart;
  lines: ClassLine[];
}

/**
 * Multiple Class Setup (legacy multipleClassSetup.action) — the class-structure editor,
 * reusing the existing GWT ClassSetupBackend unchanged (operation LOAD/SAVE on
 * `ClassSetupInterface`). Edits each subpart's class lines (limits, rooms, date pattern,
 * department, flags) and adds/removes classes. Keyed by InstrOfferingConfig id.
 */
@Component({
  selector: 'app-multiple-class-setup',
  imports: [
    FormsModule,
    RouterLink,
    ButtonModule,
    InputNumberModule,
    SelectModule,
    CheckboxModule,
    TagModule,
    MessageModule,
    ProgressSpinnerModule,
  ],
  templateUrl: './multiple-class-setup.html',
})
export class MultipleClassSetup implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private messages = inject(MessageService);

  /** InstrOfferingConfig id from the route. */
  readonly id = input<string>();

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly data = signal<ClassSetupInterface | null>(null);
  protected readonly groups = signal<SubpartGroup[]>([]);

  protected readonly offeringId = computed(() => this.data()?.offeringId ?? null);
  protected readonly deptOptions = computed<Reference[]>(() => this.data()?.departments ?? []);
  protected readonly dpOptions = computed<Reference[]>(() => [
    { id: -1, label: '— Default —' } as Reference,
    ...(this.data()?.datePatterns ?? []),
  ]);

  ngOnInit(): void {
    this.page.set('Multiple Class Setup');
    const cid = this.id();
    if (cid == null || !Number.isFinite(Number(cid))) {
      this.error.set('No instructional offering configuration was specified.');
      this.loading.set(false);
      return;
    }
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.rpc.execute<ClassSetupInterface>('ClassSetupInterface', { operation: 'LOAD', configId: Number(this.id()) }).subscribe({
      next: (d) => this.apply(d),
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  private apply(d: ClassSetupInterface): void {
    // Normalise inherited date pattern (null) to the -1 default option for the selects.
    for (const l of d.classLines ?? []) if (l.datePatternId == null) l.datePatternId = -1;
    this.data.set(d);
    if (d.name) this.page.set(d.name);
    this.build(d);
    this.loading.set(false);
  }

  private build(d: ClassSetupInterface): void {
    const bySubpart = new Map<number, ClassLine[]>();
    for (const l of d.classLines ?? []) {
      const sid = l.subpartId ?? -1;
      if (!bySubpart.has(sid)) bySubpart.set(sid, []);
      bySubpart.get(sid)!.push(l);
    }
    const groups: SubpartGroup[] = [];
    for (const sp of d.subparts ?? []) {
      groups.push({ subpart: sp, lines: bySubpart.get(sp.id ?? -1) ?? [] });
    }
    this.groups.set(groups);
  }

  addClass(group: SubpartGroup): void {
    const d = this.data();
    if (!d) return;
    const template = group.lines[group.lines.length - 1];
    const line: ClassLine = {
      subpartId: group.subpart.id,
      iType: template?.iType,
      minClassLimit: template?.minClassLimit ?? d.limit ?? 0,
      maxClassLimit: template?.maxClassLimit ?? d.limit ?? 0,
      numberOfRooms: template?.numberOfRooms ?? 1,
      roomRatio: template?.roomRatio ?? 1,
      departmentId: template?.departmentId,
      datePatternId: template?.datePatternId ?? -1,
      splitAttendance: false,
      displayInstructors: true,
      enabledForStudentScheduling: true,
      editable: true,
      editableDatePattern: true,
      canDelete: true,
      indent: template?.indent ?? 0,
      label: '(new class)',
    };
    const lines = d.classLines ?? (d.classLines = []);
    const last = group.lines[group.lines.length - 1];
    const at = last ? lines.indexOf(last) : -1;
    lines.splice(at < 0 ? lines.length : at + 1, 0, line);
    this.build(d);
  }

  deleteClass(group: SubpartGroup, line: ClassLine): void {
    const d = this.data();
    if (!d?.classLines) return;
    const at = d.classLines.indexOf(line);
    if (at >= 0) d.classLines.splice(at, 1);
    this.build(d);
  }

  save(): void {
    const d = this.data();
    if (!d) return;
    this.saving.set(true);
    const payload: ClassSetupInterface = { ...d, operation: 'SAVE' as ClassSetupInterface['operation'] };
    // Restore inherited date pattern: our -1 default maps back to null for the backend.
    for (const l of payload.classLines ?? []) if (l.datePatternId === -1) l.datePatternId = undefined;
    this.rpc.execute<ClassSetupInterface>('ClassSetupInterface', payload).subscribe({
      next: () => {
        this.saving.set(false);
        this.messages.add({ severity: 'success', summary: 'Saved', detail: 'Class setup updated.' });
        // Reload from the server so the view matches persisted state regardless of what
        // the SAVE response returns (the GWT backend may echo the form or nothing).
        this.load();
      },
      error: (e: ApiError) => {
        this.saving.set(false);
        this.messages.add({ severity: 'error', summary: 'Save failed', detail: e.message });
        // Re-normalise so the form stays usable after a failed save.
        for (const l of d.classLines ?? []) if (l.datePatternId == null) l.datePatternId = -1;
      },
    });
  }
}
