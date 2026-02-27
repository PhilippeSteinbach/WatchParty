import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideAngularModule, Film, LogOut, UserRound, LayoutDashboard } from 'lucide-angular';
import { AuthService } from '../services/auth.service';
import packageJson from '../../../package.json';

@Component({
  selector: 'app-user-menu',
  imports: [RouterLink, LucideAngularModule],
  templateUrl: './user-menu.html',
  styleUrl: './user-menu.scss'
})
export class UserMenuComponent {
  readonly authService = inject(AuthService);
  readonly version = packageJson.version;

  readonly FilmIcon = Film;
  readonly LogOutIcon = LogOut;
  readonly UserIcon = UserRound;
  readonly DashboardIcon = LayoutDashboard;

  logout(): void {
    this.authService.logout();
  }
}
