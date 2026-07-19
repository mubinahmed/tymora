import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { CheckboxModule } from 'primeng/checkbox';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';
import { IdName, SubpartEditRequest, SubpartEditResponse, SubpartEditUpdateRequest } from './subpart-edit.models';

/**
 * Scheduling Subpart Edit (legacy schedulingSubpartEdit.action) — read + save of
 * the subpart-row fields, backed by SubpartEditRequest (load) and
 * SubpartEditUpdateRequest (save). Credit configuration and time/room preferences
 * keep their own handling and are read-only here. Reached by subpart id from
 * Scheduling Subpart Detail.
 */
@Component({
  selector: 'app-subpart-edit',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    ButtonModule,
    SelectModule,
    CheckboxModule,
    MessageModule,
    ProgressSpinnerModule,
  ],
  templateUrl: './subpart-edit.html',
})
export class SchedulingSubpartEdit implements OnInit {
  private fb = inject(FormBuilder);
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private router = inject(Router);
  private messages = inject(MessageService);

  readonly id = input<string>();

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly data = signal<SubpartEditResponse | null>(null);
  protected readonly datePatternOptions = signal<IdName[]>([]);
  protected readonly itypeOptions = signal<IdName[]>([]);

  protected readonly offeringId = computed(() => this.data()?.offeringId ?? null);

  protected readonly form = this.fb.group({
    instructionalType: [null as number | null],
    datePatternId: [-1 as number | null],
    autoSpreadInTime: [false],
    studentAllowOverlap: [false],
  });

  ngOnInit(): void {
    this.page.set('Edit Scheduling Subpart');
    const sid = this.id() == null ? NaN : Number(this.id());
    if (!Number.isFinite(sid)) {
      this.error.set('No scheduling subpart was specified.');
      this.loading.set(false);
      return;
    }
    const request: SubpartEditRequest = { subpartId: sid };
    this.rpc.execute<SubpartEditResponse>('SubpartEditRequest', request).subscribe({
      next: (d) => this.apply(d),
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  private apply(d: SubpartEditResponse): void {
    this.data.set(d);
    this.datePatternOptions.set(d.datePatternOptions ?? []);
    this.itypeOptions.set(d.itypeOptions ?? []);
    if (d.courseName) this.page.set('Edit ' + d.courseName + ' ' + (d.instructionalTypeLabel ?? ''));
    this.form.patchValue({
      instructionalType: d.instructionalType ?? null,
      datePatternId: d.datePatternId ?? -1,
      autoSpreadInTime: !!d.autoSpreadInTime,
      studentAllowOverlap: !!d.studentAllowOverlap,
    });
    if (d.datePatternEditable === false) this.form.controls.datePatternId.disable();
    else this.form.controls.datePatternId.enable();
    this.loading.set(false);
  }

  save(): void {
    const sid = this.data()?.subpartId;
    if (sid == null) return;
    this.saving.set(true);
    const v = this.form.getRawValue();
    const request: SubpartEditUpdateRequest = {
      subpartId: sid,
      instructionalType: v.instructionalType,
      datePatternId: v.datePatternId,
      autoSpreadInTime: !!v.autoSpreadInTime,
      studentAllowOverlap: !!v.studentAllowOverlap,
    };
    this.rpc.execute<SubpartEditResponse>('SubpartEditUpdateRequest', request).subscribe({
      next: (d) => {
        this.saving.set(false);
        this.messages.add({ severity: 'success', summary: 'Saved', detail: 'Scheduling subpart updated.' });
        this.router.navigate(['/subpart-detail', d.subpartId ?? sid]);
      },
      error: (e: ApiError) => {
        this.saving.set(false);
        this.messages.add({ severity: 'error', summary: 'Save failed', detail: e.message });
      },
    });
  }
}
