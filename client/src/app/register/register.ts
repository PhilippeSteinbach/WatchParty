import { Component, signal, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../services/auth.service';

@Component({
  selector: 'app-register',
  imports: [FormsModule, RouterLink],
  templateUrl: './register.html',
  styleUrl: './register.scss'
})
export class RegisterComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly email = signal('');
  readonly displayName = signal('');
  readonly password = signal('');
  readonly loading = signal(false);
  readonly error = signal('');

  register(): void {
    if (!this.email() || !this.displayName() || !this.password()) return;
    this.loading.set(true);
    this.error.set('');
    this.authService.register({
      email: this.email(),
      displayName: this.displayName(),
      password: this.password()
    }).subscribe({
      next: () => this.router.navigate(['/']),
      error: (err) => {
        this.error.set(err.error?.detail ?? 'Registration failed. Please try again.');
        this.loading.set(false);
      }
    });
  }
}
