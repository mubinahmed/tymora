// DTOs for the additive user-edit command beans
// (org.unitime.timetable.gwt.shared.UserEditInterface) — Users / Database Authentication.

export interface UserInfo {
  externalId?: string;
  name?: string;
  managerName?: string;
  token?: string;
}

export interface UserListResponse {
  showTokens?: boolean;
  users?: UserInfo[];
}

export interface UserEditData {
  newUser?: boolean;
  showTokens?: boolean;
  externalId?: string;
  name?: string;
  token?: string;
}

export interface UserEditLoadRequest {
  externalId?: string;
}

export interface UserSaveRequest {
  newUser?: boolean;
  externalId?: string;
  name?: string;
  password?: string;
}

export interface UserDeleteRequest {
  externalId?: string;
}
