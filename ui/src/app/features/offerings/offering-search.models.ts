// DTOs for the additive offering-search command bean
// (org.unitime.timetable.rest.offerings.*). Hand-written because these live
// outside the gwt.shared/gwt.client packages the generator scans. Plain field
// names, so the JSON keys match directly.

export interface SubjectAreaItem {
  id?: number;
  abbreviation?: string;
  title?: string;
}

export interface OfferingRow {
  id?: number;
  courseName?: string;
  title?: string;
  offered?: boolean;
  enrollment?: number;
}

export interface SearchOfferingsRequest {
  subjectAreaId?: number;
  courseNumber?: string;
}

export interface SearchOfferingsResponse {
  subjectAreas?: SubjectAreaItem[];
  offerings?: OfferingRow[];
  canAdd?: boolean;
}
