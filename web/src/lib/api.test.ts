import { describe, it, expect, vi, beforeEach } from 'vitest';

// Mock fetch globally
const mockFetch = vi.fn();
vi.stubGlobal('fetch', mockFetch);

// Import after mocking
import { register, login, fetchMe } from './api';

beforeEach(() => {
	mockFetch.mockReset();
});

describe('register', () => {
	it('sends POST to /api/auth/register with correct body', async () => {
		mockFetch.mockResolvedValueOnce({
			ok: true,
			json: () => Promise.resolve({ message: 'Inscription réussie !' })
		});

		const result = await register({
			email: 'test@example.com',
			password: 'securepass123',
			nom: 'Test User',
			localite: 'Paris'
		});

		expect(mockFetch).toHaveBeenCalledWith('/api/auth/register', {
			method: 'POST',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify({
				email: 'test@example.com',
				password: 'securepass123',
				nom: 'Test User',
				localite: 'Paris'
			})
		});
		expect(result.message).toContain('Inscription');
	});

	it('throws on error response', async () => {
		mockFetch.mockResolvedValueOnce({
			ok: false,
			json: () => Promise.resolve({ error: 'Email invalide' })
		});

		await expect(
			register({ email: 'bad', password: 'short', nom: '', localite: '' })
		).rejects.toThrow('Email invalide');
	});
});

describe('login', () => {
	it('sends POST to /api/auth/login and returns token + user', async () => {
		const mockResponse = {
			token: 'jwt-token-123',
			user: { id: 1, email: 'test@example.com', nom: 'Test', localite: 'Paris', email_verified: true }
		};
		mockFetch.mockResolvedValueOnce({
			ok: true,
			json: () => Promise.resolve(mockResponse)
		});

		const result = await login('test@example.com', 'securepass123');
		expect(result.token).toBe('jwt-token-123');
		expect(result.user.email).toBe('test@example.com');
	});

	it('throws on wrong credentials', async () => {
		mockFetch.mockResolvedValueOnce({
			ok: false,
			json: () => Promise.resolve({ error: 'Email ou mot de passe incorrect' })
		});

		await expect(login('bad@example.com', 'wrong')).rejects.toThrow('incorrect');
	});
});

describe('fetchMe', () => {
	it('sends GET with Authorization header', async () => {
		const mockUser = { id: 1, email: 'me@example.com', nom: 'Me', localite: 'Lyon', email_verified: true };
		mockFetch.mockResolvedValueOnce({
			ok: true,
			json: () => Promise.resolve(mockUser)
		});

		const result = await fetchMe('my-jwt-token');
		expect(mockFetch).toHaveBeenCalledWith('/api/auth/me', {
			headers: { Authorization: 'Bearer my-jwt-token' }
		});
		expect(result.email).toBe('me@example.com');
	});
});
