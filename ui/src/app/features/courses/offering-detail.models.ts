// DTOs for the additive InstructionalOfferingDetailRequest command bean
// (org.unitime.timetable.gwt.shared.InstructionalOfferingDetailInterface).
// Hand-written to match the Gson iName->name field mapping (JSON keys are the
// Java fields with the leading `i` stripped and lower-cased).

export interface CoordinatorInfo {
  instructorId?: number;
  name?: string;
  share?: string;
}

export interface CrossListInfo {
  courseId?: number;
  course?: string;
  title?: string;
  controlling?: boolean;
  reservation?: string;
}

export interface ClassInfo {
  id?: number;
  section?: string;
  limit?: string;
  enrollment?: string;
  time?: string;
  room?: string;
  instructors?: string;
}

export interface SubpartInfo {
  id?: number;
  type?: string;
  indent?: number;
  classes?: ClassInfo[];
}

export interface ConfigInfo {
  id?: number;
  name?: string;
  limit?: string;
  unlimited?: boolean;
  subparts?: SubpartInfo[];
}

export interface OfferingDetailResponse {
  offeringId?: number;
  controllingCourseId?: number;
  courseName?: string;
  title?: string;
  offered?: boolean;
  unlimited?: boolean;
  byReservationOnly?: boolean;
  consent?: string;
  credit?: string;
  waitList?: string;
  notes?: string;
  enrollment?: number;
  limit?: number;
  snapshotLimit?: number;
  demand?: number;
  projectedDemand?: number;
  coordinators?: CoordinatorInfo[];
  crossListings?: CrossListInfo[];
  configs?: ConfigInfo[];
}

export interface OfferingDetailRequest {
  offeringId?: number;
  courseOfferingId?: number;
}
