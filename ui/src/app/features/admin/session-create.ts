import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';

// --- SessionCreateInterface DTOs (declared inline; the bean was added for the
//     Angular migration and is not in the generated models). Field names match
//     the facade's Gson naming (Java iField -> field). ---------------------------
type SessionCreateOperation = 'LOAD' | 'SAVE';

interface SessionStatusOption {
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
}

interface SessionCreateResponse {
  uniqueId?: number;
  label?: string;
  dateFormat?: string;
  canAdd?: boolean;
  statuses?: SessionStatusOption[];
}

interface Model {
  academicInitiative: string;
  academicYear: string;
  academicTerm: string;
  sessionBeginDateTime: string;
  classesEndDateTime: string;
  sessionEndDateTime: string;
  examBeginDate: string;
  eventBeginDate: string;
  eventEndDate: string;
  statusTypeId: number | null;
}

/**
 * Create a brand new academic session. Backed by the new SessionCreateBackend
 * command bean, which owns the CREATE half deferred by the edit-only
 * SessionEditBackend. Reached at /session-create from the academic sessions list
 * ("Add Session").
 *
 * Every mandatory NOT-NULL column is rendered here (initiative / year / term,
 * the session begin, classes end and session end dates, the examination begin
 * date, the event begin and end dates, and the status type). Enrollment week
 * boundaries default to 1 / 1 / 4 server-side. Optional setup (default date
 * pattern, holidays, notifications, sectioning status, class duration type,
 * instructional method, roll-forward) is configured afterwards on the Edit /
 * legacy pages.
 */
@Component({
  selector: 'app-session-create',
  imports: [
    FormsModule,
    CardModule,
    ButtonModule,
    InputTextModule,
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
  protected readonly statuses = signal<SessionStatusOption[]>([]);
  protected readonly model = signal<Model>({
    academicInitiative: '',
    academicYear: '',
    academicTerm: '',
    sessionBeginDateTime: '',
    classesEndDateTime: '',
    sessionEndDateTime: '',
    examBeginDate: '',
    eventBeginDate: '',
    eventEndDate: '',
    statusTypeId: null,
  });

  constructor() {
    this.page.set('Add Academic Session');
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    const req: SessionCreateRequest = { operation: 'LOAD' };
    this.rpc.execute<SessionCreateResponse>('SessionCreateRequest', req).subscribe({
      next: (d) => {
        this.dateFormat.set(d.dateFormat ?? '');
        this.canAdd.set(d.canAdd !== false);
        this.statuses.set(d.statuses ?? []);
        this.loading.set(false);
      },
      error: (e: ApiError) => {
        this.error.set(e.message);
        this.loading.set(false);
      },
    });
  }

  set<K extends keyof Model>(key: K, value: Model[K]): void {
    this.model.set({ ...this.model(), [key]: value });
  }

  save(): void {
    const m = this.model();
    const req: SessionCreateRequest = {
      operation: 'SAVE',
      academicInitiative: m.academicInitiative,
      academicYear: m.academicYear,
      academicTerm: m.academicTerm,
      sessionBeginDateTime: m.sessionBeginDateTime,
      classesEndDateTime: m.classesEndDateTime,
      sessionEndDateTime: m.sessionEndDateTime,
      examBeginDate: m.examBeginDate,
      eventBeginDate: m.eventBeginDate,
      eventEndDate: m.eventEndDate,
      statusTypeId: m.statusTypeId,
    };
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
