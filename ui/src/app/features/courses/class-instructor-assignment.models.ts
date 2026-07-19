// DTOs mirroring org.unitime.timetable.gwt.shared.AssignClassInstructorsInterface,
// the existing GWT-RPC "SimpleEdit"-style grid reused as-is (no new backend).
// The grid is generic: `fields` are columns (in DataColumn order) and `records`
// are rows of parallel string `values`. Toggles are "true"/"false"; list values
// are ids matching a field's ListItem.value.

export interface ListItem {
  value?: string;
  text?: string;
}

export interface AciField {
  name?: string;
  type?: string; // textarea | number | toggle | list | add | delete | hasError
  length?: number;
  width?: number;
  height?: number;
  flags?: number;
  values?: ListItem[];
}

export interface AciRecord {
  uniqueId?: number | null;
  values?: (string | null)[];
  editable?: boolean[];
  visible?: boolean[];
  deletable?: boolean;
}

export interface AssignClassInstructorsData {
  records?: AciRecord[];
  fields?: AciField[];
  editable?: boolean;
  configId?: number;
  offeringId?: number;
  saveSuccessful?: boolean;
  errors?: string | null;
  nextConfigId?: number;
  previousConfigId?: number;
  courseName?: string;
  courseCoordinators?: string;
  showTimeAndRoom?: boolean;
}

/** Column indices, fixed to the backend's DataColumn enum order. */
export const ACI = {
  CLASS_UID: 0,
  CLASS_PARENT_UID: 1,
  IS_FIRST: 2,
  HAS_ERROR: 3,
  CLASS_NAME: 4,
  EXTERNAL_UID: 5,
  DELETE: 6,
  ADD: 7,
  INSTR: 8,
  PCT_SHARE: 9,
  LEAD: 10,
  RESPONSIBILITY: 11,
  DISPLAY: 12,
  TIME: 13,
  ROOM: 14,
  FUNDING_DEPT: 15,
} as const;

/** A class and the instructor rows (records) that belong to it. */
export interface ClassGroup {
  classUid: string;
  nameHtml: string;
  timeHtml: string;
  roomHtml: string;
  head: AciRecord; // first record — carries the visible class name / time / room / display / funding
  records: AciRecord[];
}
