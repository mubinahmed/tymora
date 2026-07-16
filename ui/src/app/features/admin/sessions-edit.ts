import { Component, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ConfirmationService, MessageService } from 'primeng/api';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';

// --- SessionEditInterface DTOs (declared inline; the bean was added for the
//     Angular migration and is not in the generated models). Field names match
//     the facade's Gson naming (Java iField -> field). ---------------------------
type SessionEditOperation = 'LOAD' | 'SAVE' | 'DELETE';

interface SessionStatusOption {
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
  statusTypeId?: number | null;
}

interface SessionEditResponse {
  uniqueId?: number;
  academicInitiative?: string;
  academicYear?: string;
  academicTerm?: string;
  sessionBeginDateTime?: string;
  classesEndDateTime?: string;
  sessionEndDateTime?: string;
  statusTypeId?: number | null;
  label?: string;
  dateFormat?: string;
  canEdit?: boolean;
  canDelete?: boolean;
  statuses?: SessionStatusOption[];
}

interface Model {
  academicInitiative: string;
  academicYear: string;
  academicTerm: string;
  sessionBeginDateTime: string;
  classesEndDateTime: string;
  sessionEndDateTime: string;
  statusTypeId: number | null;
}

/**
 * Edit the core descriptive fields of an academic session (initiative, year,
 * term, the session begin / classes end / session end dates and status type).
 * Backed by the new SessionEditBackend command bean. Reached per-session as
 * /sessions-edit/:id from the academic sessions list.
 *
 * Scope is deliberately narrow and merge-on-save: exam/event periods, holidays,
 * date/time patterns, roll-forward and the enrollment week boundaries are left
 * untouched. Creating a new session is not supported here (several mandatory
 * columns are not rendered) -- that stays on the legacy page.
 */
@Component({
  selector: 'app-sessions-edit',
  imports: [
    FormsModule,
    CardModule,
    ButtonModule,
    InputTextModule,
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
  protected readonly statuses = signal<SessionStatusOption[]>([]);
  protected readonly model = signal<Model>({
    academicInitiative: '',
    academicYear: '',
    academicTerm: '',
    sessionBeginDateTime: '',
    classesEndDateTime: '',
    sessionEndDateTime: '',
    statusTypeId: null,
  });

  constructor() {
    this.page.set('Edit Academic Session');
    effect(() => {
      const raw = this.id();
      const uid = Number(raw);
      if (raw != null && !Number.isNaN(uid)) this.load(uid);
    });
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
        this.model.set({
          academicInitiative: d.academicInitiative ?? '',
          academicYear: d.academicYear ?? '',
          academicTerm: d.academicTerm ?? '',
          sessionBeginDateTime: d.sessionBeginDateTime ?? '',
          classesEndDateTime: d.classesEndDateTime ?? '',
          sessionEndDateTime: d.sessionEndDateTime ?? '',
          statusTypeId: d.statusTypeId ?? null,
        });
        if (d.label) this.page.set('Edit ' + d.label);
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
    const req: SessionEditRequest = {
      operation: 'SAVE',
      uniqueId: Number(this.id()),
      academicInitiative: m.academicInitiative,
      academicYear: m.academicYear,
      academicTerm: m.academicTerm,
      sessionBeginDateTime: m.sessionBeginDateTime,
      classesEndDateTime: m.classesEndDateTime,
      sessionEndDateTime: m.sessionEndDateTime,
      statusTypeId: m.statusTypeId,
    };
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
