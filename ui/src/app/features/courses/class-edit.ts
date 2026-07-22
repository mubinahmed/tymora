import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { TextareaModule } from 'primeng/textarea';
import { SelectModule } from 'primeng/select';
import { CheckboxModule } from 'primeng/checkbox';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';
import { ClassEditRequest, ClassEditResponse, ClassEditUpdateRequest, IdName } from './class-edit.models';

/**
 * Class Edit (legacy classEdit.action) — read + save of the class-data fields,
 * backed by ClassEditRequest (load) and ClassEditUpdateRequest (save). Instructor
 * assignment and time/room preferences keep their own screens, so they are not
 * edited here. Reached by class id from Class Detail.
 */
@Component({
  selector: 'app-class-edit',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    ButtonModule,
    InputTextModule,
    InputNumberModule,
    TextareaModule,
    SelectModule,
    CheckboxModule,
    MessageModule,
    ProgressSpinnerModule,
  ],
  templateUrl: './class-edit.html',
})
export class ClassEdit implements OnInit {
  private fb = inject(FormBuilder);
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private router = inject(Router);
  private messages = inject(MessageService);

  readonly id = input<string>();

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly data = signal<ClassEditResponse | null>(null);
  protected readonly datePatternOptions = signal<IdName[]>([]);

  protected readonly offeringId = computed(() => this.data()?.offeringId ?? null);

  protected readonly form = this.fb.group({
    expectedCapacity: [null as number | null],
    maxExpectedCapacity: [null as number | null],
    roomRatio: [null as number | null],
    nbrRooms: [null as number | null],
    splitAttendance: [false],
    datePatternId: [-1 as number | null],
    enabledForStudentScheduling: [false],
    displayInstructor: [false],
    notes: [''],
    schedulePrintNote: [''],
  });

  ngOnInit(): void {
    this.page.set('Edit Class');
    const cid = this.id() == null ? NaN : Number(this.id());
    if (!Number.isFinite(cid)) {
      this.error.set('No class was specified.');
      this.loading.set(false);
      return;
    }
    const request: ClassEditRequest = { classId: cid };
    this.rpc.execute<ClassEditResponse>('ClassEditRequest', request).subscribe({
      next: (d) => this.apply(d),
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  private apply(d: ClassEditResponse): void {
    this.data.set(d);
    this.datePatternOptions.set(d.datePatternOptions ?? []);
    if (d.className) this.page.set('Edit ' + d.className);
    this.form.patchValue({
      expectedCapacity: d.expectedCapacity ?? null,
      maxExpectedCapacity: d.maxExpectedCapacity ?? null,
      roomRatio: d.roomRatio ?? null,
      nbrRooms: d.nbrRooms ?? null,
      splitAttendance: !!d.splitAttendance,
      datePatternId: d.datePatternId ?? -1,
      enabledForStudentScheduling: !!d.enabledForStudentScheduling,
      displayInstructor: !!d.displayInstructor,
      notes: d.notes ?? '',
      schedulePrintNote: d.schedulePrintNote ?? '',
    });
    if (d.datePatternEditable === false) this.form.controls.datePatternId.disable();
    else this.form.controls.datePatternId.enable();
    this.loading.set(false);
  }

  save(): void {
    const cid = this.data()?.classId;
    if (cid == null) return;
    this.saving.set(true);
    const v = this.form.getRawValue();
    const request: ClassEditUpdateRequest = {
      classId: cid,
      expectedCapacity: v.expectedCapacity,
      maxExpectedCapacity: v.maxExpectedCapacity,
      roomRatio: v.roomRatio,
      nbrRooms: v.nbrRooms,
      splitAttendance: !!v.splitAttendance,
      datePatternId: v.datePatternId,
      enabledForStudentScheduling: !!v.enabledForStudentScheduling,
      displayInstructor: !!v.displayInstructor,
      notes: v.notes ?? '',
      schedulePrintNote: v.schedulePrintNote ?? '',
    };
    this.rpc.execute<ClassEditResponse>('ClassEditUpdateRequest', request).subscribe({
      next: (d) => {
        this.saving.set(false);
        this.messages.add({ severity: 'success', summary: 'Saved', detail: 'Class updated.' });
        this.router.navigate(['/class-detail', d.classId ?? cid]);
      },
      error: (e: ApiError) => {
        this.saving.set(false);
        this.messages.add({ severity: 'error', summary: 'Save failed', detail: e.message });
      },
    });
  }
}
