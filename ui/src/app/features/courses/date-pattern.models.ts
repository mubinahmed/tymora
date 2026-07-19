// DTOs for the additive DispDatePatternRequest command bean
// (org.unitime.timetable.gwt.shared.DispDatePatternInterface).

export interface DispDatePatternResponse {
  id?: number;
  name?: string;
  type?: string;
  numberOfWeeks?: number;
  startDate?: string; // yyyy-MM-dd
  endDate?: string; // yyyy-MM-dd
  activeDates?: string[]; // yyyy-MM-dd
}

export interface DispDatePatternRequest {
  datePatternId?: number;
  classId?: number;
  subpartId?: number;
}
