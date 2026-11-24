import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { SelectButtonModule } from 'primeng/selectbutton';
import { CheckboxModule } from 'primeng/checkbox';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { TabsModule } from 'primeng/tabs';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { TextareaModule } from 'primeng/textarea';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { TooltipModule } from 'primeng/tooltip';
import { MultiSelectModule } from 'primeng/multiselect';
import { MessageService } from 'primeng/api';
import QRCode from 'qrcode';
import { RpcService } from '../../core/rpc.service';
import { PageService } from '../../core/page.service';
import { ApiError } from '../../core/models';
import { downloadCsv } from '../../core/csv';
import { LogEntry } from '../seating/seating.models';
import { AttendanceDashboard } from './attendance-dashboard';
import { AttendanceReports } from './attendance-reports';
import {
  AttStatus,
  AttendanceConfig,
  AttendanceRequest,
  AttendanceResponse,
  Excuse,
  Followup,
  RosterEntry,
  SessionInfo,
} from './attendance.models';
import { DateInput, fromIso, toIso } from '../../shared/date-input';
import { DisplayDatePipe } from '../../shared/display-date.pipe';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ConfirmationService } from 'primeng/api';

const RPC = 'AttendanceRequest';

/**
 * Attendance (B1-7.8.x; demo 4.5.1): the day's class sessions from the timetable (own and covered classes,
 * or all for managers), roll call, rotating-QR self check-in shown on the classroom screen, finalising,
 * medical certificate / leave approval, follow-up cases raised by the configured rules, the dashboard and named reports,
 * course, class, student, staff, activity type, week, weekday and time, settings, the SIMS export and the
 * history. Backed by AttendanceRequest.
 */
@Component({
  selector: 'app-attendance',
  imports: [ConfirmDialogModule, DisplayDatePipe, DateInput, 
    DatePipe,
    FormsModule,
    ButtonModule,
    SelectModule,
    SelectButtonModule,
    CheckboxModule,
    TableModule,
    TagModule,
    TabsModule,
    DialogModule,
    InputTextModule,
    InputNumberModule,
    TextareaModule,
    MessageModule,
    ProgressSpinnerModule,
    TooltipModule,
    MultiSelectModule,
    AttendanceDashboard,
    AttendanceReports,
  ],
  providers: [ConfirmationService],
  templateUrl: './attendance.html',
  styleUrl: './attendance.scss',
})
export class Attendance implements OnInit, OnDestroy {
  private rpc = inject(RpcService);
  private confirmation = inject(ConfirmationService);
  private page = inject(PageService);
  private messages = inject(MessageService);

  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly info = signal<AttendanceResponse | null>(null);
  /** the INIT response: term dates, report catalogue, last recorded day (later responses do not repeat them) */
  protected readonly init = signal<AttendanceResponse | null>(null);
  protected readonly sessions = signal<SessionInfo[]>([]);
  protected readonly current = signal<SessionInfo | null>(null);
  protected readonly roster = signal<RosterEntry[]>([]);
  protected readonly excuses = signal<Excuse[]>([]);
  protected readonly followups = signal<Followup[]>([]);
  protected readonly log = signal<LogEntry[]>([]);
  protected readonly qrImage = signal<string | null>(null);
  protected readonly qrSeconds = signal(0);
  protected readonly qrOpen = signal(false);
  protected readonly decideFor = signal<{ e: Excuse; approve: boolean } | null>(null);
  protected readonly caseEdit = signal<Followup | null>(null);
  protected readonly fileView = signal<{ name: string; url: string; type: string } | null>(null);

  protected tab = 'take';
  protected date = '';
  protected scope = 'MINE';
  protected marks: Record<number, AttStatus | undefined> = {};
  protected notes: Record<number, string | undefined> = {};
  protected dirty = false;
  protected reason = '';
  protected decisionNote = '';
  /** report opened from a dashboard alert */
  protected reportToOpen: string | undefined;
  protected fromDate = '';
  protected toDate = '';
  protected config: AttendanceConfig | null = null;
  protected readonly caseFilter = signal('OPEN');
  protected recipientSel: string[] = [];
  protected exportSel: string[] = [];
  private timer: ReturnType<typeof setInterval> | null = null;
  private tick = 0;

