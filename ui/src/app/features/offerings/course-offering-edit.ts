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
 * Add / Edit Course Offering — flagship Wave 4 (command-pattern) screen, reached by id
 * (/course-offering/:id) from the legacy offering search/detail during
 * coexistence. Loads the full CourseOfferingInterface, edits the core
 * descriptive/control fields, and sends the WHOLE object back on save so the
 * many interdependent fields it doesn't render are preserved. Dropdowns come
 * from CourseOfferingPropertiesRequest.
 *
 * Create mode: /course-offering/new?subjectAreaId=..&courseNbr=.. (from the
 * offerings search "Add Course" button). Sends UpdateCourseOfferingRequest with
 * action CREATE — the same backend the GWT Course Offering Edit page used, which
 * creates the InstructionalOffering + controlling CourseOffering (not offered =
 * false, ChangeLog entry) under Right.AddCourseOffering on the subject area. The
 * course number is checked here like the GWT page did (upper-casing, the
 * tmtbl.courseNumber.pattern regex with its patternInfo message) and again on the
 * server. On success it navigates to /offering-detail/<new course offering id>.
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

  /** course offering id, from the route ('new' = create mode) */
  readonly id = input.required<string>();
  /** create mode: preselected subject area (query param) */
  readonly subjectAreaId = input<string>();
  /** create mode: prefilled course number (query param) */
  readonly courseNbr = input<string>();

  protected readonly createMode = signal(false);
  protected readonly formError = signal<string | null>(null);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly props = signal<CourseOfferingPropertiesInterface | null>(null);
  /** the full loaded offering; edited fields are merged onto this on save */
  private loaded: CourseOfferingInterface | null = null;
  protected readonly courseLabel = signal('');

  protected readonly form = this.fb.group({
    subjectAreaId: [null as number | null],
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
      if (id === 'new') this.initCreate(this.subjectAreaId(), this.courseNbr());
      else if (id) this.load(Number(id));
    });
  }

  /** Create mode: load the dropdowns + course number rules, start with an empty course. */
  private initCreate(subjectAreaId: string | undefined, courseNbr: string | undefined): void {
    this.createMode.set(true);
    this.loading.set(true);
    this.error.set(null);
    this.formError.set(null);
    this.page.set('Add Course Offering');
    this.courseLabel.set('');
    const saId = subjectAreaId != null && subjectAreaId !== '' && Number.isFinite(Number(subjectAreaId)) ? Number(subjectAreaId) : null;
    this.loaded = {
      subjectAreaId: saId ?? undefined,
      overrides: [],
      sendCoordinators: [],
      byReservationOnly: false,
    };
    this.form.reset({
      subjectAreaId: saId,
      courseNbr: courseNbr ?? '',
      title: '',
      courseTypeId: null,
      consent: null,
      nbrExpectedStudents: null,
      byReservationOnly: false,
      scheduleBookNote: '',
      notes: '',
    });
    const propsReq: CourseOfferingPropertiesRequest = { isEdit: false, subjAreaId: saId ?? undefined };
    this.rpc.execute<CourseOfferingPropertiesInterface>('CourseOfferingPropertiesRequest', propsReq).subscribe({
      next: (p) => {
        this.props.set(p);
        // Preselect the only/first subject area the user may add courses to when none came with the link.
        if (this.form.controls.subjectAreaId.value == null && p.subjectAreas?.length === 1)
          this.form.controls.subjectAreaId.setValue(p.subjectAreas[0].id ?? null);
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  /** Course number checks of the GWT Course Offering Edit page (CourseOfferingEdit.validate). */
  private validateCreate(): string | null {
    const p = this.props();
    const v = this.form.getRawValue();
    if (v.subjectAreaId == null) return 'Subject is required.';
    let nbr = (v.courseNbr ?? '').trim();
    if (!nbr) return 'Course Number is required.';
    if (p?.courseOfferingNumberUpperCase) nbr = nbr.toUpperCase();
    this.form.controls.courseNbr.setValue(nbr);
    if (p?.courseNbrRegex) {
      try {
        if (!new RegExp(p.courseNbrRegex).test(nbr)) return p.courseNbrInfo || 'Course Number does not match the required pattern.';
      } catch (e) {
        return `Course Number cannot be matched to regular expression: ${p.courseNbrRegex} . Reason: ${(e as Error).message}`;
      }
    }
    if (!(v.title ?? '').trim()) return 'Title is required.';
    return null;
  }

  private create(): void {
    const err = this.validateCreate();
    this.formError.set(err);
    if (err) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const courseOffering: CourseOfferingInterface = {
      ...this.loaded,
      subjectAreaId: v.subjectAreaId ?? undefined,
      courseNbr: (v.courseNbr ?? '').trim(),
      title: (v.title ?? '').trim(),
      courseTypeId: v.courseTypeId ?? undefined,
      consent: v.consent ?? undefined,
      byReservationOnly: !!v.byReservationOnly,
      scheduleBookNote: v.scheduleBookNote || undefined,
      notes: v.notes || undefined,
      overrides: [],
      sendCoordinators: [],
    };
    const request: UpdateCourseOfferingRequest = { action: 'CREATE', courseOffering };
    this.saving.set(true);
    this.rpc.execute<CourseOfferingInterface>('UpdateCourseOfferingRequest', request).subscribe({
      next: (res) => {
        this.saving.set(false);
        if (res.errorMessage) {
          this.formError.set(res.errorMessage);
          return;
        }
        this.messages.add({
          severity: 'success',
          summary: 'Course offering created',
          detail: `${this.subjectAbbv(v.subjectAreaId)} ${res.courseNbr ?? courseOffering.courseNbr}`.trim(),
        });
        if (res.id != null) this.router.navigate(['/offering-detail', res.id]);
        else this.router.navigate(['/offerings']);
      },
      error: (e: ApiError) => {
        this.saving.set(false);
        this.formError.set(e.message);
      },
    });
  }

  protected subjectAbbv(id: number | null | undefined): string {
    return this.props()?.subjectAreas?.find((s) => s.id === id)?.abbv ?? '';
  }

  private load(courseOfferingId: number): void {
    this.createMode.set(false);
    this.formError.set(null);
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
    if (this.createMode()) {
      this.create();
      return;
    }
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
        if (res.errorMessage) {
          this.messages.add({ severity: 'error', summary: 'Save failed', detail: res.errorMessage });
          return;
        }
        this.messages.add({ severity: 'success', summary: 'Course offering saved', detail: res.label });
      },
      error: (e: ApiError) => {
        this.saving.set(false);
        this.messages.add({ severity: 'error', summary: 'Save failed', detail: e.message });
      },
    });
  }

  back(): void {
    if (this.createMode()) this.router.navigate(['/offerings']);
    else if (this.loaded?.id != null) this.router.navigate(['/offering-detail', this.loaded.id]);
    else this.router.navigate(['/offerings']);
  }
}
