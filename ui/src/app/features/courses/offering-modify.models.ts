// DTOs for the additive OfferingModifyRequest / OfferingModifyUpdateRequest command
// beans (org.unitime.timetable.gwt.shared.OfferingModifyInterface).

export interface ConfigInfo {
  configId?: number;
  name?: string;
  limit?: number | null;
  unlimited?: boolean;
  subparts?: string[];
}

export interface OfferingModifyResponse {
  offeringId?: number;
  offeringName?: string;
  saved?: boolean;
  configs?: ConfigInfo[];
}

export interface OfferingModifyRequest {
  offeringId?: number;
}

export interface ConfigEdit {
  configId?: number;
  name?: string;
  limit?: number | null;
}

export interface OfferingModifyUpdateRequest {
  offeringId?: number;
  configs?: ConfigEdit[];
}