  protected readonly statusOptions = [
    { label: 'P', value: 'PRESENT', title: 'Present' },
    { label: 'L', value: 'LATE', title: 'Late' },
    { label: 'A', value: 'ABSENT', title: 'Absent' },
    { label: 'E', value: 'EXCUSED', title: 'Excused' },
  ];
  protected readonly recipientOptions = [
    { label: 'Lecturers of the class', value: 'LECTURER' },
    { label: 'Department timetable coordinators', value: 'COORDINATOR' },
  ];
  protected readonly approverOptions = [
    { label: 'The school office (attendance managers)', value: 'OFFICE' },
    { label: 'Any lecturer of the student', value: 'LECTURER' },
  ];
  protected readonly exportOptions = ['STUDENT_ID', 'STUDENT_NAME', 'COURSE', 'CLASS', 'SCHOOL', 'DATE', 'START', 'END', 'ROOM', 'STATUS', 'SOURCE', 'EXCUSE', 'MARKED_BY'].map((v) => ({ label: v, value: v }));

  protected readonly canManage = computed(() => !!this.info()?.canManage);
  /** Counts include unsaved marks (plain state, so not a computed signal). */
  protected counts(): Record<AttStatus | 'unmarked', number> {
    const c = { PRESENT: 0, LATE: 0, ABSENT: 0, EXCUSED: 0, unmarked: 0 };
    for (const r of this.roster()) {
      const s = this.marks[r.studentId!] ?? r.status;
      if (s) c[s]++;
      else c.unmarked++;
    }
    return c;
  }

  ngOnInit(): void {
    this.page.set('Attendance');
    this.call({ operation: 'INIT' }, true, (r) => {
      this.init.set(r);
      this.date = r.today ?? '';
      this.fromDate = r.termStart ?? '';
      this.toDate = r.termEnd ?? '';
      this.editConfig(r.config);
      if (r.canManage) this.tab = 'dashboard';
      this.loadSessions();
    });
  }

  ngOnDestroy(): void {
    this.stopTimer();
  }

  private call(req: AttendanceRequest, initial = false, after?: (r: AttendanceResponse) => void, quiet = false): void {
    if (initial) this.loading.set(true);
    if (!quiet) this.busy.set(true);
    this.rpc.execute<AttendanceResponse>(RPC, req).subscribe({
      next: (r) => {
        this.info.set(r);
        if (r.sessions) this.sessions.set(r.sessions);
        if (r.session) this.current.set(r.session);
        if (r.roster) { this.roster.set(r.roster); if (!this.dirty) { this.marks = {}; this.notes = {}; } }
        if (r.excuses) this.excuses.set(r.excuses);
        if (r.followups) this.followups.set(r.followups);
        if (r.log) this.log.set(r.log);
        if (r.qrToken) this.renderQr(r.qrToken, r.qrSecondsLeft ?? 0);
        this.loading.set(false);
        this.busy.set(false);
        if (r.message && !quiet) this.messages.add({ severity: 'success', summary: 'Attendance', detail: r.message, life: 6000 });
        after?.(r);
      },
      error: (e: ApiError) => {
        this.tabLoading.set(0);
        this.sessionsLoading.set(false);
        this.loading.set(false);
        this.busy.set(false);
        if (!this.info()) this.error.set(e.message);
        else this.messages.add({ severity: 'error', summary: 'Attendance', detail: e.message, life: 9000 });
      },
    });
  }

  /** Tab loads in progress: the tab shows a spinner instead of its (still empty) content until they finish. */
  protected readonly tabLoading = signal(0);

  private loadTab(req: AttendanceRequest): void {
    this.tabLoading.update((n) => n + 1);
    this.call(req, false, () => this.tabLoading.update((n) => Math.max(0, n - 1)));
  }

  protected onTab(t: string | number | undefined): void {
    this.tab = String(t);
    if (this.tab === 'excuses') this.loadTab({ operation: 'EXCUSES' });
    if (this.tab === 'followup') this.loadTab({ operation: 'FOLLOWUPS' });
    if (this.tab === 'history') this.loadTab({ operation: 'LOG' });
    if (this.tab === 'settings') this.editConfig(this.info()?.config);
  }

  // ------------------------------------------------------------------ take attendance

  /** A day's class meetings are loading (date arrows, date picker, My / All classes): the list shows a spinner. */
  protected readonly sessionsLoading = signal(false);

  protected loadSessions(): void {
    this.sessionsLoading.set(true);
    this.call({ operation: 'SESSIONS', date: this.date, scope: this.scope }, false, () => this.sessionsLoading.set(false));
  }

