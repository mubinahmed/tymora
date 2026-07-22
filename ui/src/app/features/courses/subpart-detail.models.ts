// DTOs for the additive SchedulingSubpartDetailRequest command bean
// (org.unitime.timetable.gwt.shared.SchedulingSubpartDetailInterface).
// Hand-written to match the Gson iName->name field mapping.

export interface SubpartClassInfo {
  id?: number;
  section?: string;
  limit?: string;
  enrollment?: string;
  time?: string;
  room?: string;
  instructors?: string;
}

export interface SubpartDetailResponse {
  subpartId?: number;
  offeringId?: number;
  parentSubpartId?: number;
  instructionalTypeLabel?: string;
  courseName?: string;
  courseTitle?: string;
  subjectArea?: string;
  courseNbr?: string;
  datePattern?: string;
  datePatternId?: number;
  credit?: string;
  managingDept?: string;
  parentSubpartLabel?: string;
  minutesPerWeek?: number;
  unlimitedEnroll?: boolean;
  autoSpreadInTime?: boolean;
  studentAllowOverlap?: boolean;
  classes?: SubpartClassInfo[];
}

export interface SubpartDetailRequest {
  subpartId?: number;
}
