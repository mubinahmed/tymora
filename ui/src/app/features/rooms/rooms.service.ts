import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, map, tap } from 'rxjs';
import { RpcService } from '../../core/rpc.service';
import {
  FilterRpcResponse,
  RoomDetailInterface,
  RoomPictureResponse,
  RoomUpdateRpcRequest,
  RPC_ROOM_DETAILS_FILTER,
} from '../../core/models';

/**
 * Rooms data access. The list + full per-room detail both come from
 * RoomInterface.RoomFilterRpcRequest (RoomDetailsBackend) ENUMERATE, whose result
 * rows ARE full RoomDetailInterface objects. Saves/deletes go through
 * RoomUpdateRpcRequest. The last ENUMERATE is cached so the edit screen can pick
 * a room by id without another round-trip (with a reload fallback for deep links).
 */
@Injectable({ providedIn: 'root' })
export class RoomsService {
  private rpc = inject(RpcService);
  private http = inject(HttpClient);

  /** FutureOperation.flag() bits — the field groups the backend may update. */
  static readonly FLAG = {
    ROOM_PROPERTIES: 1,
    EXAM_PROPERTIES: 2,
    EVENT_PROPERTIES: 4,
    GROUPS: 8,
    FEATURES: 16,
    ROOM_SHARING: 32,
    EXAM_PREFS: 64,
    EVENT_AVAILABILITY: 128,
    PICTURES: 256,
  } as const;

  readonly rooms = signal<RoomDetailInterface[]>([]);

  list(sessionId: number | null): Observable<RoomDetailInterface[]> {
    const request = { command: 'ENUMERATE', options: {}, sessionId: sessionId ?? undefined };
    return this.rpc.execute<FilterRpcResponse>(RPC_ROOM_DETAILS_FILTER, request).pipe(
      map((res) => (res.entities?.['results'] ?? []) as RoomDetailInterface[]),
      tap((rooms) => this.rooms.set(rooms)),
    );
  }

  /** From the cached list, or reload then find (supports edit deep links / refresh). */
  get(id: number, sessionId: number | null): Observable<RoomDetailInterface | undefined> {
    const cached = this.rooms().find((r) => r.uniqueId === id);
    if (cached) return new Observable((s) => (s.next(cached), s.complete()));
    return this.list(sessionId).pipe(map((rooms) => rooms.find((r) => r.uniqueId === id)));
  }

  save(
    room: RoomDetailInterface,
    sessionId: number | null,
    futureFlags: { [key: string]: number },
  ): Observable<RoomDetailInterface> {
    const request: RoomUpdateRpcRequest = {
      operation: room.uniqueId == null ? 'CREATE' : 'UPDATE',
      sessionId: sessionId ?? undefined,
      room,
      // Key "0" = primary session (the backend defaults an absent primary-session
      // flag to "all groups", so an explicit mask restricts the update to the
      // field groups this form populated). Other keys apply to future
      // sessions/rooms: -sessionId for a new room, the future-room id on edit.
      futureFlags,
    };
    return this.rpc.execute<RoomDetailInterface>('RoomUpdateRpcRequest', request);
  }

  remove(id: number, sessionId: number | null): Observable<RoomDetailInterface> {
    const request: RoomUpdateRpcRequest = {
      operation: 'DELETE',
      sessionId: sessionId ?? undefined,
      locationId: id,
    };
    return this.rpc.execute<RoomDetailInterface>('RoomUpdateRpcRequest', request);
  }

  /**
   * Two-step room picture upload, mirroring the legacy GWT flow:
   *   1) POST the file (multipart) to the UploadServlet, which stashes it on the
   *      HTTP session (same session the facade uses — cookies are same-origin).
   *   2) RoomPictureRequest[UPLOAD] turns that stashed file into a (temporary)
   *      RoomPictureInterface; for a not-yet-saved room (locationId null) it is
   *      held in the session and persisted when RoomUpdateRpcRequest is sent with
   *      the PICTURES flag and the picture in room.pictures.
   * The servlet responds with a plain-text status; an "ERROR"/"No file" prefix
   * signals failure.
   */
  uploadPicture(file: File): Observable<string> {
    const form = new FormData();
    form.append('file', file, file.name);
    return this.http.post('/upload', form, { responseType: 'text' });
  }

  registerUploadedPicture(sessionId: number | null, locationId: number | null): Observable<RoomPictureResponse> {
    return this.rpc.execute<RoomPictureResponse>('RoomPictureRequest', {
      operation: 'UPLOAD',
      sessionId: sessionId ?? undefined,
      locationId: locationId ?? undefined,
    });
  }
}