  protected shiftDay(days: number): void {
    if (this.sessionsLoading()) return;
    const d = fromIso(this.date) ?? new Date();
    d.setDate(d.getDate() + days);
    // Local date, not toISOString(): that is UTC, which in Singapore (UTC+8) is still the previous day at local
    // midnight, so the right arrow stayed on the same day and the left arrow skipped one.
    this.date = toIso(d) ?? this.date;
    this.loadSessions();
  }

  protected open(s: SessionInfo): void {
    if (this.dirty) { this.confirmDiscard(() => { this.dirty = false; this.open(s); }); return; }
    this.dirty = false;
    this.qrOpen.set(false);
    this.stopTimer();
    this.call({ operation: 'ROLL', meetingId: s.meetingId }, false, (r) => { if (r.session?.checkinOpen) this.showQr(); });
  }

  protected back(): void {
    if (this.dirty) { this.confirmDiscard(() => { this.dirty = false; this.back(); }); return; }
    this.dirty = false;
    this.current.set(null);
    this.qrOpen.set(false);
    this.stopTimer();
    this.loadSessions();
  }

  /** Unsaved marks on the open roll: ask before throwing them away. */
  private confirmDiscard(then: () => void): void {
    this.confirmation.confirm({
      header: 'Discard unsaved marks',
      message: 'The marks on this roll have not been saved. Discard them?',
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Discard',
      rejectLabel: 'Cancel',
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-text p-button-secondary',
      defaultFocus: 'reject',
      accept: () => then(),
    });
  }

  protected statusOf(r: RosterEntry): AttStatus | undefined {
    return this.marks[r.studentId!] ?? r.status;
  }

  protected setStatus(r: RosterEntry, s: AttStatus): void {
    this.marks[r.studentId!] = s;
    this.dirty = true;
  }

  protected allPresent(): void {
    for (const r of this.roster()) if (!this.statusOf(r)) this.marks[r.studentId!] = r.excused ? 'EXCUSED' : 'PRESENT';
    this.dirty = true;
  }

  protected save(after?: () => void): void {
    const s = this.current();
    if (!s) return;
    const marks = this.roster()
      .filter((r) => this.marks[r.studentId!] !== undefined || this.notes[r.studentId!] !== undefined)
      .map((r) => ({ studentId: r.studentId!, status: (this.marks[r.studentId!] ?? r.status)!, note: this.notes[r.studentId!] ?? r.note }));
    if (!marks.length) { after?.(); return; }
    this.call({ operation: 'MARK', meetingId: s.meetingId, marks, reason: this.reason }, false, () => { this.dirty = false; this.reason = ''; after?.(); });
  }

  protected finalise(): void {
    const s = this.current();
    if (!s) return;
    this.save(() => this.call({ operation: 'FINALISE', meetingId: s.meetingId }, false, () => { this.qrOpen.set(false); this.stopTimer(); }));
  }

  protected reopen(): void {
    const s = this.current();
    if (!s) return;
    const reason = prompt('Reason for reopening this session?');
    if (!reason) return;
    this.call({ operation: 'REOPEN', meetingId: s.meetingId, reason });
  }

  // ------------------------------------------------------------------ QR check-in

  protected openCheckin(): void {
    const s = this.current();
    if (!s) return;
    this.call({ operation: 'OPEN_CHECKIN', meetingId: s.meetingId }, false, () => this.showQr());
  }

  protected closeCheckin(): void {
    const s = this.current();
    if (!s) return;
    this.call({ operation: 'CLOSE_CHECKIN', meetingId: s.meetingId }, false, () => { this.qrOpen.set(false); this.stopTimer(); });
  }

  private showQr(): void {
    this.qrOpen.set(true);
    this.stopTimer();
    this.tick = 0;
    this.timer = setInterval(() => {
      this.qrSeconds.update((x) => Math.max(0, x - 1));
      this.tick++;
      const s = this.current();
      if (!s) return;
      if (this.qrSeconds() <= 0) this.call({ operation: 'QR', meetingId: s.meetingId }, false, undefined, true);
      if (this.tick % 5 === 0 && !this.dirty) this.call({ operation: 'ROLL', meetingId: s.meetingId }, false, (r) => { if (!r.session?.checkinOpen) { this.qrOpen.set(false); this.stopTimer(); } }, true);
    }, 1000);
  }

  private stopTimer(): void {
    if (this.timer) clearInterval(this.timer);
    this.timer = null;
  }

