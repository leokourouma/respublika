<script lang="ts">
	import { onMount } from 'svelte';
	import { fetchAgentRuns, fetchAgentFindings, type AgentRun } from '$lib/api';

	let runs: AgentRun[] = $state([]);
	let loading = $state(true);
	let error = $state('');
	let page = $state(1);
	let totalPages = $state(1);
	let filterAgent = $state('');
	let expandedId: number | null = $state(null);
	let expandedFindings: Record<string, unknown> | null = $state(null);
	let findingsLoading = $state(false);

	// Derived: unique agent names for dropdown
	let agentNames: string[] = $derived(
		[...new Set(runs.map((r) => r.agent_name))]
	);

	// Derived: one status card per unique agent (latest run)
	let agentCards: AgentRun[] = $derived.by(() => {
		const seen = new Map<string, AgentRun>();
		for (const r of runs) {
			if (!seen.has(r.agent_name)) seen.set(r.agent_name, r);
		}
		return [...seen.values()];
	});

	async function load(p: number = 1) {
		loading = true;
		error = '';
		try {
			const params: { page: number; limit: number; agent_name?: string } = { page: p, limit: 20 };
			if (filterAgent) params.agent_name = filterAgent;
			const res = await fetchAgentRuns(params);
			runs = res.runs;
			page = res.page;
			totalPages = res.total_pages;
		} catch (e: any) {
			error = e.message || 'Erreur de chargement';
		} finally {
			loading = false;
		}
	}

	async function toggleFindings(run: AgentRun) {
		if (expandedId === run.id) {
			expandedId = null;
			expandedFindings = null;
			return;
		}
		expandedId = run.id;
		expandedFindings = null;
		findingsLoading = true;
		try {
			expandedFindings = await fetchAgentFindings(run.id);
		} catch {
			// Fallback: parse from the run's findings string
			try {
				expandedFindings = JSON.parse(run.findings);
			} catch {
				expandedFindings = { raw: run.findings };
			}
		} finally {
			findingsLoading = false;
		}
	}

	function relativeTime(iso: string): string {
		const now = Date.now();
		const then = new Date(iso).getTime();
		const diff = Math.max(0, now - then);
		const secs = Math.floor(diff / 1000);
		if (secs < 60) return 'il y a quelques secondes';
		const mins = Math.floor(secs / 60);
		if (mins < 60) return `il y a ${mins} min`;
		const hours = Math.floor(mins / 60);
		if (hours < 24) return `il y a ${hours}h`;
		const days = Math.floor(hours / 24);
		return `il y a ${days}j`;
	}

	function duration(start: string, end: string): string {
		const ms = new Date(end).getTime() - new Date(start).getTime();
		const secs = Math.max(0, Math.round(ms / 1000));
		if (secs < 60) return `${secs}s`;
		return `${Math.floor(secs / 60)}m ${secs % 60}s`;
	}

	function statusClass(s: string): string {
		if (s === 'success') return 'status-success';
		if (s === 'warning') return 'status-warning';
		return 'status-failure';
	}

	function filterByAgent(name: string) {
		filterAgent = name;
		load(1);
	}

	onMount(() => load(1));
</script>

<svelte:head>
	<title>Agents - ResPublika</title>
</svelte:head>

