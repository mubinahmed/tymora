import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { DatePickerModule } from 'primeng/datepicker';
import { SelectModule } from 'primeng/select';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';

type SessionCreateOperation = 'LOAD' | 'SAVE';

interface Option {
  id: number;
  label: string;
}

interface SessionCreateRequest {
  operation: SessionCreateOperation;
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

interface SessionCreateResponse {
  uniqueId?: number;
  label?: string;
  dateFormat?: string;
  canAdd?: boolean;
  wkEnroll?: number;
  wkChange?: number;
  wkDrop?: number;
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
 * Create a brand new academic session, aligned with the legacy sessionEdit page:
 * identity, the date boundaries, status, the default date pattern / class
 * duration / instructional method, the enroll/change/drop week boundaries, the
 * default student status and the notification dates. Backed by SessionCreateBackend.
 * The interactive holidays calendar, the date/time pattern editors and roll-forward
 * remain on the legacy page.
 */
@Component({
  selector: 'app-session-create',
  imports: [
    FormsModule,
    ButtonModule,
    InputTextModule,
    InputNumberModule,
    DatePickerModule,
    SelectModule,
    MessageModule,
    ProgressSpinnerModule,
  ],
  templateUrl: './session-create.html',
})
export class SessionCreate {
  private rpc = inject(RpcService);
  private page = inject(PageService);
  private router = inject(Router);
  private messages = inject(MessageService);

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly dateFormat = signal('');
  protected readonly canAdd = signal(false);

  protected readonly statuses = signal<Option[]>([]);
  protected readonly datePatterns = signal<Option[]>([]);
  protected readonly durationTypes = signal<Option[]>([]);
  protected readonly instructionalMethods = signal<Option[]>([]);
  protected readonly sectStatuses = signal<Option[]>([]);

  /** Selects that allow "no default" get a leading blank option (value null). */
  protected readonly datePatternOptions = computed(() => this.withNone(this.datePatterns()));
  protected readonly durationOptions = computed(() => this.withNone(this.durationTypes()));
  protected readonly methodOptions = computed(() => this.withNone(this.instructionalMethods()));
  protected readonly sectStatusOptions = computed(() => this.withNone(this.sectStatuses()));

  protected model: Model = this.blank();

  constructor() {
    this.page.set('Add Academic Session');
    this.load();
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
      wkEnroll: 1,
      wkChange: 1,
      wkDrop: 4,
      sectStatusId: null,
      notificationsBegin: '',
      notificationsEnd: '',
    };
  }

  private withNone(options: Option[]): { label: string; value: number | null }[] {
    return [{ label: '— None —', value: null }, ...options.map((o) => ({ label: o.label, value: o.id }))];
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.rpc.execute<SessionCreateResponse>('SessionCreateRequest', { operation: 'LOAD' }).subscribe({
      next: (d) => {
        this.dateFormat.set(d.dateFormat ?? '');
        this.canAdd.set(d.canAdd !== false);
        this.statuses.set(d.statuses ?? []);
        this.datePatterns.set(d.datePatterns ?? []);
        this.durationTypes.set(d.durationTypes ?? []);
        this.instructionalMethods.set(d.instructionalMethods ?? []);
        this.sectStatuses.set(d.sectStatuses ?? []);
        this.model.wkEnroll = d.wkEnroll ?? 1;
        this.model.wkChange = d.wkChange ?? 1;
        this.model.wkDrop = d.wkDrop ?? 4;
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  save(): void {
    const m = this.model;
    const req: SessionCreateRequest = { operation: 'SAVE', ...m };
    this.saving.set(true);
    this.rpc.execute<SessionCreateResponse>('SessionCreateRequest', req).subscribe({
      next: (d) => {
        this.saving.set(false);
        this.messages.add({ severity: 'success', summary: 'Academic session created', detail: d.label });
        this.back();
      },
      error: (e: ApiError) => {
        this.saving.set(false);
        this.messages.add({ severity: 'error', summary: 'Create failed', detail: e.message });
      },
    });
  }

  back(): void {
    this.router.navigate(['/list/sessions']);
  }
}
