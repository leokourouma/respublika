<script lang="ts">
	import type { DeputeResume } from '$lib/api';
	import { initials, photoUrl } from '$lib/design';

	let { depute }: { depute: DeputeResume } = $props();

	let imgError = $state(false);
</script>

<a href="/deputes/{depute.id_an}" class="card depute-card">
	<div class="avatar" style="background: {depute.couleur || 'var(--violet)'}">
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
	<div class="info">
		<div class="nom">{depute.nom}</div>
		<div class="groupe-line">
			{#if depute.groupe_libelle}
				{depute.groupe_libelle}
			{:else}
				Non inscrit
			{/if}
		</div>
	</div>
	{#if depute.groupe_abrege}
		<span class="badge badge-groupe">{depute.groupe_abrege}</span>
	{/if}
</a>

<style>
	.depute-card {
		display: flex;
		align-items: center;
		gap: 12px;
		cursor: pointer;
		padding: 14px 16px;
	}

	.avatar {
		width: 44px;
		height: 44px;
		border-radius: 50%;
		display: flex;
		align-items: center;
		justify-content: center;
		font-weight: 700;
		font-size: 0.8rem;
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
		font-size: 0.85rem;
	}

	.info {
		flex: 1;
		min-width: 0;
	}

	.nom {
		font-weight: 600;
		font-size: 0.9rem;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.groupe-line {
		font-size: 0.78rem;
		color: var(--text-secondary);
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
</style>
