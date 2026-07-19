// DTOs for the additive CrossListsRequest / CrossListsUpdateRequest command beans
// (org.unitime.timetable.gwt.shared.CrossListsInterface).

export interface CrossCourse {
  courseId?: number;
  courseName?: string;
  title?: string;
  controlling?: boolean;
  reservation?: number | null;
  canDelete?: boolean;
}

export interface CrossListsResponse {
  offeringId?: number;
  controllingCourseId?: number;
  offeringName?: string;
  ioLimit?: number;
  unlimited?: boolean;
  singleCourseLimit?: boolean;
  saved?: boolean;
  courses?: CrossCourse[];
}

export interface CrossListsRequest {
  offeringId?: number;
}

export interface CourseReservation {
  courseId?: number;
  reservation?: number | null;
}

export interface CrossListsUpdateRequest {
  offeringId?: number;
  controllingCourseId?: number;
  courses?: CourseReservation[];
}
