import { create } from 'zustand';
import type { CurrentUser } from '../api/auth';

interface AppSessionState {
  currentUser: CurrentUser | null;
  setCurrentUser: (user: CurrentUser | null) => void;
}

export const useAppSessionStore = create<AppSessionState>((set) => ({
  currentUser: null,
  setCurrentUser: (currentUser) => set({ currentUser })
}));
