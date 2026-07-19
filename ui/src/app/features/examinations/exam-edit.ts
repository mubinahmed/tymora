import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { TextareaModule } from 'primeng/textarea';
import { SelectModule } from 'primeng/select';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';
import { ExamEditRequest, ExamEditResponse, ExamEditUpdateRequest, IdName } from './exam-edit.models';

/**
 * Edit Examination (legacy examEdit.action) — read + save of the exam's scalar fields
 * (name, note, length, seating, size, print offset, max rooms). Exam owners, preferences
 * and instructor assignment keep their own screens. Reached by exam id from Exam Detail.
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

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly data = signal<ExamEditResponse | null>(null);
  protected readonly seatingOptions = signal<IdName[]>([]);

  protected readonly form = this.fb.group({
    name: [''],
    length: [null as number | null, [Validators.required, Validators.min(1)]],
    seatingType: [0 as number],
    examSize: [null as number | null],
    printOffset: [null as number | null],
    maxNbrRooms: [null as number | null],
    note: [''],
  });

  ngOnInit(): void {
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
