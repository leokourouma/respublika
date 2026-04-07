<script lang="ts">
	import '../app.css';
	import type { Snippet } from 'svelte';

	let { children }: { children: Snippet } = $props();

	let searchQuery = $state('');

	function handleSearch(e: Event) {
		e.preventDefault();
		if (searchQuery.trim()) {
			window.location.href = `/scrutins?q=${encodeURIComponent(searchQuery.trim())}`;
		}
	}
</script>

<svelte:head>
	<title>ResPublika</title>
	<meta name="description" content="Transparence parlementaire - Assemblée nationale française" />
</svelte:head>

<header>
	<div class="container header-inner">
		<a href="/" class="logo">
			<span class="logo-icon">RP</span>
			<span class="logo-text">ResPublika</span>
		</a>
		<nav>
			<a href="/" class="nav-link active">Scrutins</a>
			<a href="/deputes" class="nav-link">Députés</a>
		</nav>
		<form class="search-bar" onsubmit={handleSearch}>
			<svg class="search-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
				<circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/>
			</svg>
			<input
				type="text"
				placeholder="Chercher un député, un scrutin..."
				bind:value={searchQuery}
			/>
		</form>
	</div>
</header>

<main class="container">
	{@render children()}
</main>

<style>
	header {
		background: var(--surface);
		border-bottom: 1px solid var(--border);
		padding: 14px 0;
		position: sticky;
		top: 0;
		z-index: 100;
		backdrop-filter: blur(12px);
	}

	.header-inner {
		display: flex;
		align-items: center;
		gap: 24px;
	}

	.logo {
		display: flex;
		align-items: center;
		gap: 10px;
		flex-shrink: 0;
	}

	.logo-icon {
		background: var(--violet);
		color: white;
		font-weight: 800;
		font-size: 0.85rem;
		padding: 5px 8px;
		border-radius: 6px;
		letter-spacing: -0.02em;
	}

	.logo-text {
		font-weight: 700;
		font-size: 1.1rem;
		letter-spacing: -0.02em;
	}

	nav {
		display: flex;
		gap: 4px;
	}

	.nav-link {
		padding: 6px 14px;
		border-radius: var(--radius-sm);
		font-size: 0.875rem;
		font-weight: 500;
		color: var(--text-secondary);
		transition: all 0.15s;
	}

	.nav-link:hover, .nav-link.active {
		background: rgba(108, 79, 130, 0.15);
		color: var(--text-primary);
	}

	.search-bar {
		margin-left: auto;
		display: flex;
		align-items: center;
		gap: 8px;
		background: var(--bg);
		border: 1px solid var(--border);
		border-radius: var(--radius-sm);
		padding: 7px 12px;
		min-width: 260px;
	}

	.search-bar:focus-within {
		border-color: var(--violet);
	}

	.search-icon {
		color: var(--text-muted);
		flex-shrink: 0;
	}

	.search-bar input {
		background: none;
		border: none;
		color: var(--text-primary);
		font-size: 0.85rem;
		outline: none;
		width: 100%;
		font-family: inherit;
	}

	.search-bar input::placeholder {
		color: var(--text-muted);
	}

	main {
		padding-top: 32px;
		padding-bottom: 64px;
		min-height: calc(100vh - 60px);
	}
</style>
