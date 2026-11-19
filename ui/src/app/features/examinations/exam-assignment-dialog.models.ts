import { AssignmentChangeInfo } from '../approval/change-requests.models';

/**
 * Models for the ExamAssignmentDialogRequest command bean
 * (org.unitime.timetable.gwt.shared.ExamAssignmentDialogInterface). Field names follow the
 * facade's Gson naming (iField -> field).
 */
export type ExamDialogOperation =
  | 'LOAD'
  | 'REFRESH'
  | 'SELECT_PERIOD'
  | 'SELECT_ROOMS'
  | 'SELECT_EXAM'
  | 'REMOVE'
  | 'SUGGESTIONS'
  | 'SELECT_SUGGESTION'
  | 'ASSIGN'
  | 'CLEAR';

export interface ExamAssignmentDialogRequest {
  operation: ExamDialogOperation;
  examId?: number;
  periodId?: number;
  roomIds?: number[];
  suggestionIndex?: number;
  minRoomSize?: number | null;
  maxRoomSize?: number | null;
  roomFilter?: string | null;
  allowRoomConflict?: boolean;
  roomOrder?: string;
  depth?: number;
  timeout?: number;
  filter?: string | null;
  /** ASSIGN: reason for the change (required) */
  reason?: string;
}

export interface ExamConflict {
  /** Direct | MoreThanTwoADay | BackToBack | DistanceBackToBack | Distribution */
  type?: string;
  instructor?: boolean;
  count?: number;
  other?: string;
  otherTime?: string;
  preference?: string;
}

export interface ExamPlacement {
  periodId?: number;
  period?: string;
  periodPref?: string;
  roomIds?: number[];
  rooms?: string;
  roomCapacity?: number;
  valid?: boolean;
  direct?: number;
  moreThanTwoADay?: number;
  backToBack?: number;
  distanceBackToBack?: number;
  instructorDirect?: number;
  instructorMoreThanTwoADay?: number;
  instructorBackToBack?: number;
  distribution?: number;
  conflicts?: ExamConflict[];
}

export interface ExamPeriodOption {
  periodId?: number;
  name?: string;
  pref?: string;
  initial?: boolean;
  selected?: boolean;
  direct?: number;
  moreThanTwoADay?: number;
  backToBack?: number;
  distanceBackToBack?: number;
  instructorDirect?: number;
  instructorMoreThanTwoADay?: number;
  instructorBackToBack?: number;
  distributionConflicts?: string[];
}

export interface ExamRoomOption {
  roomId?: number;
  name?: string;
  capacity?: number;
  pref?: string;
  hard?: boolean;
  selected?: boolean;
  initial?: boolean;
}

export interface ExamChangeLine {
  examId?: number;
  exam?: string;
  unassigned?: boolean;
  selected?: boolean;
  roomsMissing?: boolean;
  fromPeriod?: string;
  toPeriod?: string;
  fromRooms?: string;
  toRooms?: string;
  direct?: number;
  directBefore?: number;
  moreThanTwoADay?: number;
  moreThanTwoADayBefore?: number;
  backToBack?: number;
  backToBackBefore?: number;
}

export interface ExamChange {
  value?: number;
  nrAssigned?: number;
  nrUnassigned?: number;
  lines?: ExamChangeLine[];
}

export interface ExamAssignmentDialogResponse {
  examId?: number;
  examName?: string;
  examType?: string;
  nrStudents?: number;
  maxRooms?: number;
  length?: number;
  seatingType?: string;
  solverMode?: boolean;
  canComputeSuggestions?: boolean;
  canAssign?: boolean;
  assignConfirm?: string;
  assigned?: boolean;
  message?: string;
  current?: ExamPlacement;
  selected?: ExamPlacement;
  periods?: ExamPeriodOption[];
  rooms?: ExamRoomOption[];
  change?: ExamChange;
  suggestions?: ExamChange[];
  suggestionsMessage?: string;
  suggestionsTimeout?: boolean;
  minRoomSize?: number;
  maxRoomSize?: number;
  roomFilter?: string;
  roomOrder?: string;
  allowRoomConflict?: boolean;
  depth?: number;
  timeout?: number;
  /** whether a change by this user (in this mode) needs approval */
  needsApproval?: boolean;
  /** ASSIGN stored the change as a pending request instead of applying it */
  submitted?: boolean;
  changeRequestId?: number;
  pendingRequests?: AssignmentChangeInfo[];
}
