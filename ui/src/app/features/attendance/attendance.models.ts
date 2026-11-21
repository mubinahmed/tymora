/** Models for AttendanceRequest / MyAttendanceRequest (org.unitime.timetable.gwt.shared.AttendanceInterface). */
import { LogEntry } from '../seating/seating.models';

export interface AttendanceConfig {
  minPct: number;
  eligibilityPct: number | null;
  lateMinutes: number;
  checkinMinutes: number;
  rollcallEnabled: boolean;
  qrEnabled: boolean;
  qrRotationSec: number;
  oneDevice: boolean;
  lateCountsPresent: boolean;
  excusedCountsPresent: boolean;
  consecutiveAbsences: number;
  minSessions: number;
  alertRecipients: string;
  excuseApprover: string;
  notifyStudent: boolean;
  exportColumns: string;
}

export type AttStatus = 'PRESENT' | 'LATE' | 'ABSENT' | 'EXCUSED';

export interface SessionInfo {
  meetingId?: number;
  classId?: number;
  attendanceSessionId?: number;
  className?: string;
  course?: string;
  itype?: string;
  date?: string;
  time?: string;
  room?: string;
  instructors?: string;
  takerName?: string;
  status?: 'NOT_TAKEN' | 'OPEN' | 'FINALISED';
  enrolled?: number;
  present?: number;
  late?: number;
  absent?: number;
  excused?: number;
  relief?: boolean;
  canTake?: boolean;
  checkinOpen?: boolean;
  checkinUntil?: string;
}

export interface RosterEntry {
  studentId?: number;
  externalId?: string;
  name?: string;
  status?: AttStatus;
  source?: string;
  note?: string;
  markedTs?: string;
  markedBy?: string;
  excused?: boolean;
  ratePct?: number;
}

export interface Excuse {
  id?: number;
  studentId?: number;
  studentName?: string;
  studentExternalId?: string;
  type?: string;
  note?: string;
  fileName?: string;
  fileType?: string;
  status?: string;
  decidedBy?: string;
  decisionNote?: string;
  fromDate?: string;
  toDate?: string;
  submittedTs?: string;
  decidedTs?: string;
  canDecide?: boolean;
  hasFile?: boolean;
  fileBase64?: string;
  from?: string;
  to?: string;
}

export interface Followup {
  id?: number;
  studentId?: number;
  classId?: number;
  studentName?: string;
  studentExternalId?: string;
  className?: string;
  rule?: string;
  detail?: string;
  status?: string;
  recipients?: string;
  notes?: string;
  outcome?: string;
  updatedBy?: string;
  createdTs?: string;
  nextReview?: string;
  updatedTs?: string;
  ratePct?: number;
}

export interface ReportRow {
  key?: string;
  label?: string;
  sessions?: number;
  records?: number;
  present?: number;
  late?: number;
  absent?: number;
  excused?: number;
  ratePct?: number;
}

export interface AttendanceRequest {
  operation: string;
  date?: string;
  fromDate?: string;
  toDate?: string;
  scope?: string;
  meetingId?: number;
  classId?: number;
  id?: number;
  marks?: { studentId: number; status: string; note?: string }[];
  reason?: string;
  note?: string;
  status?: string;
  outcome?: string;
  decision?: string;
  nextReview?: string;
  dimension?: string;
  config?: AttendanceConfig;
  report?: string;
  period?: string;
}

export interface AttendanceResponse {
  canManage?: boolean;
  canTake?: boolean;
  config?: AttendanceConfig;
  termStart?: string;
  termEnd?: string;
  today?: string;
  sessions?: SessionInfo[];
  session?: SessionInfo;
  roster?: RosterEntry[];
  qrToken?: string;
  qrSecondsLeft?: number;
  excuses?: Excuse[];
  excuse?: Excuse;
  followups?: Followup[];
  report?: ReportRow[];
  exportColumns?: string[];
  exportRows?: string[][];
  log?: LogEntry[];
  pendingExcuses?: number;
  openFollowups?: number;
  message?: string;
  dashboard?: Dashboard;
  table?: ReportTable;
  reports?: ReportInfo[];
  lastRecorded?: string;
}

export interface ReportInfo {
  id: string;
  title: string;
  description: string;
  /** DATE, RANGE, NONE, plus DIMENSION */
  params: string;
}

export interface ReportColumn {
  name: string;
  /** text, num, pct (bar against the thresholds), share (neutral bar), status (tag), date */
  type: string;
}

export interface ReportTable {
  id: string;
  title: string;
  description?: string;
  note?: string;
  columns: ReportColumn[];
  rows: string[][];
  summary: string[][];
}

export interface TrendPoint {
  date: string;
  ratePct: number;
  records: number;
}

export interface DashboardAlert {
  key: string;
  label: string;
  detail: string;
  severity: 'ok' | 'info' | 'warning' | 'critical';
  /** tab:<tab> or report:<id> */
  target: string;
  count: number;
}

export interface Dashboard {
  date: string;
  period: string;
  groupBy: string;
  periodFrom: string;
  periodTo: string;
  expected: number;
  present: number;
  late: number;
  partial: number;
  absent: number;
  excused: number;
  notRecorded: number;
  sessionsScheduled: number;
  sessionsFinalised: number;
  sessionsOpen: number;
  sessionsNotTaken: number;
  dayRatePct?: number;
  weekRatePct?: number;
  termRatePct?: number;
  studentsBelowMin: number;
  studentsAtRisk: number;
  byGroup: ReportRow[];
  lowestClasses: ReportRow[];
  trend: TrendPoint[];
  alerts: DashboardAlert[];
}

export interface ClassStat {
  classId?: number;
  className?: string;
  course?: string;
  instructors?: string;
  sessions?: number;
  present?: number;
  late?: number;
  absent?: number;
  excused?: number;
  ratePct?: number;
  standing?: 'OK' | 'BELOW_MIN' | 'ELIGIBILITY_RISK';
}

export interface MyAttendanceResponse {
  student?: boolean;
  name?: string;
  config?: AttendanceConfig;
  classes?: ClassStat[];
  records?: { className?: string; date?: string; time?: string; room?: string; status?: string; source?: string }[];
  excuses?: Excuse[];
  warnings?: string[];
  overallPct?: number;
  checkinStatus?: string;
  checkinClass?: string;
  message?: string;
}
