<script lang="ts">
	import { onMount } from 'svelte';
	import { fetchDeputes, type DeputeResume } from '$lib/api';
	import DeputeCard from '$lib/components/DeputeCard.svelte';

	let deputes: DeputeResume[] = $state([]);
	let loading = $state(true);
	let total = $state(0);
	let page = $state(1);
	let totalPages = $state(1);
	let searchNom = $state('');
	let searchTimeout: ReturnType<typeof setTimeout>;

	async function load(p: number) {
		loading = true;
		try {
			const res = await fetchDeputes({ page: p, limit: 30, nom: searchNom || undefined });
			deputes = res.deputes;
			total = res.total;
			page = res.page;
			totalPages = res.total_pages;
		} finally {
			loading = false;
		}
	}

	function onSearch() {
		clearTimeout(searchTimeout);
		searchTimeout = setTimeout(() => load(1), 300);
	}

	onMount(() => load(1));
</script>

<div class="page-header">
	<h1>Députés</h1>
	<p class="subtitle">{total} député{total > 1 ? 's' : ''}</p>
</div>

<div class="filter-bar">
	<input
		type="text"
		placeholder="Rechercher par nom..."
		bind:value={searchNom}
		oninput={onSearch}
		class="filter-input"
	/>
</div>

{#if loading}
	<div class="loading">
		<div class="spinner"></div>
	</div>
{:else}
	<div class="deputes-grid">
		{#each deputes as depute (depute.id_an)}
			<DeputeCard {depute} />
		{/each}
	</div>

	{#if totalPages > 1}
		<div class="pagination">
			<button class="page-btn" disabled={page <= 1} onclick={() => load(page - 1)}>Précédent</button>
			<span class="page-info">Page {page} / {totalPages}</span>
			<button class="page-btn" disabled={page >= totalPages} onclick={() => load(page + 1)}>Suivant</button>
		</div>
	{/if}
{/if}

<style>
	.page-header { margin-bottom: 20px; }
	h1 { font-size: 1.6rem; font-weight: 700; letter-spacing: -0.02em; }
	.subtitle { color: var(--text-secondary); font-size: 0.9rem; margin-top: 4px; }

	.filter-bar {
		margin-bottom: 24px;
	}

	.filter-input {
		width: 100%;
		max-width: 360px;
		background: var(--surface);
		border: 1px solid var(--border);
		border-radius: var(--radius-sm);
		padding: 10px 14px;
		color: var(--text-primary);
		font-family: inherit;
		font-size: 0.875rem;
		outline: none;
	}

	.filter-input:focus {
		border-color: var(--violet);
	}

	.filter-input::placeholder {
		color: var(--text-muted);
	}

	.deputes-grid {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
		gap: 10px;
	}

	.loading {
		display: flex;
		justify-content: center;
		padding: 60px 0;
	}

	.spinner {
		width: 20px;
		height: 20px;
		border: 2px solid var(--border);
		border-top-color: var(--violet);
		border-radius: 50%;
		animation: spin 0.6s linear infinite;
	}

	@keyframes spin { to { transform: rotate(360deg); } }

	.pagination {
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 16px;
		margin-top: 32px;
	}

	.page-btn {
		background: var(--surface);
		color: var(--text-primary);
		border: 1px solid var(--border);
		border-radius: var(--radius-sm);
		padding: 8px 18px;
		font-size: 0.85rem;
		font-family: inherit;
		font-weight: 500;
		cursor: pointer;
		transition: all 0.15s;
	}

	.page-btn:hover:not(:disabled) { border-color: var(--violet); background: var(--surface-hover); }
	.page-btn:disabled { opacity: 0.4; cursor: not-allowed; }
	.page-info { font-size: 0.85rem; color: var(--text-secondary); }
</style>
