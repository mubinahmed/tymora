// DTOs for the additive ClassEditRequest / ClassEditUpdateRequest command beans
// (org.unitime.timetable.gwt.shared.ClassEditInterface). Hand-written to match
// the Gson iName->name field mapping.

export interface IdName {
  id?: number;
  name?: string;
}

export interface ClassEditResponse {
  classId?: number;
  offeringId?: number;
  subpartId?: number;
  className?: string;
  section?: string;
  itypeDesc?: string;
  courseName?: string;
  courseTitle?: string;
  managingDept?: string;
  lms?: string;
  unlimited?: boolean;
  datePatternEditable?: boolean;
  saved?: boolean;
  expectedCapacity?: number;
  maxExpectedCapacity?: number;
  roomRatio?: number;
  nbrRooms?: number;
  splitAttendance?: boolean;
  datePatternId?: number;
  notes?: string;
  schedulePrintNote?: string;
  enabledForStudentScheduling?: boolean;
  displayInstructor?: boolean;
  datePatternOptions?: IdName[];
}

export interface ClassEditRequest {
  classId?: number;
}

export interface ClassEditUpdateRequest {
  classId?: number;
  expectedCapacity?: number | null;
  maxExpectedCapacity?: number | null;
  roomRatio?: number | null;
  nbrRooms?: number | null;
  splitAttendance?: boolean;
  datePatternId?: number | null;
  notes?: string;
  schedulePrintNote?: string;
  enabledForStudentScheduling?: boolean;
  displayInstructor?: boolean;
}
