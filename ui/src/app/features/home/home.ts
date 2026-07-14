import { Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { AuthService } from '../../core/auth.service';
import { PageService } from '../../core/page.service';

@Component({
  selector: 'app-home',
  imports: [RouterLink, CardModule, ButtonModule],
  templateUrl: './home.html',
})
export class Home implements OnInit {
  private auth = inject(AuthService);
  private page = inject(PageService);

  protected readonly user = this.auth.user;
  protected readonly session = this.auth.session;

  ngOnInit(): void {
    this.page.set('Home');
  }
}
