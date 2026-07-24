import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { SelectModule } from 'primeng/select';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ConfirmationService, MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';

type SessionEditOperation = 'LOAD' | 'SAVE' | 'DELETE';

interface Option {
  id: number;
  label: string;
}

interface SessionEditRequest {
  operation: SessionEditOperation;
  uniqueId?: number | null;
  academicInitiative?: string;
  academicYear?: string;
  academicTerm?: string;
  sessionBeginDateTime?: string;
  classesEndDateTime?: string;
  sessionEndDateTime?: string;
  examBeginDate?: string;
  eventBeginDate?: string;
  eventEndDate?: string;
  statusTypeId?: number | null;
  defaultDatePatternId?: number | null;
  durationTypeId?: number | null;
  instructionalMethodId?: number | null;
  wkEnroll?: number | null;
  wkChange?: number | null;
  wkDrop?: number | null;
  sectStatusId?: number | null;
  notificationsBegin?: string;
  notificationsEnd?: string;
}

interface SessionEditResponse {
  uniqueId?: number;
  academicInitiative?: string;
  academicYear?: string;
  academicTerm?: string;
  sessionBeginDateTime?: string;
  classesEndDateTime?: string;
  sessionEndDateTime?: string;
  examBeginDate?: string;
  eventBeginDate?: string;
  eventEndDate?: string;
  statusTypeId?: number | null;
  defaultDatePatternId?: number | null;
  durationTypeId?: number | null;
  instructionalMethodId?: number | null;
  wkEnroll?: number | null;
  wkChange?: number | null;
  wkDrop?: number | null;
  sectStatusId?: number | null;
  notificationsBegin?: string;
  notificationsEnd?: string;
  label?: string;
  dateFormat?: string;
  canEdit?: boolean;
  canDelete?: boolean;
  statuses?: Option[];
  datePatterns?: Option[];
  durationTypes?: Option[];
  instructionalMethods?: Option[];
  sectStatuses?: Option[];
}

interface Model {
  academicInitiative: string;
  academicYear: string;
  academicTerm: string;
  sessionBeginDateTime: string;
  classesEndDateTime: string;
  examBeginDate: string;
  sessionEndDateTime: string;
  eventBeginDate: string;
  eventEndDate: string;
  statusTypeId: number | null;
  defaultDatePatternId: number | null;
  durationTypeId: number | null;
  instructionalMethodId: number | null;
  wkEnroll: number | null;
  wkChange: number | null;
  wkDrop: number | null;
  sectStatusId: number | null;
  notificationsBegin: string;
  notificationsEnd: string;
}

/**
 * Edit an academic session, aligned with the legacy sessionEdit page: identity,
 * the date boundaries (session begin, classes end, exam begin, session end, event
 * begin/end), status, the default date pattern / class duration / instructional
 * method, the enroll/change/drop week boundaries, the default student status and
 * the notification dates. Backed by SessionEditBackend (merge-on-save). The
 * interactive holidays calendar, the date/time pattern editors and roll-forward
 * remain on the legacy page.
 */
@Component({
  selector: 'app-sessions-edit',
  imports: [
    FormsModule,
    ButtonModule,
    InputTextModule,
    InputNumberModule,
    SelectModule,
    MessageModule,
    ProgressSpinnerModule,
    ConfirmDialogModule,
  ],
  providers: [ConfirmationService],
  templateUrl: './sessions-edit.html',
})
export class SessionsEdit {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private router = inject(Router);
  private confirm = inject(ConfirmationService);
  private messages = inject(MessageService);

  /** academic session id from the route (/sessions-edit/:id) */
  readonly id = input.required<string>();

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly label = signal('');
  protected readonly dateFormat = signal('');
  protected readonly canEdit = signal(false);
  protected readonly canDelete = signal(false);

  protected readonly statuses = signal<Option[]>([]);
  protected readonly datePatterns = signal<Option[]>([]);
  protected readonly durationTypes = signal<Option[]>([]);
  protected readonly instructionalMethods = signal<Option[]>([]);
  protected readonly sectStatuses = signal<Option[]>([]);

  protected readonly datePatternOptions = computed(() => this.withNone(this.datePatterns()));
  protected readonly durationOptions = computed(() => this.withNone(this.durationTypes()));
  protected readonly methodOptions = computed(() => this.withNone(this.instructionalMethods()));
  protected readonly sectStatusOptions = computed(() => this.withNone(this.sectStatuses()));

  protected model: Model = this.blank();

