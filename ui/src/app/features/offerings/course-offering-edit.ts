import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { TextareaModule } from 'primeng/textarea';
import { CheckboxModule } from 'primeng/checkbox';
import { SelectModule } from 'primeng/select';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import {
  ApiError,
  CourseOfferingInterface,
  CourseOfferingPropertiesInterface,
  CourseOfferingPropertiesRequest,
  GetCourseOfferingRequest,
  GetCourseOfferingResponse,
  UpdateCourseOfferingRequest,
} from '../../core/models';

/**
 * Edit Course Offering — flagship Wave 4 (command-pattern) screen, reached by id
 * (/course-offering/:id) from the legacy offering search/detail during
 * coexistence. Loads the full CourseOfferingInterface, edits the core
 * descriptive/control fields, and sends the WHOLE object back on save so the
 * many interdependent fields it doesn't render are preserved. Dropdowns come
 * from CourseOfferingPropertiesRequest.
 *
 * Deferred: subject-area move, cross-listing, coordinators, and the credit
 * configuration wizard (shown read-only here).
 */
@Component({
  selector: 'app-course-offering-edit',
  imports: [
    ReactiveFormsModule,
    CardModule,
    ButtonModule,
    InputTextModule,
    InputNumberModule,
    TextareaModule,
    CheckboxModule,
    SelectModule,
    MessageModule,
    ProgressSpinnerModule,
  ],
  templateUrl: './course-offering-edit.html',
})
export class CourseOfferingEdit {
  private fb = inject(FormBuilder);
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private messages = inject(MessageService);
  private router = inject(Router);

  /** course offering id, from the route */
  readonly id = input.required<string>();

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly props = signal<CourseOfferingPropertiesInterface | null>(null);
  /** the full loaded offering; edited fields are merged onto this on save */
  private loaded: CourseOfferingInterface | null = null;
  protected readonly courseLabel = signal('');

  protected readonly form = this.fb.group({
    courseNbr: ['', [Validators.required, Validators.maxLength(40)]],
    title: [''],
    courseTypeId: [null as number | null],
    consent: [null as number | null],
    nbrExpectedStudents: [null as number | null],
    byReservationOnly: [false],
    scheduleBookNote: [''],
    notes: [''],
  });

  protected readonly subjectArea = computed(() => this.loaded?.subjectAreaAbbv ?? '');
  protected readonly creditText = computed(() => this.loaded?.creditText ?? '');

  constructor() {
    effect(() => {
      const id = this.id();
      if (id) this.load(Number(id));
    });
  }

  private load(courseOfferingId: number): void {
    this.loading.set(true);
    this.error.set(null);
    this.page.set('Edit Course Offering');

    const propsReq: CourseOfferingPropertiesRequest = { isEdit: true, courseOfferingId };
    this.rpc
      .execute<CourseOfferingPropertiesInterface>('CourseOfferingPropertiesRequest', propsReq)
      .subscribe({ next: (p) => this.props.set(p), error: () => this.props.set(null) });

    const req: GetCourseOfferingRequest = { courseOfferingId };
    this.rpc.execute<GetCourseOfferingResponse>('GetCourseOfferingRequest', req).subscribe({
      next: (res) => {
        const co = res.courseOffering ?? {};
        this.loaded = co;
        this.courseLabel.set(`${co.subjectAreaAbbv ?? ''} ${co.courseNbr ?? ''}`.trim());
        this.form.reset({
          courseNbr: co.courseNbr ?? '',
          title: co.title ?? '',
          courseTypeId: co.courseTypeId ?? null,
          consent: co.consent ?? null,
          nbrExpectedStudents: co.nbrExpectedStudents ?? null,
          byReservationOnly: co.byReservationOnly ?? false,
          scheduleBookNote: co.scheduleBookNote ?? '',
          notes: co.notes ?? '',
        });
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  save(): void {
    if (this.form.invalid || !this.loaded) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    // Merge edited fields onto the full loaded object; everything else is preserved.
    const courseOffering: CourseOfferingInterface = {
      ...this.loaded,
      courseNbr: v.courseNbr ?? '',
      title: v.title || undefined,
      courseTypeId: v.courseTypeId ?? undefined,
      consent: v.consent ?? undefined,
      nbrExpectedStudents: v.nbrExpectedStudents ?? undefined,
      byReservationOnly: !!v.byReservationOnly,
      scheduleBookNote: v.scheduleBookNote || undefined,
      notes: v.notes || undefined,
    };
    const request: UpdateCourseOfferingRequest = { action: 'UPDATE', courseOffering };

    this.saving.set(true);
    this.rpc.execute<CourseOfferingInterface>('UpdateCourseOfferingRequest', request).subscribe({
      next: (res) => {
        this.saving.set(false);
        this.messages.add({ severity: 'success', summary: 'Course offering saved', detail: res.label });
      },
      error: (e: ApiError) => {
        this.saving.set(false);
        this.messages.add({ severity: 'error', summary: 'Save failed', detail: e.message });
      },
    });
  }

  back(): void {
    this.router.navigate(['/home']);
  }
}
