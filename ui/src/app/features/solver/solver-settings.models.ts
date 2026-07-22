// DTOs for the additive solver-configuration command beans
// (org.unitime.timetable.gwt.shared.SolverSettingsInterface).

export interface SettingInfo {
  id?: number;
  name?: string;
  description?: string;
  appearanceLabel?: string;
}

export interface SolverSettingListResponse {
  settings?: SettingInfo[];
}

export interface ParamInfo {
  defId?: number;
  name?: string;
  description?: string;
  type?: string;
  defaultValue?: string;
  value?: string;
  useDefault?: boolean;
}

export interface GroupInfo {
  name?: string;
  description?: string;
  params?: ParamInfo[];
}

export interface SolverSettingEditResponse {
  id?: number;
  name?: string;
  description?: string;
  appearanceLabel?: string;
  saved?: boolean;
  groups?: GroupInfo[];
}

export interface SolverSettingListRequest {}
export interface SolverSettingEditRequest {
  settingId?: number;
}
export interface SolverSettingDeleteRequest {
  settingId?: number;
}

export interface ParamValue {
  defId?: number;
  value?: string;
  useDefault?: boolean;
}

export interface SolverSettingUpdateRequest {
  settingId?: number;
  name?: string;
  description?: string;
  params?: ParamValue[];
}
