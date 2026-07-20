// DTOs for the additive enrollment-audit-report command beans
// (org.unitime.timetable.gwt.shared.EnrollmentAuditReportInterface).

export interface Option {
  value?: string;
  label?: string;
}

export interface SubjectAreaOption {
  id?: number;
  abbreviation?: string;
  title?: string;
}

export interface EnrollmentAuditInitResponse {
  reports?: Option[];
  modes?: Option[];
  subjectAreas?: SubjectAreaOption[];
  defaultMode?: string;
}

export interface EnrollmentAuditGenerateRequest {
  reports?: string[];
  mode?: string;
  all?: boolean;
  subjectIds?: number[];
  showExternalId?: boolean;
  showStudentName?: boolean;
}

export interface EnrollmentAuditReportResponse {
  fileName?: string;
  contentType?: string;
  content?: number[];
}
