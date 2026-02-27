import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule, AlertTriangle } from 'lucide-angular';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-danger-zone',
  standalone: true,
  imports: [FormsModule, LucideAngularModule],
  templateUrl: './danger-zone.html',
})
export class DangerZoneComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly AlertIcon = AlertTriangle;

  readonly showConfirm = signal(false);
  readonly password = signal('');
  readonly deleting = signal(false);
  readonly error = signal('');

  confirmDelete(): void {
    this.showConfirm.set(true);
    this.error.set('');
  }

  cancel(): void {
    this.showConfirm.set(false);
    this.password.set('');
    this.error.set('');
  }

  deleteAccount(): void {
    this.deleting.set(true);
    this.error.set('');
    this.authService.deleteAccount({ password: this.password() }).subscribe({
      next: () => {
        this.authService.logout();
        this.router.navigate(['/']);
      },
      error: (err) => {
        this.deleting.set(false);
        this.error.set(err.error?.message || err.statusText || 'Failed to delete account.');
      },
    });
  }
}