<!-- TOP BAR -->
<div class="top-bar">
	<div class="top-bar-left">
		<h1>Agents</h1>
		<p class="subtitle">Surveillance automatique de la pipeline</p>
	</div>
	<div class="top-bar-right">
		<select class="filter-select" bind:value={filterAgent} onchange={() => load(1)}>
			<option value="">Tous les agents</option>
			{#each agentNames as name}
				<option value={name}>{name}</option>
			{/each}
		</select>
		<button class="refresh-btn" onclick={() => load(page)}>
			<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
				<path d="M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8"/>
				<path d="M21 3v5h-5"/>
			</svg>
			Actualiser
		</button>
	</div>
</div>

{#if loading}
	<div class="loading">
		<div class="spinner"></div>
		Chargement des agents...
	</div>
{:else if error}
	<div class="error-msg">{error}</div>
{:else}
	<div class="agents-layout">
		<!-- LEFT COLUMN: Agent status cards -->
		<aside class="agent-cards">
			<div class="cards-label">Statut des agents</div>
			{#each agentCards as card (card.agent_name)}
				<div class="agent-card card">
					<div class="agent-card-header">
						<code class="agent-name">{card.agent_name}</code>
						<span class="status-dot {statusClass(card.status)}"></span>
					</div>
					<div class="agent-card-meta">
						<span class="badge badge-{card.status === 'success' ? 'adopte' : card.status === 'warning' ? 'abstention' : 'rejete'}">
							{card.status}
						</span>
						<span class="meta-time">{relativeTime(card.started_at)}</span>
					</div>
					<div class="agent-card-stats">
						<div class="mini-stat">
							<span class="mini-val">{card.records_processed}</span>
							<span class="mini-label">traites</span>
						</div>
						<div class="mini-stat">
							<span class="mini-val">{card.records_affected}</span>
							<span class="mini-label">affectes</span>
						</div>
					</div>
					<button class="link-btn" onclick={() => filterByAgent(card.agent_name)}>
						voir l'historique
					</button>
				</div>
			{/each}
			{#if agentCards.length === 0}
				<p class="empty-msg">Aucun agent lance</p>
			{/if}
		</aside>

		<!-- RIGHT COLUMN: Run history table -->
		<section class="run-history">
			<div class="cards-label">Historique des runs</div>
			<div class="history-table">
				<div class="table-header">
					<span class="col-agent">Agent</span>
					<span class="col-date">Date</span>
					<span class="col-status">Statut</span>
					<span class="col-num">Traites</span>
					<span class="col-num">Affectes</span>
					<span class="col-dur">Duree</span>
				</div>
				{#each runs as run (run.id)}
					<button class="table-row" class:expanded={expandedId === run.id} onclick={() => toggleFindings(run)}>
						<span class="col-agent"><code>{run.agent_name}</code></span>
						<span class="col-date">{relativeTime(run.started_at)}</span>
						<span class="col-status">
							<span class="status-dot-inline {statusClass(run.status)}"></span>
							{run.status}
						</span>
						<span class="col-num">{run.records_processed}</span>
						<span class="col-num">{run.records_affected}</span>
						<span class="col-dur">{duration(run.started_at, run.finished_at)}</span>
					</button>

					{#if expandedId === run.id}
						<div class="findings-panel">
							{#if findingsLoading}
								<div class="loading-sm"><div class="spinner"></div></div>
							{:else if expandedFindings}
								<!-- schema-watcher findings (has "folders" key) -->
								{#if 'folders' in expandedFindings && typeof expandedFindings.folders === 'object'}
									{@const folders = expandedFindings.folders as Record<string, {new_fields?: string[], removed_fields?: string[], severity?: string}>}
									{@const folderEntries = Object.entries(folders)}
									{@const allOk = folderEntries.every(([, f]) => (!f.new_fields || f.new_fields.length === 0) && (!f.removed_fields || f.removed_fields.length === 0))}
									{#if allOk}
										<p class="schema-ok">Schema stable</p>
									{:else}
										{#each folderEntries as [folder, diff]}
											{@const newF = diff.new_fields || []}
											{@const removedF = diff.removed_fields || []}
											{#if newF.length > 0 || removedF.length > 0}
												<div class="findings-group schema-folder">
													<div class="findings-key">{folder}
														{#if diff.severity === 'BREAKING'}<span class="breaking-label">BREAKING</span>{/if}
													</div>
													{#if newF.length > 0}
														<div class="schema-section schema-new">
															<div class="schema-section-label">new_fields <span class="findings-count">({newF.length})</span></div>
															<ul class="findings-list">
																{#each newF as field}
																	<li>{field}</li>
																{/each}
															</ul>
														</div>
													{/if}
													{#if removedF.length > 0}
														<div class="schema-section schema-removed">
															<div class="schema-section-label">removed_fields <span class="findings-count">({removedF.length})</span></div>
															<ul class="findings-list">
																{#each removedF as field}
																	<li>{field}</li>
																{/each}
															</ul>
														</div>
													{/if}
												</div>
											{/if}
										{/each}
									{/if}
								{:else}
									<!-- anomaly-detector / generic findings -->
									{#each Object.entries(expandedFindings) as [key, items]}
										{#if key === 'warning_summary' && typeof items === 'object' && items !== null && !Array.isArray(items)}
											{@const entries = Object.entries(items)}
											{#if entries.length > 0}
												<div class="findings-group findings-warn">
													<div class="findings-key">warning_summary <span class="findings-count">({entries.length} types)</span></div>
													<div class="summary-table">
														{#each entries.sort((a, b) => Number(b[1]) - Number(a[1])) as [msg, count]}
															<div class="summary-row">
																<span class="summary-msg">{msg}</span>
																<span class="summary-count">{count}</span>
															</div>
														{/each}
													</div>
												</div>
											{/if}
										{:else}
											{@const arr = Array.isArray(items) ? items : []}
											{#if arr.length > 0}
												<div class="findings-group" class:findings-blocked={key === 'blocked'} class:findings-warn={key === 'gaps'}>
													<div class="findings-key">{key} <span class="findings-count">({arr.length})</span></div>
													<ul class="findings-list">
														{#each arr as item}
															<li>
																{#if typeof item === 'object' && item !== null}
																	{#if 'file' in item}<strong>{item.file}</strong>: {/if}
																	{#if 'message' in item}{item.message}{/if}
																	{#if !('file' in item) && !('message' in item)}{JSON.stringify(item)}{/if}
																{:else}
																	{String(item)}
																{/if}
															</li>
														{/each}
													</ul>
												</div>
											{/if}
										{/if}
									{/each}
									{@const hasContent = Object.entries(expandedFindings).some(([k, v]) =>
										k === 'warning_summary'
											? (typeof v === 'object' && v !== null && Object.keys(v).length > 0)
											: (Array.isArray(v) && v.length > 0)
									)}
									{#if !hasContent}
										<p class="empty-findings">Aucune anomalie detectee</p>
									{/if}
								{/if}
							{/if}
						</div>
					{/if}
				{/each}
				{#if runs.length === 0}
					<p class="empty-msg table-empty">Aucun run enregistre</p>
				{/if}
			</div>

			{#if totalPages > 1}
				<div class="pagination">
					<button class="page-btn" disabled={page <= 1} onclick={() => load(page - 1)}>Precedent</button>
					<span class="page-info">Page {page} / {totalPages}</span>
					<button class="page-btn" disabled={page >= totalPages} onclick={() => load(page + 1)}>Suivant</button>
				</div>
			{/if}
		</section>
	</div>
{/if}

<style>
	/* ── Top Bar ── */
	.top-bar {
		display: flex;
		align-items: flex-start;
		justify-content: space-between;
		margin-bottom: 28px;
		gap: 16px;
		flex-wrap: wrap;
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

	.top-bar-right {
		display: flex;
		gap: 8px;
		align-items: center;
	}

	.filter-select {
		background: var(--surface);
		color: var(--text-primary);
		border: 1px solid var(--border);
		border-radius: var(--radius-sm);
		padding: 7px 12px;
		font-family: inherit;
		font-size: 0.85rem;
		cursor: pointer;
		outline: none;
	}

	.filter-select:focus {
		border-color: var(--violet);
	}

	.refresh-btn {
		display: inline-flex;
		align-items: center;
		gap: 6px;
		background: none;
		border: 1px solid var(--border);
		border-radius: var(--radius-sm);
		color: var(--text-secondary);
		font-family: inherit;
		font-size: 0.8rem;
		font-weight: 500;
		padding: 7px 14px;
		cursor: pointer;
		transition: all 0.15s;
	}

	.refresh-btn:hover {
		border-color: var(--violet);
		color: var(--text-primary);
	}

	/* ── Layout ── */
	.agents-layout {
		display: grid;
		grid-template-columns: 280px 1fr;
		gap: 24px;
		align-items: start;
	}

	.cards-label {
		font-size: 0.75rem;
		font-weight: 600;
		text-transform: uppercase;
		letter-spacing: 0.06em;
		color: var(--violet);
		margin-bottom: 12px;
	}

	/* ── Agent Cards (left) ── */
	.agent-cards {
		display: flex;
		flex-direction: column;
		gap: 12px;
	}

	.agent-card {
		padding: 16px;
	}

	.agent-card-header {
		display: flex;
		align-items: center;
		justify-content: space-between;
		margin-bottom: 8px;
	}

	.agent-name {
		font-size: 0.82rem;
		font-weight: 600;
		background: rgba(108, 79, 130, 0.15);
		padding: 2px 8px;
		border-radius: 4px;
		color: var(--text-primary);
	}

	.status-dot {
		width: 10px;
		height: 10px;
		border-radius: 50%;
		flex-shrink: 0;
	}

	.status-success { background: var(--vert); }
	.status-warning { background: var(--ambre); }
	.status-failure { background: var(--rouge); }

	.agent-card-meta {
		display: flex;
		align-items: center;
		gap: 8px;
		margin-bottom: 12px;
	}

	.meta-time {
		font-size: 0.78rem;
		color: var(--text-muted);
	}

	.agent-card-stats {
		display: flex;
		gap: 20px;
		margin-bottom: 10px;
	}

	.mini-stat {
		text-align: center;
	}

	.mini-val {
		display: block;
		font-size: 1.1rem;
		font-weight: 700;
		color: var(--text-primary);
	}

	.mini-label {
		font-size: 0.68rem;
		color: var(--text-secondary);
		text-transform: uppercase;
		letter-spacing: 0.04em;
	}

	.link-btn {
		background: none;
		border: none;
		color: var(--violet);
		font-family: inherit;
		font-size: 0.78rem;
		font-weight: 500;
		cursor: pointer;
		padding: 0;
		transition: color 0.15s;
	}

	.link-btn:hover {
		color: var(--text-primary);
	}

	/* ── Run History Table (right) ── */
	.history-table {
		border: 1px solid var(--border);
		border-radius: var(--radius);
		overflow: hidden;
	}

	.table-header {
		display: grid;
		grid-template-columns: 1.5fr 1fr 0.8fr 0.6fr 0.6fr 0.6fr;
		gap: 8px;
		padding: 10px 16px;
		background: var(--surface);
		font-size: 0.72rem;
		font-weight: 600;
		text-transform: uppercase;
		letter-spacing: 0.04em;
		color: var(--text-muted);
		border-bottom: 1px solid var(--border);
	}

	.table-row {
		display: grid;
		grid-template-columns: 1.5fr 1fr 0.8fr 0.6fr 0.6fr 0.6fr;
		gap: 8px;
		padding: 12px 16px;
		border: none;
		border-bottom: 1px solid var(--border);
		background: none;
		color: var(--text-primary);
		font-family: inherit;
		font-size: 0.85rem;
		text-align: left;
		cursor: pointer;
		width: 100%;
		transition: background 0.15s;
	}

	.table-row:hover {
		background: var(--surface-hover);
	}

	.table-row.expanded {
		background: var(--surface);
	}

	.table-row:last-of-type {
		border-bottom: none;
	}

	.table-row code {
		font-size: 0.8rem;
		background: rgba(108, 79, 130, 0.1);
		padding: 1px 6px;
		border-radius: 4px;
	}

	.status-dot-inline {
		display: inline-block;
		width: 8px;
		height: 8px;
		border-radius: 50%;
		margin-right: 4px;
		vertical-align: middle;
	}

	.col-num, .col-dur {
		text-align: right;
	}

	.col-date {
		color: var(--text-secondary);
		font-size: 0.8rem;
	}

	/* ── Findings Panel ── */
	.findings-panel {
		padding: 16px 20px;
		border-bottom: 1px solid var(--border);
		background: var(--bg);
	}

	.findings-group {
		margin-bottom: 12px;
	}

	.findings-group:last-child {
		margin-bottom: 0;
	}

	.findings-key {
		font-size: 0.78rem;
		font-weight: 600;
		text-transform: uppercase;
		letter-spacing: 0.04em;
		margin-bottom: 6px;
	}

	.findings-count {
		font-weight: 400;
		color: var(--text-muted);
	}

	.findings-blocked .findings-key {
		color: var(--rouge);
	}

	.findings-warn .findings-key {
		color: var(--ambre);
	}

	.findings-list {
		list-style: none;
		padding: 0;
		display: flex;
		flex-direction: column;
		gap: 4px;
	}

	.findings-list li {
		font-size: 0.82rem;
		color: var(--text-secondary);
		padding: 6px 10px;
		background: var(--surface);
		border-radius: var(--radius-badge);
		line-height: 1.4;
	}

	.findings-blocked .findings-list li {
		border-left: 3px solid var(--rouge);
	}

	.findings-warn .findings-list li {
		border-left: 3px solid var(--ambre);
	}

	.findings-list li strong {
		color: var(--text-primary);
	}

	.summary-table {
		display: flex;
		flex-direction: column;
		gap: 4px;
	}

	.summary-row {
		display: flex;
		justify-content: space-between;
		align-items: center;
		padding: 6px 10px;
		background: var(--surface);
		border-radius: var(--radius-badge);
		border-left: 3px solid var(--ambre);
	}

	.summary-msg {
		font-size: 0.82rem;
		color: var(--text-secondary);
	}

	.summary-count {
		font-size: 0.82rem;
		font-weight: 700;
		color: var(--ambre);
		min-width: 32px;
		text-align: right;
	}

	/* ── Schema Watcher ── */
	.schema-ok {
		font-size: 0.9rem;
		font-weight: 600;
		color: var(--vert);
		padding: 8px 0;
	}

	.schema-folder {
		margin-bottom: 14px;
	}

	.schema-folder .findings-key {
		color: var(--text-primary);
		display: flex;
		align-items: center;
		gap: 8px;
	}

	.breaking-label {
		font-size: 0.68rem;
		font-weight: 700;
		text-transform: uppercase;
		letter-spacing: 0.04em;
		background: rgba(199, 80, 80, 0.2);
		color: #e07070;
		padding: 2px 8px;
		border-radius: 4px;
	}

	.schema-section {
		margin-top: 6px;
		margin-bottom: 8px;
	}

	.schema-section-label {
		font-size: 0.72rem;
		font-weight: 600;
		text-transform: uppercase;
		letter-spacing: 0.04em;
		margin-bottom: 4px;
	}

	.schema-new .schema-section-label {
		color: var(--teal);
	}

	.schema-new .findings-list li {
		border-left: 3px solid var(--teal);
	}

	.schema-removed .schema-section-label {
		color: var(--rouge);
	}

	.schema-removed .findings-list li {
		border-left: 3px solid var(--rouge);
	}

	.empty-findings {
		font-size: 0.85rem;
		color: var(--text-muted);
		padding: 8px 0;
	}

	/* ── Shared ── */
	.loading {
		display: flex;
		align-items: center;
		gap: 12px;
		justify-content: center;
		padding: 80px 0;
		color: var(--text-secondary);
		font-size: 0.9rem;
	}

	.loading-sm {
		display: flex;
		justify-content: center;
		padding: 16px 0;
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

	.empty-msg {
		font-size: 0.85rem;
		color: var(--text-muted);
		padding: 16px 0;
	}

	.table-empty {
		padding: 24px 16px;
	}

	.pagination {
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 16px;
		margin-top: 24px;
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

	/* ── Responsive ── */
	@media (max-width: 768px) {
		.agents-layout {
			grid-template-columns: 1fr;
		}

		.agent-cards {
			flex-direction: row;
			overflow-x: auto;
			gap: 10px;
			padding-bottom: 4px;
		}

		.agent-card {
			min-width: 220px;
			flex-shrink: 0;
		}

		.table-header,
		.table-row {
			grid-template-columns: 1.2fr 0.8fr 0.7fr 0.5fr 0.5fr 0.5fr;
			font-size: 0.78rem;
			padding: 10px 12px;
			gap: 4px;
		}

		.top-bar {
			flex-direction: column;
		}

		.top-bar-right {
			width: 100%;
		}

		.filter-select {
			flex: 1;
		}
	}

	@media (max-width: 520px) {
		.col-dur {
			display: none;
		}

		.table-header,
		.table-row {
			grid-template-columns: 1.2fr 0.8fr 0.7fr 0.5fr 0.5fr;
		}
	}
</style>
