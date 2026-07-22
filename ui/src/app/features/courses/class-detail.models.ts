// DTOs for the additive ClassDetailRequest command bean
// (org.unitime.timetable.gwt.shared.ClassDetailInterface). Hand-written to match
// the Gson iName->name field mapping.

export interface ClassInstructorInfo {
  instructorId?: number;
  name?: string;
  share?: string;
  lead?: boolean;
}

export interface ClassDetailResponse {
  classId?: number;
  offeringId?: number;
  subpartId?: number;
  parentClassId?: number;
  className?: string;
  section?: string;
  itypeDesc?: string;
  courseName?: string;
  courseTitle?: string;
  parentClassName?: string;
  crosslisted?: boolean;
  cancelled?: boolean;
  displayInstructor?: boolean;
  enabledForStudentScheduling?: boolean;
  splitAttendance?: boolean;
  expectedCapacity?: string;
  enrollment?: string;
  snapshotLimit?: string;
  datePattern?: string;
  datePatternId?: number;
  roomRatio?: string;
  nbrRooms?: number;
  managingDept?: string;
  fundingDept?: string;
  lms?: string;
  notes?: string;
  schedulePrintNote?: string;
  time?: string;
  room?: string;
  instructors?: ClassInstructorInfo[];
}

export interface ClassDetailRequest {
  classId?: number;
}
