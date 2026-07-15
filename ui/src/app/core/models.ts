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
 * `RoomFilterRpcRequest` is an AMBIGUOUS simple name — two command beans register
 * it with different classes (EventInterface.RoomFilterRpcRequest -> RoomFilterBackend,
 * and RoomInterface.RoomFilterRpcRequest -> RoomDetailsBackend). The facade drops
 * ambiguous simple names, so callers must use the fully-qualified name. We want the
 * Events one (room enumeration -> FilterRpcResponse). `$` = Java nested-class separator.
 */
export const RPC_ROOM_FILTER = 'org.unitime.timetable.gwt.shared.EventInterface$RoomFilterRpcRequest';
