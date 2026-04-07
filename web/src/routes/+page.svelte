<script lang="ts">
	import { onMount } from 'svelte';
	import { fetchScrutinsLatest, fetchScrutin, fetchDeputeRandom, type ScrutinFull, type ScrutinResume, type DeputeRandom } from '$lib/api';
	import { photoUrl, initials } from '$lib/design';
	import ScrutinCard from '$lib/components/ScrutinCard.svelte';
	import VoteBar from '$lib/components/VoteBar.svelte';

	let spotlight: DeputeRandom | null = $state(null);
	let spotlightImgError = $state(false);
	let scrutins: ScrutinFull[] = $state([]);
	let loading = $state(true);
	let error = $state('');
	let page = $state(1);
	let totalPages = $state(1);
	let total = $state(0);

	async function loadSpotlight() {
		try {
			spotlight = await fetchDeputeRandom();
			spotlightImgError = false;
		} catch {}
	}

	async function loadPage(p: number) {
		loading = true;
		error = '';
		try {
			const res = await fetchScrutinsLatest({ page: p, limit: 12 });
			total = res.total;
			totalPages = res.total_pages;
			page = res.page;

			const details = await Promise.all(
				res.scrutins.map((s: ScrutinResume) => fetchScrutin(s.uid).catch(() => null))
			);
			scrutins = details.filter((s): s is ScrutinFull => s !== null);
		} catch (e: any) {
			error = e.message || 'Erreur de chargement';
		} finally {
			loading = false;
		}
	}

	onMount(() => {
		loadSpotlight();
		loadPage(1);
	});
</script>

