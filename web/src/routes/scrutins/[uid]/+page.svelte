<script lang="ts">
	import { onMount } from 'svelte';
	import { page } from '$app/stores';
	import { fetchScrutin, type ScrutinFull } from '$lib/api';
	import { sortLabel, sortColor, formatDate, positionLabel } from '$lib/design';
	import VoteBar from '$lib/components/VoteBar.svelte';

	let scrutin: ScrutinFull | null = $state(null);
	let loading = $state(true);
	let error = $state('');

	onMount(async () => {
		try {
			const uid = $page.params.uid!;
			scrutin = await fetchScrutin(uid);
		} catch (e: any) {
			error = e.message;
		} finally {
			loading = false;
		}
	});
</script>

{#if loading}
	<div class="loading">
		<div class="spinner"></div>
		Chargement...
	</div>
{:else if error}
	<div class="error-msg">{error}</div>
{:else if scrutin}
	{@const sort = sortLabel(scrutin.sort)}
	{@const sColor = sortColor(scrutin.sort)}
	{@const totalPour = scrutin.groupes.reduce((s, g) => s + g.pour, 0)}
	{@const totalContre = scrutin.groupes.reduce((s, g) => s + g.contre, 0)}
	{@const totalAbst = scrutin.groupes.reduce((s, g) => s + g.abstentions, 0)}
	{@const totalNV = scrutin.groupes.reduce((s, g) => s + g.non_votants, 0)}

	<a href="/" class="back-link">Tous les scrutins</a>

	<div class="scrutin-header">
		<span class="badge" style="background: {sColor}22; color: {sColor}">{sort}</span>
		<span class="date">{formatDate(scrutin.date_vote)}</span>
	</div>

	<h1>{scrutin.titre}</h1>

	<div class="global-stats">
		<div class="stat-block">
			<span class="stat-value">{scrutin.nombre_votants}</span>
			<span class="stat-label">Votants</span>
		</div>
		<div class="stat-block">
			<span class="stat-value">{scrutin.suffrages_exprimes}</span>
			<span class="stat-label">Suffrages exprimés</span>
		</div>
		<div class="stat-block">
			<span class="stat-value">{scrutin.nbre_suffrages_requis}</span>
			<span class="stat-label">Majorité requise</span>
		</div>
	</div>

	<div class="bar-section">
		<VoteBar pour={totalPour} contre={totalContre} abstentions={totalAbst} nonVotants={totalNV} />
		<div class="bar-legend">
			<span class="leg pour">Pour {totalPour}</span>
			<span class="leg contre">Contre {totalContre}</span>
			<span class="leg abst">Abst. {totalAbst}</span>
			<span class="leg nv">NV {totalNV}</span>
		</div>
	</div>

	<h2>Détail par groupe</h2>
	<div class="groupes-list">
		{#each scrutin.groupes as g}
			<div class="card groupe-row">
				<div class="groupe-header">
					<div class="groupe-identity">
						<span class="groupe-dot" style="background: {g.couleur || 'var(--gris)'}"></span>
						<div>
							<div class="groupe-name">{g.groupe_abrege}</div>
							<div class="groupe-full">{g.groupe_libelle}</div>
						</div>
					</div>
					<div class="groupe-position">
						<span class="badge badge-{g.position_majoritaire}">{positionLabel(g.position_majoritaire)}</span>
					</div>
				</div>

				<VoteBar pour={g.pour} contre={g.contre} abstentions={g.abstentions} nonVotants={g.non_votants} />

				<div class="groupe-stats">
					<span class="gs pour">Pour {g.pour}</span>
					<span class="gs contre">Contre {g.contre}</span>
					<span class="gs abst">Abst. {g.abstentions}</span>
					<span class="gs nv">NV {g.non_votants}</span>
					<span class="gs-sep"></span>
					<span class="gs members">{g.nombre_membres} membres</span>
					<span class="gs participation">{g.participation_pct}% participation</span>
				</div>

				<div class="synthese">{g.synthese}</div>
			</div>
		{/each}
	</div>
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

	.back-link::before {
		content: '←';
	}

	.back-link:hover {
		color: var(--text-primary);
	}

	.scrutin-header {
		display: flex;
		align-items: center;
		gap: 12px;
		margin-bottom: 12px;
	}

	.date {
		font-size: 0.85rem;
		color: var(--text-muted);
	}

	h1 {
		font-size: 1.4rem;
		font-weight: 600;
		line-height: 1.4;
		margin-bottom: 24px;
	}

	.global-stats {
		display: flex;
		gap: 24px;
		margin-bottom: 24px;
	}

	.stat-block {
		background: var(--surface);
		border: 1px solid var(--border);
		border-radius: var(--radius-sm);
		padding: 14px 20px;
		text-align: center;
	}

	.stat-value {
		display: block;
		font-size: 1.5rem;
		font-weight: 700;
		color: var(--text-primary);
	}

	.stat-label {
		font-size: 0.75rem;
		color: var(--text-secondary);
		text-transform: uppercase;
		letter-spacing: 0.04em;
	}

	.bar-section {
		margin-bottom: 32px;
	}

	.bar-legend {
		display: flex;
		gap: 14px;
		margin-top: 8px;
		font-size: 0.8rem;
		font-weight: 500;
	}

	.leg.pour { color: var(--vert); }
	.leg.contre { color: var(--rouge); }
	.leg.abst { color: var(--ambre); }
	.leg.nv { color: var(--gris); }

	h2 {
		font-size: 1.1rem;
		font-weight: 600;
		margin-bottom: 16px;
	}

	.groupes-list {
		display: flex;
		flex-direction: column;
		gap: 12px;
	}

	.groupe-row {
		padding: 16px;
	}

	.groupe-header {
		display: flex;
		align-items: center;
		justify-content: space-between;
		margin-bottom: 10px;
	}

	.groupe-identity {
		display: flex;
		align-items: center;
		gap: 10px;
	}

	.groupe-dot {
		width: 10px;
		height: 10px;
		border-radius: 50%;
		flex-shrink: 0;
	}

	.groupe-name {
		font-weight: 600;
		font-size: 0.9rem;
	}

	.groupe-full {
		font-size: 0.78rem;
		color: var(--text-secondary);
	}

	.groupe-stats {
		display: flex;
		flex-wrap: wrap;
		gap: 10px;
		margin-top: 8px;
		font-size: 0.75rem;
		font-weight: 500;
	}

	.gs.pour { color: var(--vert); }
	.gs.contre { color: var(--rouge); }
	.gs.abst { color: var(--ambre); }
	.gs.nv { color: var(--gris); }
	.gs-sep { border-left: 1px solid var(--border); }
	.gs.members, .gs.participation { color: var(--text-muted); }

	.synthese {
		margin-top: 8px;
		font-size: 0.82rem;
		font-style: italic;
		color: var(--text-secondary);
	}

	.loading {
		display: flex;
		align-items: center;
		gap: 12px;
		justify-content: center;
		padding: 80px 0;
		color: var(--text-secondary);
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
</style>
