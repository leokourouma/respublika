<script lang="ts">
	import { onMount } from 'svelte';
	import { page } from '$app/stores';
	import { fetchDepute, fetchDeputeVotes, fetchDissidences, type DeputeProfil, type VoteDepute, type Dissidence } from '$lib/api';
	import { initials, photoUrl, positionLabel, formatDate, sortLabel, sortColor } from '$lib/design';
	import VoteBar from '$lib/components/VoteBar.svelte';

	let depute: DeputeProfil | null = $state(null);
	let votes: VoteDepute[] = $state([]);
	let dissidences: Dissidence[] = $state([]);
	let nbDissidences = $state(0);
	let totalVotes = $state(0);
	let loading = $state(true);
	let imgError = $state(false);

	onMount(async () => {
		const id = $page.params.id!;
		try {
			const [d, v, dis] = await Promise.all([
				fetchDepute(id),
				fetchDeputeVotes(id, { limit: 20 }),
				fetchDissidences(id)
			]);
			depute = d;
			votes = v.votes;
			totalVotes = v.total;
			dissidences = dis.dissidences;
			nbDissidences = dis.nb_dissidences;
		} finally {
			loading = false;
		}
	});
</script>

{#if loading}
	<div class="loading"><div class="spinner"></div></div>
{:else if depute}
	<a href="/deputes" class="back-link">Tous les députés</a>

	<div class="profile-header">
		<div class="avatar" style="background: {depute.groupe.couleur || 'var(--violet)'}">
			{#if !imgError}
				<img
					src={photoUrl(depute.id_an)}
					alt={depute.nom}
					onerror={() => imgError = true}
				/>
			{:else}
				<span class="initials">{initials(depute.nom)}</span>
			{/if}
		</div>
		<div class="profile-info">
			<h1>{depute.nom}</h1>
			<div class="profile-meta">
				{#if depute.groupe.abrege}
					<span class="badge badge-groupe">{depute.groupe.abrege}</span>
				{/if}
				<span class="groupe-name">{depute.groupe.libelle || 'Non inscrit'}</span>
			</div>
		</div>
	</div>

	<div class="stats-grid">
		<div class="stat-card">
			<span class="stat-value">{depute.stats_votes.total}</span>
			<span class="stat-label">Votes</span>
		</div>
		<div class="stat-card pour">
			<span class="stat-value">{depute.stats_votes.pour}</span>
			<span class="stat-label">Pour</span>
		</div>
		<div class="stat-card contre">
			<span class="stat-value">{depute.stats_votes.contre}</span>
			<span class="stat-label">Contre</span>
		</div>
		<div class="stat-card abst">
			<span class="stat-value">{depute.stats_votes.abstention}</span>
			<span class="stat-label">Abstention</span>
		</div>
		<div class="stat-card dissidence">
			<span class="stat-value">{nbDissidences}</span>
			<span class="stat-label">Dissidences</span>
		</div>
	</div>

	{#if depute.stats_votes.total > 0}
		<div class="section">
			<VoteBar
				pour={depute.stats_votes.pour}
				contre={depute.stats_votes.contre}
				abstentions={depute.stats_votes.abstention}
				nonVotants={depute.stats_votes.non_votant}
			/>
		</div>
	{/if}

	{#if dissidences.length > 0}
		<div class="section">
			<h2>Dissidences</h2>
			<div class="vote-list">
				{#each dissidences as d}
					<a href="/scrutins/{d.scrutin_uid}" class="card vote-row">
						<div class="vote-row-header">
							<span class="badge badge-dissidence">Dissidence</span>
							<span class="vote-date">{formatDate(d.date_vote)}</span>
						</div>
						<div class="vote-titre">{d.titre}</div>
						<div class="dissidence-detail">
							A voté <strong>{positionLabel(d.position_depute)}</strong>
							(groupe: {positionLabel(d.position_groupe)})
						</div>
					</a>
				{/each}
			</div>
		</div>
	{/if}

	<div class="section">
		<h2>Derniers votes ({totalVotes})</h2>
		<div class="vote-list">
			{#each votes as v}
				{@const sl = sortLabel(v.sort_scrutin)}
				<a href="/scrutins/{v.scrutin_uid}" class="card vote-row">
					<div class="vote-row-header">
						<span class="badge badge-{v.position}">{positionLabel(v.position)}</span>
						<span class="badge" style="background: {sortColor(v.sort_scrutin)}22; color: {sortColor(v.sort_scrutin)}; font-size: 0.7rem">{sl}</span>
						<span class="vote-date">{formatDate(v.date_vote)}</span>
					</div>
					<div class="vote-titre">{v.titre}</div>
					{#if v.par_delegation}
						<div class="delegation">Par délégation</div>
					{/if}
				</a>
			{/each}
		</div>
	</div>
{/if}

<style>
	.back-link {
		display: inline-flex;
		align-items: center;
		gap: 4px;
		font-size: 0.85rem;
		color: var(--text-secondary);
		margin-bottom: 20px;
	}
	.back-link::before { content: '←'; }
	.back-link:hover { color: var(--text-primary); }

	.profile-header {
		display: flex;
		align-items: center;
		gap: 18px;
		margin-bottom: 24px;
	}

	.avatar {
		width: 64px;
		height: 64px;
		border-radius: 50%;
		display: flex;
		align-items: center;
		justify-content: center;
		font-weight: 700;
		font-size: 1.1rem;
		color: white;
		flex-shrink: 0;
		overflow: hidden;
	}

	.avatar img {
		width: 100%;
		height: 100%;
		object-fit: cover;
	}

	.initials {
		font-weight: 700;
		font-size: 1.1rem;
	}

	h1 {
		font-size: 1.5rem;
		font-weight: 700;
		letter-spacing: -0.02em;
	}

	.profile-meta {
		display: flex;
		align-items: center;
		gap: 8px;
		margin-top: 4px;
	}

	.groupe-name {
		font-size: 0.85rem;
		color: var(--text-secondary);
	}

	.stats-grid {
		display: flex;
		gap: 12px;
		flex-wrap: wrap;
		margin-bottom: 24px;
	}

	.stat-card {
		background: var(--surface);
		border: 1px solid var(--border);
		border-radius: var(--radius-sm);
		padding: 14px 18px;
		text-align: center;
		min-width: 80px;
	}

	.stat-value {
		display: block;
		font-size: 1.6rem;
		font-weight: 800;
	}

	.stat-label {
		font-size: 0.72rem;
		color: var(--text-secondary);
		text-transform: uppercase;
		letter-spacing: 0.04em;
	}

	.stat-card.pour .stat-value { color: var(--vert); }
	.stat-card.contre .stat-value { color: var(--rouge); }
	.stat-card.abst .stat-value { color: var(--ambre); }
	.stat-card.dissidence .stat-value { color: var(--rose); }

	.section {
		margin-bottom: 28px;
	}

	h2 {
		font-size: 1.05rem;
		font-weight: 600;
		margin-bottom: 14px;
	}

	.vote-list {
		display: flex;
		flex-direction: column;
		gap: 10px;
	}

	.vote-row {
		display: block;
		padding: 14px 16px;
		cursor: pointer;
	}

	.vote-row-header {
		display: flex;
		align-items: center;
		gap: 8px;
		margin-bottom: 6px;
	}

	.vote-date {
		margin-left: auto;
		font-size: 0.78rem;
		color: var(--text-muted);
	}

	.vote-titre {
		font-size: 0.88rem;
		line-height: 1.4;
		color: var(--text-primary);
		display: -webkit-box;
		-webkit-line-clamp: 2;
		-webkit-box-orient: vertical;
		overflow: hidden;
	}

	.dissidence-detail {
		margin-top: 4px;
		font-size: 0.78rem;
		color: var(--text-secondary);
	}

	.delegation {
		margin-top: 4px;
		font-size: 0.75rem;
		color: var(--text-muted);
		font-style: italic;
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
</style>