<!-- SPOTLIGHT DÉPUTÉ -->
{#if spotlight}
	{@const s = spotlight.stats_votes}
	<section class="spotlight">
		<div class="spotlight-label">Député du jour</div>
		<a href="/deputes/{spotlight.id_an}" class="spotlight-card card">
			<div class="spotlight-left">
				<div class="spotlight-photo" style="background: {spotlight.groupe.couleur || 'var(--violet)'}">
					{#if !spotlightImgError}
						<img
							src={photoUrl(spotlight.id_an)}
							alt={spotlight.nom}
							onerror={() => spotlightImgError = true}
						/>
					{:else}
						<span class="spotlight-initials">{initials(spotlight.nom)}</span>
					{/if}
				</div>
				<div class="spotlight-info">
					<h2>{spotlight.nom}</h2>
					<div class="spotlight-meta">
						{#if spotlight.groupe.abrege}
							<span class="badge badge-groupe">{spotlight.groupe.abrege}</span>
						{/if}
						<span>{spotlight.groupe.libelle || 'Non inscrit'}</span>
					</div>
				</div>
			</div>
			<div class="spotlight-stats">
				<div class="spot-stat">
					<span class="spot-val">{spotlight.participation_pct}%</span>
					<span class="spot-label">Participation</span>
				</div>
				<div class="spot-stat">
					<span class="spot-val">{spotlight.loyaute_pct}%</span>
					<span class="spot-label">Loyauté</span>
				</div>
				<div class="spot-stat dissidence">
					<span class="spot-val">{spotlight.nb_dissidences}</span>
					<span class="spot-label">Dissidences</span>
				</div>
			</div>
			<div class="spotlight-bar">
				<VoteBar pour={s.pour} contre={s.contre} abstentions={s.abstention} nonVotants={s.non_votant} />
				<div class="spotlight-bar-legend">
					<span class="pour">Pour {s.pour}</span>
					<span class="contre">Contre {s.contre}</span>
					<span class="abst">Abst. {s.abstention}</span>
				</div>
			</div>
		</a>
		<button class="shuffle-btn" onclick={loadSpotlight}>
			<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
				<path d="M16 3h5v5"/><path d="M4 20 21 3"/><path d="M21 16v5h-5"/><path d="M15 15l6 6"/><path d="M4 4l5 5"/>
			</svg>
			Autre député
		</button>
	</section>
{/if}

<!-- DERNIERS SCRUTINS -->
<section class="scrutins-section">
	<div class="section-header">
		<h1>Derniers scrutins</h1>
		<p class="subtitle">{total} votes de l'Assemblée nationale</p>
	</div>

	{#if loading}
		<div class="loading">
			<div class="spinner"></div>
			Chargement des scrutins...
		</div>
	{:else if error}
		<div class="error-msg">{error}</div>
	{:else}
		<div class="scrutins-grid">
			{#each scrutins as scrutin (scrutin.uid)}
				<ScrutinCard {scrutin} />
			{/each}
		</div>

		{#if totalPages > 1}
			<div class="pagination">
				<button class="page-btn" disabled={page <= 1} onclick={() => loadPage(page - 1)}>
					Précédent
				</button>
				<span class="page-info">Page {page} / {totalPages}</span>
				<button class="page-btn" disabled={page >= totalPages} onclick={() => loadPage(page + 1)}>
					Suivant
				</button>
			</div>
		{/if}
	{/if}
</section>

<style>
	/* ── Spotlight ── */
	.spotlight {
		margin-bottom: 40px;
		position: relative;
	}

	.spotlight-label {
		font-size: 0.75rem;
		font-weight: 600;
		text-transform: uppercase;
		letter-spacing: 0.06em;
		color: var(--violet);
		margin-bottom: 10px;
	}

	.spotlight-card {
		display: block;
		padding: 24px;
		cursor: pointer;
		border-left: 3px solid var(--violet);
	}

	.spotlight-left {
		display: flex;
		align-items: center;
		gap: 16px;
		margin-bottom: 20px;
	}

	.spotlight-photo {
		width: 72px;
		height: 72px;
		border-radius: 50%;
		overflow: hidden;
		display: flex;
		align-items: center;
		justify-content: center;
		flex-shrink: 0;
		color: white;
	}

	.spotlight-photo img {
		width: 100%;
		height: 100%;
		object-fit: cover;
	}

	.spotlight-initials {
		font-weight: 700;
		font-size: 1.3rem;
	}

	.spotlight-info h2 {
		font-size: 1.3rem;
		font-weight: 700;
		letter-spacing: -0.02em;
	}

	.spotlight-meta {
		display: flex;
		align-items: center;
		gap: 8px;
		margin-top: 4px;
		font-size: 0.85rem;
		color: var(--text-secondary);
	}

	.spotlight-stats {
		display: flex;
		gap: 24px;
		margin-bottom: 16px;
	}

	.spot-stat {
		text-align: center;
	}

	.spot-val {
		display: block;
		font-size: 1.5rem;
		font-weight: 800;
		color: var(--text-primary);
	}

	.spot-stat.dissidence .spot-val {
		color: var(--rose);
	}

	.spot-label {
		font-size: 0.7rem;
		color: var(--text-secondary);
		text-transform: uppercase;
		letter-spacing: 0.04em;
	}

	.spotlight-bar {
		margin-top: 4px;
	}

	.spotlight-bar-legend {
		display: flex;
		gap: 14px;
		margin-top: 6px;
		font-size: 0.78rem;
		font-weight: 500;
	}

	.spotlight-bar-legend .pour { color: var(--vert); }
	.spotlight-bar-legend .contre { color: var(--rouge); }
	.spotlight-bar-legend .abst { color: var(--ambre); }

	.shuffle-btn {
		display: inline-flex;
		align-items: center;
		gap: 6px;
		margin-top: 12px;
		background: none;
		border: 1px solid var(--border);
		border-radius: var(--radius-sm);
		color: var(--text-secondary);
		font-family: inherit;
		font-size: 0.8rem;
		font-weight: 500;
		padding: 6px 14px;
		cursor: pointer;
		transition: all 0.15s;
	}

	.shuffle-btn:hover {
		border-color: var(--violet);
		color: var(--text-primary);
	}

	/* ── Scrutins Section ── */
	.scrutins-section {
		margin-bottom: 32px;
	}

	.section-header {
		margin-bottom: 28px;
	}

	h1 {
		font-size: 1.6rem;
		font-weight: 700;
		letter-spacing: -0.02em;
	}

	.subtitle {
		color: var(--text-secondary);
		font-size: 0.9rem;
		margin-top: 4px;
	}

	.scrutins-grid {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
		gap: 16px;
	}

	.loading {
		display: flex;
		align-items: center;
		gap: 12px;
		justify-content: center;
		padding: 80px 0;
		color: var(--text-secondary);
		font-size: 0.9rem;
	}

	.spinner {
		width: 20px;
		height: 20px;
		border: 2px solid var(--border);
		border-top-color: var(--violet);
		border-radius: 50%;
		animation: spin 0.6s linear infinite;
	}

	@keyframes spin {
		to { transform: rotate(360deg); }
	}

	.error-msg {
		text-align: center;
		padding: 40px;
		color: var(--rouge);
	}

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

	.page-btn:hover:not(:disabled) {
		border-color: var(--violet);
		background: var(--surface-hover);
	}

	.page-btn:disabled {
		opacity: 0.4;
		cursor: not-allowed;
	}

	.page-info {
		font-size: 0.85rem;
		color: var(--text-secondary);
	}
</style>
