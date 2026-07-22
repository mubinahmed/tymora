import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { CheckboxModule } from 'primeng/checkbox';
import { MultiSelectModule } from 'primeng/multiselect';
import { DialogModule } from 'primeng/dialog';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';
import { DistTypeInfo, DistTypeListResponse, DistTypeUpdateRequest, IdName } from './distribution-types.models';

/**
 * Distribution Types (legacy distributionTypeList/distributionTypeEdit.action) — a
 * master-detail editor backed by DistTypeListRequest (load all) and DistTypeUpdateRequest
 * (save one). Edits each type's label/abbreviation/description/flags, its allowed
 * preference levels and the departments (in this session) that may use it.
 */
@Component({
  selector: 'app-distribution-types',
  imports: [
    ReactiveFormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    TextareaModule,
    CheckboxModule,
    MultiSelectModule,
    DialogModule,
    TagModule,
    MessageModule,
    ProgressSpinnerModule,
  ],
  templateUrl: './distribution-types.html',
})
export class DistributionTypes implements OnInit {
  private fb = inject(FormBuilder);
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private messages = inject(MessageService);

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly data = signal<DistTypeListResponse | null>(null);
  protected readonly editing = signal<DistTypeInfo | null>(null);

  protected readonly types = computed<DistTypeInfo[]>(() => this.data()?.types ?? []);
  protected readonly departments = computed<IdName[]>(() => this.data()?.departments ?? []);
  protected readonly prefLevels = computed<IdName[]>(() => this.data()?.prefLevels ?? []);

  protected readonly form = this.fb.group({
    label: ['', Validators.required],
    abbreviation: [''],
    descr: [''],
    instructorPref: [false],
    survey: [false],
    visible: [true],
    allowedPrefIds: [[] as number[]],
    departmentIds: [[] as number[]],
  });

  ngOnInit(): void {
    this.page.set('Distribution Types');
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.rpc.execute<DistTypeListResponse>('DistTypeListRequest', {}).subscribe({
      next: (d) => {
        this.data.set(d);
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  deptNames(t: DistTypeInfo): string {
    const byId = new Map((this.departments() ?? []).map((d) => [d.id, d.name]));
    return (t.departmentIds ?? []).map((id) => byId.get(id) ?? '').filter(Boolean).join(', ');
  }

  edit(t: DistTypeInfo): void {
    this.editing.set(t);
    this.form.reset({
      label: t.label ?? '',
      abbreviation: t.abbreviation ?? '',
      descr: t.descr ?? '',
      instructorPref: !!t.instructorPref,
      survey: !!t.survey,
      visible: !!t.visible,
      allowedPrefIds: [...(t.allowedPrefIds ?? [])],
      departmentIds: [...(t.departmentIds ?? [])],
    });
  }

  cancel(): void {
    this.editing.set(null);
  }

  save(): void {
    const t = this.editing();
    if (!t || this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.saving.set(true);
    const request: DistTypeUpdateRequest = {
      id: t.id,
      label: v.label ?? '',
      abbreviation: v.abbreviation ?? '',
      descr: v.descr ?? '',
      instructorPref: !!v.instructorPref,
      survey: !!v.survey,
      visible: !!v.visible,
      allowedPrefIds: v.allowedPrefIds ?? [],
      departmentIds: v.departmentIds ?? [],
    };
    this.rpc.execute<DistTypeListResponse>('DistTypeUpdateRequest', request).subscribe({
      next: (d) => {
        this.saving.set(false);
        this.data.set(d);
        this.editing.set(null);
        this.messages.add({ severity: 'success', summary: 'Saved', detail: 'Distribution type updated.' });
      },
      error: (e: ApiError) => {
        this.saving.set(false);
        this.messages.add({ severity: 'error', summary: 'Save failed', detail: e.message });
      },
    });
  }
}
