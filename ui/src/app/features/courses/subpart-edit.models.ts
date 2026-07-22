// DTOs for the additive SubpartEditRequest / SubpartEditUpdateRequest command beans
// (org.unitime.timetable.gwt.shared.SchedulingSubpartEditInterface). Hand-written
// to match the Gson iName->name field mapping.

export interface IdName {
  id?: number;
  name?: string;
}

export interface SubpartEditResponse {
  subpartId?: number;
  offeringId?: number;
  parentSubpartId?: number;
  instructionalTypeLabel?: string;
  courseName?: string;
  courseTitle?: string;
  subjectArea?: string;
  courseNbr?: string;
  creditText?: string;
  managingDept?: string;
  parentSubpartLabel?: string;
  minutesPerWeek?: number;
  instructionalType?: number;
  datePatternId?: number;
  unlimited?: boolean;
  datePatternEditable?: boolean;
  saved?: boolean;
  autoSpreadInTime?: boolean;
  studentAllowOverlap?: boolean;
  datePatternOptions?: IdName[];
  itypeOptions?: IdName[];
}

export interface SubpartEditRequest {
  subpartId?: number;
}

export interface SubpartEditUpdateRequest {
  subpartId?: number;
  instructionalType?: number | null;
  datePatternId?: number | null;
  autoSpreadInTime?: boolean;
  studentAllowOverlap?: boolean;
}