  constructor() {
    this.page.set('Edit Academic Session');
    effect(() => {
      const raw = this.id();
      const uid = Number(raw);
      if (raw != null && !Number.isNaN(uid)) this.load(uid);
    });
  }

  private blank(): Model {
    return {
      academicInitiative: '',
      academicYear: '',
      academicTerm: '',
      sessionBeginDateTime: '',
      classesEndDateTime: '',
      examBeginDate: '',
      sessionEndDateTime: '',
      eventBeginDate: '',
      eventEndDate: '',
      statusTypeId: null,
      defaultDatePatternId: null,
      durationTypeId: null,
      instructionalMethodId: null,
      wkEnroll: null,
      wkChange: null,
      wkDrop: null,
      sectStatusId: null,
      notificationsBegin: '',
      notificationsEnd: '',
    };
  }

  private withNone(options: Option[]): { label: string; value: number | null }[] {
    return [{ label: '— None —', value: null }, ...options.map((o) => ({ label: o.label, value: o.id }))];
  }

  private load(uniqueId: number): void {
    this.loading.set(true);
    this.error.set(null);
    const req: SessionEditRequest = { operation: 'LOAD', uniqueId };
    this.rpc.execute<SessionEditResponse>('SessionEditRequest', req).subscribe({
      next: (d) => {
        this.label.set(d.label ?? '');
        this.dateFormat.set(d.dateFormat ?? '');
        this.canEdit.set(d.canEdit !== false);
        this.canDelete.set(d.canDelete === true);
        this.statuses.set(d.statuses ?? []);
        this.datePatterns.set(d.datePatterns ?? []);
        this.durationTypes.set(d.durationTypes ?? []);
        this.instructionalMethods.set(d.instructionalMethods ?? []);
        this.sectStatuses.set(d.sectStatuses ?? []);
        this.model = {
          academicInitiative: d.academicInitiative ?? '',
          academicYear: d.academicYear ?? '',
          academicTerm: d.academicTerm ?? '',
          sessionBeginDateTime: d.sessionBeginDateTime ?? '',
          classesEndDateTime: d.classesEndDateTime ?? '',
          examBeginDate: d.examBeginDate ?? '',
          sessionEndDateTime: d.sessionEndDateTime ?? '',
          eventBeginDate: d.eventBeginDate ?? '',
          eventEndDate: d.eventEndDate ?? '',
          statusTypeId: d.statusTypeId ?? null,
          defaultDatePatternId: d.defaultDatePatternId ?? null,
          durationTypeId: d.durationTypeId ?? null,
          instructionalMethodId: d.instructionalMethodId ?? null,
          wkEnroll: d.wkEnroll ?? null,
          wkChange: d.wkChange ?? null,
          wkDrop: d.wkDrop ?? null,
          sectStatusId: d.sectStatusId ?? null,
          notificationsBegin: d.notificationsBegin ?? '',
          notificationsEnd: d.notificationsEnd ?? '',
        };
        if (d.label) this.page.set('Edit ' + d.label);
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  save(): void {
    const req: SessionEditRequest = { operation: 'SAVE', uniqueId: Number(this.id()), ...this.model };
    this.saving.set(true);
    this.rpc.execute<SessionEditResponse>('SessionEditRequest', req).subscribe({
      next: (d) => {
        this.saving.set(false);
        this.messages.add({ severity: 'success', summary: 'Academic session saved', detail: d.label });
        this.back();
      },
      error: (e: ApiError) => {
        this.saving.set(false);
        this.messages.add({ severity: 'error', summary: 'Save failed', detail: e.message });
      },
    });
  }

  confirmDelete(): void {
    this.confirm.confirm({
      header: 'Delete academic session',
      message: `Delete "${this.label()}"? This permanently removes the session and all of its data and cannot be undone.`,
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => this.doDelete(),
    });
  }

  private doDelete(): void {
    const req: SessionEditRequest = { operation: 'DELETE', uniqueId: Number(this.id()) };
    this.saving.set(true);
    this.rpc.execute<SessionEditResponse>('SessionEditRequest', req).subscribe({
      next: (d) => {
        this.saving.set(false);
        this.messages.add({ severity: 'success', summary: 'Academic session deleted', detail: d.label });
        this.back();
      },
      error: (e: ApiError) => {
        this.saving.set(false);
        this.messages.add({ severity: 'error', summary: 'Delete failed', detail: e.message });
      },
    });
  }

  back(): void {
    this.router.navigate(['/list/sessions']);
  }
}
