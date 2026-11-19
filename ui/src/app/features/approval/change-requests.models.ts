import { SelectedAssignment } from '../../core/models';

/**
 * Models for the AssignmentChangeRequest command bean
 * (org.unitime.timetable.gwt.shared.AssignmentChangeInterface).
 */
export type ChangeOperation = 'INFO' | 'SUBMIT_CLASS' | 'LIST' | 'APPROVE' | 'REJECT' | 'CANCEL' | 'MARK_SEEN';
export type ChangeStatus = 'PENDING' | 'APPLIED' | 'REJECTED' | 'CANCELLED' | 'FAILED';

export interface AssignmentChangeRequest {
  operation: ChangeOperation;
  id?: number;
  classId?: number;
  assignments?: SelectedAssignment[];
  reason?: string;
  summary?: string;
  note?: string;
  /** LIST: PENDING | MINE | ALL */
  filter?: string;
  subjectId?: number;
}

export interface AssignmentChangeInfo {
  id?: number;
  type?: 'CLASS' | 'EXAM';
  mode?: 'SOLVER' | 'DATABASE';
  status?: ChangeStatus;
  subjectId?: number;
  subjectName?: string;
  summary?: string;
  reason?: string;
  requestedBy?: string;
  requestedName?: string;
  requestedTs?: string;
  decidedName?: string;
  decidedTs?: string;
  decisionNote?: string;
  resultMessage?: string;
  mine?: boolean;
  canDecide?: boolean;
  canCancel?: boolean;
  selfApproved?: boolean;
}

export interface AssignmentChangeResponse {
  policy?: string;
  canApprove?: boolean;
  canRequest?: boolean;
  classNeedsApproval?: boolean;
  examSolverNeedsApproval?: boolean;
  examDatabaseNeedsApproval?: boolean;
  pendingCount?: number;
  unseenDecisions?: number;
  myPendingCount?: number;
  changes?: AssignmentChangeInfo[];
  change?: AssignmentChangeInfo;
  message?: string;
}

export const MIN_REASON_LENGTH = 5;
