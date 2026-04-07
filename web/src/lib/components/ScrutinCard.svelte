<script lang="ts">
	import type { ScrutinFull } from '$lib/api';
	import { sortLabel, sortColor, formatDate } from '$lib/design';
	import VoteBar from './VoteBar.svelte';

	let { scrutin }: { scrutin: ScrutinFull } = $props();

	const totalPour = $derived(scrutin.groupes.reduce((s, g) => s + g.pour, 0));
	const totalContre = $derived(scrutin.groupes.reduce((s, g) => s + g.contre, 0));
	const totalAbst = $derived(scrutin.groupes.reduce((s, g) => s + g.abstentions, 0));
	const totalNV = $derived(scrutin.groupes.reduce((s, g) => s + g.non_votants, 0));
	const sort = $derived(sortLabel(scrutin.sort));
	const sColor = $derived(sortColor(scrutin.sort));
</script>

<a href="/scrutins/{scrutin.uid}" class="card scrutin-card">
	<div class="card-header">
		<span class="badge" style="background: {sColor}22; color: {sColor}">{sort}</span>
		<span class="date">{formatDate(scrutin.date_vote)}</span>
	</div>

	<h3 class="titre">{scrutin.titre}</h3>

	<VoteBar pour={totalPour} contre={totalContre} abstentions={totalAbst} nonVotants={totalNV} />

	<div class="stats-row">
		<span class="stat pour">Pour {totalPour}</span>
		<span class="stat contre">Contre {totalContre}</span>
		<span class="stat abst">Abst. {totalAbst}</span>
		<span class="stat nv">NV {totalNV}</span>
	</div>

	<div class="groupes-preview">
		{#each scrutin.groupes.slice(0, 5) as g}
			<span class="groupe-chip" style="border-color: {g.couleur || 'var(--border)'}">
				{g.groupe_abrege}
				<small>{g.position_majoritaire === 'pour' ? '+' : g.position_majoritaire === 'contre' ? '-' : '~'}</small>
			</span>
		{/each}
		{#if scrutin.groupes.length > 5}
			<span class="groupe-chip more">+{scrutin.groupes.length - 5}</span>
		{/if}
	</div>
</a>

<style>
	.scrutin-card {
		display: block;
		cursor: pointer;
	}

	.card-header {
		display: flex;
		align-items: center;
		justify-content: space-between;
		margin-bottom: 10px;
	}

	.date {
		font-size: 0.8rem;
		color: var(--text-muted);
	}

	.titre {
		font-size: 0.95rem;
		font-weight: 500;
		line-height: 1.4;
		margin-bottom: 14px;
		color: var(--text-primary);
		display: -webkit-box;
		-webkit-line-clamp: 2;
		-webkit-box-orient: vertical;
		overflow: hidden;
	}

	.stats-row {
		display: flex;
		gap: 12px;
		margin-top: 10px;
		font-size: 0.78rem;
		font-weight: 500;
	}

	.stat.pour { color: var(--vert); }
	.stat.contre { color: var(--rouge); }
	.stat.abst { color: var(--ambre); }
	.stat.nv { color: var(--gris); }

	.groupes-preview {
		display: flex;
		gap: 6px;
		flex-wrap: wrap;
		margin-top: 12px;
	}

	.groupe-chip {
		font-size: 0.72rem;
		font-weight: 600;
		padding: 2px 8px;
		border-radius: 4px;
		border: 1px solid;
		color: var(--text-secondary);
		background: transparent;
	}

	.groupe-chip small {
		margin-left: 2px;
		opacity: 0.7;
	}

	.groupe-chip.more {
		border-color: var(--border);
		color: var(--text-muted);
	}
</style>
