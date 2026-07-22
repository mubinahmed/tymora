// DTOs for the additive chameleon (masquerade) command beans
// (org.unitime.timetable.rest.chameleon.*). Hand-written — outside the
// generator's gwt.shared/gwt.client scan. Plain field names, JSON keys match.

export interface ChameleonUserItem {
  puid?: string;
  name?: string;
}

export interface GetChameleonUsersResponse {
  currentName?: string;
  masquerading?: boolean;
  users?: ChameleonUserItem[];
}

export interface ChameleonSwitchRequest {
  puid?: string;
  name?: string;
}
