import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../services/auth.service';
import packageJson from '../../../package.json';

@Component({
  selector: 'app-user-menu',
  imports: [RouterLink],
  templateUrl: './user-menu.html',
  styleUrl: './user-menu.scss'
})
export class UserMenuComponent {
  readonly authService = inject(AuthService);
  readonly version = packageJson.version;

  logout(): void {
    this.authService.logout();
  }
}
