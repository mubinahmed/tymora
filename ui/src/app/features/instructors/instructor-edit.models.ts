// DTOs for the additive instructor add/edit command beans
// (org.unitime.timetable.gwt.shared.InstructorEditInterface).

export interface IdName {
  id?: number;
  name?: string;
}

export interface InstructorEditResponse {
  instructorId?: number;
  departmentId?: number;
  deptName?: string;
  deptCode?: string;
  fname?: string;
  mname?: string;
  lname?: string;
  title?: string;
  externalId?: string;
  careerAcct?: string;
  email?: string;
  note?: string;
  positionTypeId?: number;
  ignoreTooFar?: boolean;
  saved?: boolean;
  deleted?: boolean;
  positionTypes?: IdName[];
  departments?: IdName[];
}

export interface InstructorEditRequest {
  instructorId?: number;
}

export interface InstructorDeleteRequest {
  instructorId?: number;
}

export interface InstructorSaveRequest {
  instructorId?: number | null;
  departmentId?: number | null;
  fname?: string;
  mname?: string;
  lname?: string;
  title?: string;
  externalId?: string;
  careerAcct?: string;
  email?: string;
  positionTypeId?: number | null;
  note?: string;
  ignoreTooFar?: boolean;
}
