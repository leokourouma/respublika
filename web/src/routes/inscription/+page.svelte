<script lang="ts">
	import { register } from '$lib/api';
	import { goto } from '$app/navigation';
	import { auth } from '$lib/auth.svelte';

	let nom = $state('');
	let email = $state('');
	let localite = $state('');
	let password = $state('');
	let passwordConfirm = $state('');
	let error = $state('');
	let success = $state('');
	let submitting = $state(false);

	async function handleSubmit(e: Event) {
		e.preventDefault();
		error = '';
		success = '';

		if (!nom.trim()) { error = 'Le nom est requis'; return; }
		if (!email.trim() || !email.includes('@')) { error = 'Email invalide'; return; }
		if (!localite.trim()) { error = 'La localité est requise'; return; }
		if (password.length < 8) { error = 'Le mot de passe doit contenir au moins 8 caractères'; return; }
		if (password !== passwordConfirm) { error = 'Les mots de passe ne correspondent pas'; return; }

		submitting = true;
		try {
			const result = await register({ email, password, nom, localite });
			success = result.message;
		} catch (err: any) {
			error = err.message || 'Erreur lors de l\'inscription';
		} finally {
			submitting = false;
		}
	}
</script>

<svelte:head>
	<title>Inscription — ResPublika</title>
</svelte:head>

<div class="auth-page">
	<div class="auth-card">
		<h1>Inscription</h1>
		<p class="subtitle">Créez votre compte ResPublika</p>

		{#if success}
			<div class="alert alert-success">{success}</div>
			<a href="/connexion" class="btn btn-primary" style="margin-top: 16px; display: block; text-align: center;">
				Se connecter
			</a>
		{:else}
			<form onsubmit={handleSubmit}>
				{#if error}
					<div class="alert alert-error">{error}</div>
				{/if}

				<div class="field">
					<label for="nom">Nom complet</label>
					<input id="nom" type="text" bind:value={nom} placeholder="Jean Dupont" autocomplete="name" />
				</div>

				<div class="field">
					<label for="email">Email</label>
					<input id="email" type="email" bind:value={email} placeholder="jean@exemple.fr" autocomplete="email" />
				</div>

				<div class="field">
					<label for="localite">Localité</label>
					<input id="localite" type="text" bind:value={localite} placeholder="Paris, France" autocomplete="address-level2" />
				</div>

				<div class="field">
					<label for="password">Mot de passe</label>
					<input id="password" type="password" bind:value={password} placeholder="8 caractères minimum" autocomplete="new-password" />
				</div>

				<div class="field">
					<label for="passwordConfirm">Confirmer le mot de passe</label>
					<input id="passwordConfirm" type="password" bind:value={passwordConfirm} placeholder="Confirmez votre mot de passe" autocomplete="new-password" />
				</div>

				<button type="submit" class="btn btn-primary" disabled={submitting}>
					{submitting ? 'Inscription en cours...' : 'S\'inscrire'}
				</button>
			</form>

			<p class="switch-link">
				Déjà un compte ? <a href="/connexion">Se connecter</a>
			</p>
		{/if}
	</div>
</div>

<style>
	.auth-page {
		display: flex;
		justify-content: center;
		padding-top: 40px;
	}

	.auth-card {
		background: var(--surface);
		border: 1px solid var(--border);
		border-radius: var(--radius);
		padding: 32px;
		width: 100%;
		max-width: 420px;
	}

	h1 {
		font-size: 1.5rem;
		font-weight: 700;
		margin-bottom: 4px;
	}

	.subtitle {
		color: var(--text-secondary);
		font-size: 0.875rem;
		margin-bottom: 24px;
	}

	.field {
		margin-bottom: 16px;
	}

	label {
		display: block;
		font-size: 0.8rem;
		font-weight: 600;
		color: var(--text-secondary);
		margin-bottom: 6px;
	}

	input {
		width: 100%;
		padding: 10px 12px;
		background: var(--bg);
		border: 1px solid var(--border);
		border-radius: var(--radius-sm);
		color: var(--text-primary);
		font-size: 0.875rem;
		font-family: inherit;
		outline: none;
		transition: border-color 0.15s;
	}

	input:focus {
		border-color: var(--violet);
	}

	input::placeholder {
		color: var(--text-muted);
	}

	.btn {
		padding: 10px 20px;
		border-radius: var(--radius-sm);
		font-size: 0.875rem;
		font-weight: 600;
		font-family: inherit;
		cursor: pointer;
		border: none;
		transition: opacity 0.15s;
	}

	.btn:disabled {
		opacity: 0.6;
		cursor: not-allowed;
	}

	.btn-primary {
		background: var(--violet);
		color: white;
		width: 100%;
		margin-top: 8px;
	}

	.btn-primary:hover:not(:disabled) {
		opacity: 0.9;
	}

	.alert {
		padding: 10px 14px;
		border-radius: var(--radius-sm);
		font-size: 0.85rem;
		margin-bottom: 16px;
	}

	.alert-error {
		background: rgba(199, 80, 80, 0.15);
		color: #e07070;
		border: 1px solid rgba(199, 80, 80, 0.3);
	}

	.alert-success {
		background: rgba(90, 158, 79, 0.15);
		color: #7ec873;
		border: 1px solid rgba(90, 158, 79, 0.3);
	}

	.switch-link {
		text-align: center;
		font-size: 0.8rem;
		color: var(--text-secondary);
		margin-top: 20px;
	}

	.switch-link a {
		color: var(--violet);
		font-weight: 600;
	}

	.switch-link a:hover {
		text-decoration: underline;
	}
</style>
