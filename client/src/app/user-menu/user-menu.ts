import { Component, inject, signal, HostListener, ElementRef } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { LucideAngularModule, Film, LogOut, Settings, ChevronDown, Sofa } from 'lucide-angular';
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
  private readonly router = inject(Router);
  private readonly elRef = inject(ElementRef);
  readonly version = packageJson.version;

  readonly FilmIcon = Film;
  readonly LogOutIcon = LogOut;
  readonly SettingsIcon = Settings;
  readonly ChevronDownIcon = ChevronDown;
  readonly SofaIcon = Sofa;

  readonly dropdownOpen = signal(false);

  toggleDropdown(): void {
    this.dropdownOpen.update(v => !v);
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.elRef.nativeElement.contains(event.target)) {
      this.dropdownOpen.set(false);
    }
  }

  logout(): void {
    this.dropdownOpen.set(false);
    this.authService.logout();
    this.router.navigate(['/']);
  }
}