  private renderQr(token: string, seconds: number): void {
    const s = this.current();
    if (!s) return;
    const url = `${window.location.origin}/attend?m=${s.meetingId}&t=${token}`;
    this.qrSeconds.set(seconds);
    QRCode.toDataURL(url, { width: 420, margin: 1, errorCorrectionLevel: 'M' }).then((img) => this.qrImage.set(img));
    this.qrUrl = url;
  }

  protected qrUrl = '';

  // ------------------------------------------------------------------ excuses

  protected decide(): void {
    const d = this.decideFor();
    if (!d) return;
    this.call({ operation: 'DECIDE_EXCUSE', id: d.e.id, decision: d.approve ? 'APPROVE' : 'REJECT', note: this.decisionNote }, false, () => { this.decideFor.set(null); this.decisionNote = ''; });
  }

  protected viewFile(e: Excuse): void {
    this.call({ operation: 'EXCUSE_FILE', id: e.id }, false, (r) => {
      const x = r.excuse;
      if (!x?.fileBase64) return;
      this.fileView.set({ name: x.fileName ?? 'document', type: x.fileType ?? 'application/octet-stream', url: `data:${x.fileType};base64,${x.fileBase64}` });
    });
  }

  protected excuseLabel(t?: string): string {
    return t === 'MC' ? 'Medical certificate' : t === 'LEAVE' ? 'Leave' : t === 'OFFICIAL' ? 'Official activity' : (t ?? '');
  }

  // ------------------------------------------------------------------ follow-up

  protected readonly visibleCases = computed(() => {
    const filter = this.caseFilter();
    return this.followups().filter((f) => filter === 'ALL' || (filter === 'OPEN' ? f.status !== 'CLOSED' : f.status === filter));
  });

  protected evaluate(): void {
    this.call({ operation: 'EVALUATE' });
  }

  protected editCase(f: Followup): void {
    this.caseEdit.set({ ...f, nextReview: f.nextReview ? String(f.nextReview).substring(0, 10) : undefined });
  }

  protected saveCase(): void {
    const f = this.caseEdit();
    if (!f) return;
    this.call({ operation: 'UPDATE_FOLLOWUP', id: f.id, status: f.status, note: f.notes, outcome: f.outcome, nextReview: f.nextReview ?? '' }, false, () => this.caseEdit.set(null));
  }

  protected ruleLabel(r?: string): string {
    return r === 'ELIGIBILITY_RISK' ? 'Exam eligibility at risk' : r === 'BELOW_MIN' ? 'Below minimum' : r === 'CONSECUTIVE' ? 'Consecutive absences' : (r ?? '');
  }

  // ------------------------------------------------------------------ reports, settings, export

  /** A dashboard alert opens the tab or report that resolves it (tab:<tab> or report:<id>). */
  protected goTo(target: string): void {
    const [kind, id] = target.split(':');
    if (kind === 'report') {
      this.reportToOpen = id;
      this.onTab('reports');
    } else this.onTab(id);
  }

  protected rateClass(r?: number): string {
    const c = this.info()?.config;
    if (r == null || !c) return '';
    if (c.eligibilityPct != null && r < c.eligibilityPct) return 'bad';
    if (r < c.minPct) return 'warn';
    return 'ok';
  }

  /** Copies a configuration for editing; the multi-selects get stable arrays (a fresh array per change detection loops). */
  private editConfig(c?: AttendanceConfig): void {
    if (!c) return;
    this.config = { ...c };
    this.recipientSel = (c.alertRecipients ?? '').split(',').filter((x) => x);
    this.exportSel = (c.exportColumns ?? '').split(',').filter((x) => x);
  }

  protected saveConfig(): void {
    if (!this.config) return;
    this.config.alertRecipients = this.recipientSel.join(',');
    this.config.exportColumns = this.exportSel.join(',');
    this.call({ operation: 'SAVE_CONFIG', config: this.config }, false, (r) => this.editConfig(r.config));
  }

  protected exportSims(): void {
    this.call({ operation: 'EXPORT', fromDate: this.fromDate, toDate: this.toDate }, false, (r) => {
      downloadCsv(`attendance-sims-${this.fromDate}-${this.toDate}`, r.exportColumns ?? [], r.exportRows ?? []);
      this.messages.add({ severity: 'success', summary: 'SIMS export', detail: `${r.exportRows?.length ?? 0} attendance records exported.` });
    });
  }
}
