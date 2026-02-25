import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../services/auth.service';

@Component({
  selector: 'app-user-menu',
  imports: [RouterLink],
  templateUrl: './user-menu.html',
  styleUrl: './user-menu.scss'
})
export class UserMenuComponent {
  readonly authService = inject(AuthService);

  logout(): void {
    this.authService.logout();
  }
}
