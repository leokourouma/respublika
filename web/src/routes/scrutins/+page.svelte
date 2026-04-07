<script lang="ts">
	import { onMount } from 'svelte';
	import { page } from '$app/stores';
	import { fetchRecherche, fetchScrutin, type ScrutinFull, type ScrutinResume } from '$lib/api';
	import ScrutinCard from '$lib/components/ScrutinCard.svelte';

	let query = $derived($page.url.searchParams.get('q') || '');
	let scrutins: ScrutinFull[] = $state([]);
	let loading = $state(true);
	let total = $state(0);
	let currentPage = $state(1);
	let totalPages = $state(1);

	async function load(p: number) {
		loading = true;
		try {
			const res = await fetchRecherche(query, { page: p, limit: 12 });
			total = res.total;
			totalPages = res.total_pages;
			currentPage = res.page;
			const details = await Promise.all(
				res.scrutins.map((s: ScrutinResume) => fetchScrutin(s.uid).catch(() => null))
			);
			scrutins = details.filter((s): s is ScrutinFull => s !== null);
		} finally {
			loading = false;
		}
	}

	onMount(() => load(1));
</script>

<a href="/" class="back-link">Accueil</a>

<div class="page-header">
	<h1>Recherche : "{query}"</h1>
	<p class="subtitle">{total} résultat{total > 1 ? 's' : ''}</p>
</div>

{#if loading}
	<div class="loading"><div class="spinner"></div></div>
{:else}
	<div class="scrutins-grid">
		{#each scrutins as scrutin (scrutin.uid)}
			<ScrutinCard {scrutin} />
		{/each}
	</div>

	{#if totalPages > 1}
		<div class="pagination">
			<button class="page-btn" disabled={currentPage <= 1} onclick={() => load(currentPage - 1)}>Précédent</button>
			<span class="page-info">Page {currentPage} / {totalPages}</span>
			<button class="page-btn" disabled={currentPage >= totalPages} onclick={() => load(currentPage + 1)}>Suivant</button>
		</div>
	{/if}
{/if}

<style>
	.back-link {
		display: inline-flex;
		align-items: center;
		gap: 4px;
		font-size: 0.85rem;
		color: var(--text-secondary);
		margin-bottom: 16px;
	}
	.back-link::before { content: '←'; }
	.back-link:hover { color: var(--text-primary); }

	.page-header { margin-bottom: 28px; }
	h1 { font-size: 1.4rem; font-weight: 700; }
	.subtitle { color: var(--text-secondary); font-size: 0.9rem; margin-top: 4px; }

	.scrutins-grid {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
		gap: 16px;
	}

	.loading {
		display: flex;
		justify-content: center;
		padding: 80px 0;
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
	}
	.page-btn:hover:not(:disabled) { border-color: var(--violet); }
	.page-btn:disabled { opacity: 0.4; cursor: not-allowed; }
	.page-info { font-size: 0.85rem; color: var(--text-secondary); }
</style>
