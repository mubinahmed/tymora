import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { SelectButtonModule } from 'primeng/selectbutton';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { EventGrid } from './event-grid';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import {
  AcademicSession,
  ApiError,
  ContactInterface,
  EventInterface,
  EventLookupRpcRequest,
  ResourceInterface,
  ResourceLookupRpcRequest,
} from '../../core/models';

interface PickerItem {
  id: number;
  label: string;
}

/**
 * Lookup Examinations — the "exams" GWT page (EventResourceTimetable with
 * PageType.Exams). That page is locked to the SUBJECT resource type and applies
 * an event filter of type:"Final Exam" type:"Midterm Exam" (Event.sEventTypesAbbv
 * indices 1 & 2). We reproduce that here: pick a subject area (via
 * ResourceLookupRpcRequest SUBJECT), then look up its examination events via
 * EventLookupRpcRequest with the exam-type filter. Read-only list + weekly grid;
 * the meeting-level export/detail is deferred (shared with the Events screen).
 */
@Component({
  selector: 'app-exams',
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    SelectModule,
    SelectButtonModule,
    TagModule,
    MessageModule,
    ProgressSpinnerModule,
    EventGrid,
  ],
  templateUrl: './exams.html',
})
export class Exams implements OnInit {
  private rpc = inject(RpcService);
  private page = inject(PageService);

  /** The exam-type event filter that defines the Exams page (Final + Midterm). */
  private readonly examTypes = ['Final Exam', 'Midterm Exam'];

  protected readonly loading = signal(false);
  protected readonly loadingList = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly picks = signal<PickerItem[]>([]);
  protected readonly events = signal<EventInterface[]>([]);
  protected readonly searched = signal(false);

  protected resourceId: number | null = null;

  /**
   * Examinations are academic-session scoped: the EventAction permission gate
   * matches Right.Events against a Session qualifier, so every request MUST carry
   * the session id or the backend denies access.
   */
  protected readonly sessions = signal<AcademicSession[]>([]);
  protected sessionId: number | null = null;

  protected readonly view = signal<'list' | 'grid'>('list');
  protected readonly viewOptions = [
    { label: 'List', value: 'list', icon: 'pi pi-list' },
    { label: 'Grid', value: 'grid', icon: 'pi pi-calendar' },
  ];

  ngOnInit(): void {
    this.page.set('Examinations');
    this.loadSessions();
  }

  /** Load selectable academic sessions and default to the current (selected) one. */
  private loadSessions(): void {
    this.loading.set(true);
    this.rpc.execute<AcademicSession[]>('ListAcademicSessions', {}).subscribe({
      next: (list) => {
        const sessions = list ?? [];
        this.sessions.set(sessions);
        this.sessionId = (sessions.find((s) => s.selected) ?? sessions[0])?.uniqueId ?? null;
        this.loadSubjects();
      },
      error: (e: ApiError) => this.fail(e),
    });
  }

  onSessionChange(): void {
    this.resourceId = null;
    this.events.set([]);
    this.searched.set(false);
    this.error.set(null);
    this.loadSubjects();
  }

  /** Populate the subject-area picker for the selected session. */
  private loadSubjects(): void {
    this.loading.set(true);
    const request: ResourceLookupRpcRequest = {
      resourceType: 'SUBJECT',
      name: '',
      limit: 2000,
      sessionId: this.sessionId ?? undefined,
    };
    this.rpc.execute<ResourceInterface[]>('ResourceLookupRpcRequest', request).subscribe({
      next: (list) => {
        this.picks.set(
          (list ?? []).map((r) => ({
            id: r.resourceId!,
            label: [r.abbreviation, r.resourceName].filter(Boolean).join(' — '),
          })),
        );
        this.loading.set(false);
      },
      error: (e: ApiError) => this.fail(e),
    });
  }

  /** Look up examination events for the selected subject area. */
  loadEvents(): void {
    if (this.resourceId == null) return;
    const request: EventLookupRpcRequest = {
      resourceType: 'SUBJECT',
      resourceId: this.resourceId,
      eventFilter: { command: 'ENUMERATE', options: { type: this.examTypes } },
      roomFilter: { command: 'ENUMERATE', options: {} },
      sessionId: this.sessionId ?? undefined,
    };
    this.loadingList.set(true);
    this.error.set(null);
    this.rpc.execute<EventInterface[]>('EventLookupRpcRequest', request).subscribe({
      next: (list) => {
        this.events.set(list ?? []);
        this.searched.set(true);
        this.loadingList.set(false);
      },
      error: (e: ApiError) => this.fail(e, true),
    });
  }

  private fail(e: ApiError, list = false): void {
    this.error.set(e.message);
    (list ? this.loadingList : this.loading).set(false);
  }

  meetingSpan(e: EventInterface): string {
    const m = e.meetings ?? [];
    if (!m.length) return '—';
    const first = m[0].meetingDate;
    const last = m[m.length - 1].meetingDate;
    return first === last ? (first ?? '') : `${first} – ${last}`;
  }

  contactName(c?: ContactInterface): string {
    return c?.formattedName || [c?.firstName, c?.lastName].filter(Boolean).join(' ') || '';
  }
}
