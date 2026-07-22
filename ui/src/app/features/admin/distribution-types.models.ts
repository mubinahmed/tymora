// DTOs for the additive distribution-type edit command beans
// (org.unitime.timetable.gwt.shared.DistributionTypeEditInterface).

export interface IdName {
  id?: number;
  name?: string;
}

export interface DistTypeInfo {
  id?: number;
  reference?: string;
  label?: string;
  abbreviation?: string;
  descr?: string;
  instructorPref?: boolean;
  examPref?: boolean;
  survey?: boolean;
  visible?: boolean;
  allowedPrefIds?: number[];
  departmentIds?: number[];
}

export interface DistTypeListResponse {
  saved?: boolean;
  types?: DistTypeInfo[];
  departments?: IdName[];
  prefLevels?: IdName[];
}

export interface DistTypeListRequest {}

export interface DistTypeUpdateRequest {
  id?: number;
  label?: string;
  abbreviation?: string;
  descr?: string;
  instructorPref?: boolean;
  survey?: boolean;
  visible?: boolean;
  allowedPrefIds?: number[];
  departmentIds?: number[];
}
