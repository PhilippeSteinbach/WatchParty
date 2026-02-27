import { Routes } from '@angular/router';
import { HomeComponent } from './home/home';
import { JoinRoomComponent } from './join-room/join-room';
import { LoginComponent } from './login/login';
import { RegisterComponent } from './register/register';
import { SettingsComponent } from './settings/settings';
import { AccountSettingsComponent } from './settings/account-settings/account-settings';
import { SecuritySettingsComponent } from './settings/security-settings/security-settings';
import { RoomSettingsComponent } from './settings/room-settings/room-settings';
import { DangerZoneComponent } from './settings/danger-zone/danger-zone';
import { authGuard } from './guards/auth.guard';

export const routes: Routes = [
  { path: '', component: HomeComponent },
  { path: 'room/:code', component: JoinRoomComponent },
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },
  {
    path: 'settings',
    component: SettingsComponent,
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'account', pathMatch: 'full' },
      { path: 'account', component: AccountSettingsComponent },
      { path: 'security', component: SecuritySettingsComponent },
      { path: 'rooms', component: RoomSettingsComponent },
      { path: 'danger-zone', component: DangerZoneComponent },
    ],
  },
];
