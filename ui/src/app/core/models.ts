// DTOs come from the generated file (reflection over org.unitime.timetable.gwt.shared,
// matching the facade's Gson naming). Regenerate with the TsModelGenerator tool;
// do not hand-edit the generated file. App-specific (non-DTO) types live here.

export * from './generated/models.generated';

/** Normalized error shape emitted by the HTTP interceptor. */
export interface ApiError {
  status: number;
  message: string;
}
