import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { TextareaModule } from 'primeng/textarea';
import { SelectModule } from 'primeng/select';
import { MultiSelectModule } from 'primeng/multiselect';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';
import {
  ExamAddRequest,
  ExamAddResponse,
  ExamEditRequest,
  ExamEditResponse,
  ExamEditUpdateRequest,
  ExamTypeInfo,
  IdName,
} from './exam-edit.models';

/**
 * Edit Examination (legacy examEdit.action).
 *
 * Edit mode (/exam-edit/:id): read + save of the exam's scalar fields (name, note,
 * length, seating, size, print offset, max rooms) via ExamEditRequest /
 * ExamEditUpdateRequest.
 *
 * Add mode (/exam-edit/new?firstType=CourseOffering&firstId=<id>): mirrors the legacy
 * add page reached with firstType / firstId (CourseOffering, InstructionalOffering,
 * InstrOfferingConfig, SchedulingSubpart, Class_). Backed by ExamAddRequest (LOAD for
 * defaults / types / instructors, SAVE to create the Exam + ExamOwner); navigates to
 * the new exam's detail page. Period / room preferences keep the defaults.
 */
@Component({
  selector: 'app-exam-edit',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    ButtonModule,
    InputTextModule,
    InputNumberModule,
    TextareaModule,
    SelectModule,
    MultiSelectModule,
    MessageModule,
    ProgressSpinnerModule,
  ],
  templateUrl: './exam-edit.html',
})
export class ExamEdit implements OnInit {
  private fb = inject(FormBuilder);
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private router = inject(Router);
  private messages = inject(MessageService);

  readonly id = input<string>();
  /** Add mode: legacy owner type (CourseOffering, Class_, ...). */
  readonly firstType = input<string>();
  /** Add mode: owner unique id. */
  readonly firstId = input<string>();

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly data = signal<ExamEditResponse | null>(null);
  protected readonly seatingOptions = signal<IdName[]>([]);

  /** Add mode state. */
  protected readonly createMode = computed(() => this.id() === 'new');
  protected readonly addData = signal<ExamAddResponse | null>(null);
  protected readonly examTypes = signal<ExamTypeInfo[]>([]);
  protected readonly typeOptions = computed(() =>
    this.examTypes().map((t) => ({ name: t.label ?? '', id: t.id ?? null })),
  );
  protected readonly instructorOptions = computed(() =>
    (this.addData()?.instructors ?? []).map((i) => ({ name: i.label ?? '', id: i.id ?? null })),
  );

  protected readonly form = this.fb.group({
    examTypeId: [null as number | null],
    name: [''],
    length: [null as number | null, [Validators.required, Validators.min(1)]],
    seatingType: [0 as number],
    examSize: [null as number | null],
    printOffset: [null as number | null],
    maxNbrRooms: [null as number | null],
    note: [''],
    instructorIds: [[] as number[]],
  });

  ngOnInit(): void {
    if (this.createMode()) {
      this.initCreate();
      return;
    }
    this.page.set('Edit Examination');
    const eid = this.id() == null ? NaN : Number(this.id());
    if (!Number.isFinite(eid)) {
      this.error.set('No examination was specified.');
      this.loading.set(false);
      return;
    }
    const request: ExamEditRequest = { examId: eid };
    this.rpc.execute<ExamEditResponse>('ExamEditRequest', request).subscribe({
      next: (d) => this.apply(d),
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  // ---------------------------------------------------------------- add mode

  private initCreate(): void {
    this.page.set('Add Examination');
    const firstType = this.firstType();
    const firstId = this.firstId() == null ? NaN : Number(this.firstId());
    if (!firstType || !Number.isFinite(firstId)) {
      this.error.set('No class or course was specified for the new examination.');
      this.loading.set(false);
      return;
    }
    const request: ExamAddRequest = { operation: 'LOAD', firstType, firstId };
    this.rpc.execute<ExamAddResponse>('ExamAddRequest', request).subscribe({
      next: (d) => {
        this.addData.set(d);
        this.examTypes.set(d.examTypes ?? []);
        this.seatingOptions.set((d.seatingOptions ?? []).map((s) => ({ id: s.id, name: s.label })));
        this.form.patchValue({
          examTypeId: d.examTypeId ?? null,
          name: '',
          length: d.length ?? 120,
          seatingType: d.seatingType ?? 1,
          examSize: null,
          printOffset: null,
          maxNbrRooms: d.maxNbrRooms ?? 4,
          note: '',
          instructorIds: [],
        });
        this.form.controls.examTypeId.addValidators(Validators.required);
        this.form.controls.examTypeId.updateValueAndValidity();
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  /** When the exam type changes, default the length from that type's first period (as legacy). */
  onExamTypeChange(): void {
    const typeId = this.form.controls.examTypeId.value;
    const t = this.examTypes().find((x) => x.id === typeId);
    if (t?.defaultLength) this.form.controls.length.setValue(t.defaultLength);
  }

  private create(): void {
    const v = this.form.getRawValue();
    this.saving.set(true);
    const request: ExamAddRequest = {
      operation: 'SAVE',
      firstType: this.firstType(),
      firstId: Number(this.firstId()),
      examTypeId: v.examTypeId,
      name: v.name ?? '',
      length: v.length,
      seatingType: v.seatingType ?? 1,
      examSize: v.examSize,
      printOffset: v.printOffset,
      maxNbrRooms: v.maxNbrRooms,
      note: v.note ?? '',
      instructorIds: v.instructorIds ?? [],
    };
    this.rpc.execute<ExamAddResponse>('ExamAddRequest', request).subscribe({
      next: (d) => {
        this.saving.set(false);
        this.messages.add({ severity: 'success', summary: 'Saved', detail: 'Examination created.' });
        if (d.examId != null) this.router.navigate(['/exam-detail', d.examId]);
        else this.router.navigate(['/examinations-list']);
      },
      error: (e: ApiError) => {
        this.saving.set(false);
        this.messages.add({ severity: 'error', summary: 'Save failed', detail: e.message });
      },
    });
  }

  cancelCreate(): void {
    history.back();
  }

  // --------------------------------------------------------------- edit mode

  private apply(d: ExamEditResponse): void {
    this.data.set(d);
    this.seatingOptions.set(d.seatingOptions ?? []);
    if (d.label) this.page.set('Edit ' + d.label);
    this.form.patchValue({
      name: d.name ?? '',
      length: d.length ?? null,
      seatingType: d.seatingType ?? 0,
      examSize: d.examSize ?? null,
      printOffset: d.printOffset ?? null,
      maxNbrRooms: d.maxNbrRooms ?? null,
      note: d.note ?? '',
    });
    this.loading.set(false);
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (this.createMode()) {
      this.create();
      return;
    }
    const v = this.form.getRawValue();
    this.saving.set(true);
    const request: ExamEditUpdateRequest = {
      examId: Number(this.id()),
      name: v.name ?? '',
      length: v.length,
      seatingType: v.seatingType ?? 0,
      examSize: v.examSize,
      printOffset: v.printOffset,
      maxNbrRooms: v.maxNbrRooms,
      note: v.note ?? '',
    };
    this.rpc.execute<ExamEditResponse>('ExamEditUpdateRequest', request).subscribe({
      next: (d) => {
        this.saving.set(false);
        this.messages.add({ severity: 'success', summary: 'Saved', detail: 'Examination updated.' });
        this.router.navigate(['/exam-detail', d.examId ?? Number(this.id())]);
      },
      error: (e: ApiError) => {
        this.saving.set(false);
        this.messages.add({ severity: 'error', summary: 'Save failed', detail: e.message });
      },
    });
  }
}
