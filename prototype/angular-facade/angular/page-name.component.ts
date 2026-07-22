import { Component, Input, OnInit } from '@angular/core';
import { RpcService } from './rpc.service';
import { PageNameInterface, PageNameRpcRequest } from './page-name.model';

/**
 * Minimal end-to-end usage: renders a localized page title + help link fetched
 * from the real PageNameBackend through the facade. This is the exact call the
 * proof harness verified server-side.
 */
@Component({
  selector: 'ut-page-name',
  standalone: true,
  template: `
    <h1>
      {{ page?.name }}
      <a *ngIf="page?.helpUrl" [href]="page!.helpUrl" target="_blank" rel="noopener">?</a>
    </h1>
  `,
})
export class PageNameComponent implements OnInit {
  @Input() rawName = 'Rooms';
  page?: PageNameInterface;

  constructor(private rpc: RpcService) {}

  ngOnInit(): void {
    const request: PageNameRpcRequest = { name: this.rawName };
    this.rpc
      .execute<PageNameInterface>('PageNameRpcRequest', request)
      .subscribe((page) => (this.page = page));
    // -> POST api/rpc/PageNameRpcRequest {"name":"Rooms"}
    // <- {"helpUrl":"https://help48.unitime.org/rooms","name":"Rooms"}
  }
}