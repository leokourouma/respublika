import { fetchMe, type UserProfile } from './api';

const TOKEN_KEY = 'respublika_token';

class AuthState {
	user = $state<UserProfile | null>(null);
	token = $state<string | null>(null);
	loading = $state(true);

	constructor() {
		if (typeof window !== 'undefined') {
			const saved = localStorage.getItem(TOKEN_KEY);
			if (saved) {
				this.token = saved;
				this.restore();
			} else {
				this.loading = false;
			}
		} else {
			this.loading = false;
		}
	}

	get isLoggedIn() {
		return this.user !== null && this.token !== null;
	}

	async restore() {
		if (!this.token) {
			this.loading = false;
			return;
		}
		try {
			this.user = await fetchMe(this.token);
		} catch {
			this.logout();
		} finally {
			this.loading = false;
		}
	}

	setAuth(token: string, user: UserProfile) {
		this.token = token;
		this.user = user;
		localStorage.setItem(TOKEN_KEY, token);
	}

	logout() {
		this.token = null;
		this.user = null;
		localStorage.removeItem(TOKEN_KEY);
	}
}

export const auth = new AuthState();
