<script lang="ts">
	let { pour = 0, contre = 0, abstentions = 0, nonVotants = 0 }: {
		pour?: number;
		contre?: number;
		abstentions?: number;
		nonVotants?: number;
	} = $props();

	const total = $derived(pour + contre + abstentions + nonVotants);
</script>

{#if total > 0}
<div class="vote-bar">
	{#if pour > 0}
		<div class="segment pour" style="width: {(pour / total) * 100}%" title="Pour: {pour}"></div>
	{/if}
	{#if contre > 0}
		<div class="segment contre" style="width: {(contre / total) * 100}%" title="Contre: {contre}"></div>
	{/if}
	{#if abstentions > 0}
		<div class="segment abstention" style="width: {(abstentions / total) * 100}%" title="Abstention: {abstentions}"></div>
	{/if}
	{#if nonVotants > 0}
		<div class="segment non-votant" style="width: {(nonVotants / total) * 100}%" title="Non votants: {nonVotants}"></div>
	{/if}
</div>
{/if}

<style>
	.vote-bar {
		display: flex;
		height: 8px;
		border-radius: 4px;
		overflow: hidden;
		gap: 2px;
	}

	.segment {
		border-radius: 2px;
		min-width: 3px;
		transition: width 0.3s ease;
	}

	.pour { background: var(--vert); }
	.contre { background: var(--rouge); }
	.abstention { background: var(--ambre); }
	.non-votant { background: var(--gris); }
</style>
