import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { SelectModule } from 'primeng/select';
import { CheckboxModule } from 'primeng/checkbox';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ConfirmationService, MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';
import {
  IdName,
  InstructorEditRequest,
  InstructorEditResponse,
  InstructorSaveRequest,
} from './instructor-edit.models';

/**
 * Add / Edit Instructor (legacy instructorAdd.action + instructorInfoEdit.action) —
 * one screen for both: `/instructor-add` (department picker) and
 * `/instructor-edit/:id`. Backed by InstructorAddInitRequest / InstructorEditRequest
 * (load), InstructorSaveRequest (create/update) and InstructorDeleteRequest (delete).
 */
@Component({
  selector: 'app-instructor-edit',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    ButtonModule,
    InputTextModule,
    TextareaModule,
    SelectModule,
    CheckboxModule,
    MessageModule,
    ProgressSpinnerModule,
    ConfirmDialogModule,
  ],
  providers: [ConfirmationService],
  templateUrl: './instructor-edit.html',
})
export class InstructorEdit implements OnInit {
  private fb = inject(FormBuilder);
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private router = inject(Router);
  private messages = inject(MessageService);
  private confirm = inject(ConfirmationService);

  /** Present only on the edit route; absent = add mode. */
  readonly id = input<string>();
  protected readonly isEdit = computed(() => this.id() != null);

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly data = signal<InstructorEditResponse | null>(null);
  protected readonly positionTypes = signal<IdName[]>([]);
  protected readonly departments = signal<IdName[]>([]);

  protected readonly form = this.fb.group({
    departmentId: [null as number | null],
    fname: [''],
    mname: [''],
    lname: ['', Validators.required],
    title: [''],
    externalId: [''],
    careerAcct: [''],
    email: [''],
    positionTypeId: [null as number | null],
    note: [''],
    ignoreTooFar: [false],
  });

  ngOnInit(): void {
    this.page.set(this.isEdit() ? 'Edit Instructor' : 'Add Instructor');
    if (this.isEdit()) {
      const iid = Number(this.id());
      if (!Number.isFinite(iid)) {
        this.error.set('No instructor was specified.');
        this.loading.set(false);
        return;
      }
      const request: InstructorEditRequest = { instructorId: iid };
      this.rpc.execute<InstructorEditResponse>('InstructorEditRequest', request).subscribe({
        next: (d) => this.apply(d),
        error: (e: ApiError) => this.fail(e),
      });
    } else {
      this.rpc.execute<InstructorEditResponse>('InstructorAddInitRequest', {}).subscribe({
        next: (d) => this.apply(d),
        error: (e: ApiError) => this.fail(e),
      });
    }
  }

  private apply(d: InstructorEditResponse): void {
    this.data.set(d);
    this.positionTypes.set(d.positionTypes ?? []);
    this.departments.set(d.departments ?? []);
    if (this.isEdit()) {
      this.form.patchValue({
        fname: d.fname ?? '',
        mname: d.mname ?? '',
        lname: d.lname ?? '',
        title: d.title ?? '',
        externalId: d.externalId ?? '',
        careerAcct: d.careerAcct ?? '',
        email: d.email ?? '',
        positionTypeId: d.positionTypeId ?? null,
        note: d.note ?? '',
        ignoreTooFar: !!d.ignoreTooFar,
      });
      if (d.lname) this.page.set('Edit ' + d.lname);
    }
    this.loading.set(false);
  }

  private fail(e: ApiError): void {
    this.error.set(e.message);
    this.loading.set(false);
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    if (!this.isEdit() && v.departmentId == null) {
      this.messages.add({ severity: 'warn', summary: 'Department', detail: 'Select a department.' });
      return;
    }
    this.saving.set(true);
    const request: InstructorSaveRequest = {
      instructorId: this.isEdit() ? Number(this.id()) : null,
      departmentId: this.isEdit() ? null : v.departmentId,
      fname: v.fname ?? '',
      mname: v.mname ?? '',
      lname: v.lname ?? '',
      title: v.title ?? '',
      externalId: v.externalId ?? '',
      careerAcct: v.careerAcct ?? '',
      email: v.email ?? '',
      positionTypeId: v.positionTypeId,
      note: v.note ?? '',
      ignoreTooFar: !!v.ignoreTooFar,
    };
    this.rpc.execute<InstructorEditResponse>('InstructorSaveRequest', request).subscribe({
      next: (d) => {
        this.saving.set(false);
        this.messages.add({ severity: 'success', summary: 'Saved', detail: 'Instructor saved.' });
        this.router.navigate(['/instructor-detail', d.instructorId]);
      },
      error: (e: ApiError) => {
        this.saving.set(false);
        this.messages.add({ severity: 'error', summary: 'Save failed', detail: e.message });
      },
    });
  }

  askDelete(): void {
    this.confirm.confirm({
      header: 'Delete instructor',
      message: 'Delete this instructor? This removes it from its classes, exams and assignments.',
      icon: 'pi pi-exclamation-triangle',
      accept: () => this.doDelete(),
    });
  }

  private doDelete(): void {
    this.saving.set(true);
    this.rpc.execute<InstructorEditResponse>('InstructorDeleteRequest', { instructorId: Number(this.id()) }).subscribe({
      next: () => {
        this.saving.set(false);
        this.messages.add({ severity: 'success', summary: 'Deleted', detail: 'Instructor deleted.' });
        this.router.navigate(['/list', 'instructors']);
      },
      error: (e: ApiError) => {
        this.saving.set(false);
        this.messages.add({ severity: 'error', summary: 'Delete failed', detail: e.message });
      },
    });
  }
}
