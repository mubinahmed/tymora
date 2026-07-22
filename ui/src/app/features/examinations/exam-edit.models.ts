// DTOs for the additive exam detail/edit command beans
// (org.unitime.timetable.gwt.shared.ExamEditInterface).

export interface IdName {
  id?: number;
  name?: string;
}

export interface ExamEditResponse {
  examId?: number;
  label?: string;
  name?: string;
  note?: string;
  length?: number;
  examSize?: number;
  sizeText?: string;
  printOffset?: number;
  seatingType?: number;
  seatingTypeLabel?: string;
  maxNbrRooms?: number;
  examTypeId?: number;
  examTypeLabel?: string;
  assignedPeriod?: string;
  assignedRoom?: string;
  avgPeriod?: string;
  saved?: boolean;
  instructors?: string[];
  owners?: string[];
  seatingOptions?: IdName[];
}

export interface ExamDetailRequest {
  examId?: number;
}

export interface ExamEditRequest {
  examId?: number;
}

export interface ExamEditUpdateRequest {
  examId?: number;
  name?: string;
  note?: string;
  length?: number | null;
  seatingType?: number;
  examSize?: number | null;
  printOffset?: number | null;
  maxNbrRooms?: number | null;
}
