export type ControlMode = 'COLLABORATIVE' | 'HOST_ONLY';

export interface Room {
  id: string;
  code: string;
  name: string;
  controlMode: ControlMode;
  participantCount: number;
  createdAt: string;
}

export interface Participant {
  id: string;
  nickname: string;
  isHost: boolean;
}

export type PlayerAction = 'PLAY' | 'PAUSE' | 'SEEK' | 'CHANGE_VIDEO' | 'SYNC' | 'ENDED';

export interface PlayerState {
  action: PlayerAction;
  videoUrl?: string;
  currentTimeSeconds: number;
  isPlaying: boolean;
}

export interface RoomState {
  roomCode: string;
  currentVideoUrl: string;
  currentTimeSeconds: number;
  isPlaying: boolean;
  participants: Participant[];
}

export interface ChatMessage {
  id: string;
  nickname: string;
  content: string;
  reactions: Record<string, number>;
  sentAt: string;
}

export interface PlaylistItem {
  id: string;
  videoUrl: string;
  title: string | null;
  thumbnailUrl: string | null;
  durationSeconds: number;
  addedBy: string;
  position: number;
  addedAt: string;
}

export type SyncCorrectionType = 'RATE_ADJUST' | 'SEEK' | 'RATE_RESET';

export interface SyncCorrection {
  targetTimeSeconds: number;
  playbackRate: number;
  correctionType: SyncCorrectionType;
}

export interface VideoRecommendation {
  videoId: string;
  videoUrl: string;
  title: string;
  thumbnailUrl: string;
  channelName: string;
}
