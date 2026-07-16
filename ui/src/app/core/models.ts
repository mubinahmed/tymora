// DTOs come from the generated file (reflection over org.unitime.timetable.gwt.shared,
// matching the facade's Gson naming). Regenerate with the TsModelGenerator tool;
// do not hand-edit the generated file. App-specific (non-DTO) types live here.

export * from './generated/models.generated';

/** Normalized error shape emitted by the HTTP interceptor. */
export interface ApiError {
  status: number;
  message: string;
}

/**
 * SimpleListInterface DTOs — generic read-only tabular listing (SimpleListBackend,
 * page key -> permission-gated entity list). Hand-declared here (not in the
 * generated file) because the bean was added as part of the Angular migration.
 */
export interface SimpleListRow {
  id?: number;
  cells?: string[];
}
export interface SimpleListResponse {
  title?: string;
  columns?: string[];
  rows?: SimpleListRow[];
}
export interface SimpleListRequest {
  page?: string;
}

/**
 * `RoomFilterRpcRequest` is an AMBIGUOUS simple name — two command beans register
 * it with different classes (EventInterface.RoomFilterRpcRequest -> RoomFilterBackend,
 * and RoomInterface.RoomFilterRpcRequest -> RoomDetailsBackend). The facade drops
 * ambiguous simple names, so callers must use the fully-qualified name. We want the
 * Events one (room enumeration -> FilterRpcResponse). `$` = Java nested-class separator.
 */
export const RPC_ROOM_FILTER = 'org.unitime.timetable.gwt.shared.EventInterface$RoomFilterRpcRequest';

/**
 * The OTHER RoomFilterRpcRequest — RoomInterface.RoomFilterRpcRequest -> RoomDetailsBackend
 * (gated by Right.Rooms). Its ENUMERATE returns full RoomDetailInterface rows (building,
 * type, capacity, coordinates, departments, features, groups), so it backs the Rooms
 * list AND per-room edit load. Same simple-name collision -> must use the FQN.
 */
export const RPC_ROOM_DETAILS_FILTER = 'org.unitime.timetable.gwt.shared.RoomInterface$RoomFilterRpcRequest';
