const BASE = '/api';

async function get<T>(path: string): Promise<T> {
	const res = await fetch(`${BASE}${path}`);
	if (!res.ok) throw new Error(`${res.status} ${res.statusText}`);
	return res.json();
}

export interface DeputeResume {
	id_an: string;
	nom: string;
	groupe_uid: string;
	groupe_libelle: string;
	groupe_abrege: string;
	couleur: string;
}

export interface DeputeProfil {
	id_an: string;
	nom: string;
	groupe: { uid: string; libelle: string; abrege: string; couleur: string };
	stats_votes: { total: number; pour: number; contre: number; abstention: number; non_votant: number };
	nb_deports: number;
}

export interface VoteDepute {
	scrutin_uid: string;
	titre: string;
	date_vote: string;
	sort_scrutin: string;
	position: 'pour' | 'contre' | 'abstention' | 'non_votant';
	par_delegation: boolean;
}

export interface Dissidence {
	scrutin_uid: string;
	titre: string;
	date_vote: string;
	sort_scrutin: string;
	position_depute: string;
	position_groupe: string;
}

export interface VoteGroupe {
	groupe_uid: string;
	groupe_libelle: string;
	groupe_abrege: string;
	couleur: string;
	nombre_membres: number;
	position_majoritaire: string;
	pour: number;
	contre: number;
	abstentions: number;
	non_votants: number;
	participation_pct: number;
	synthese: string;
}

export interface ScrutinFull {
	uid: string;
	titre: string;
	date_vote: string;
	sort: string;
	nombre_votants: number;
	suffrages_exprimes: number;
	nbre_suffrages_requis: number;
	groupes: VoteGroupe[];
}

export interface ScrutinResume {
	uid: string;
	titre: string;
	date_vote: string;
	sort: string;
	nombre_votants: number;
}

export interface Deport {
	uid: string;
	depute_id: string;
	depute_nom: string;
	libelle_portee: string;
	explication_html: string;
}

export interface DeputeRandom {
	id_an: string;
	nom: string;
	groupe: { uid: string; libelle: string; abrege: string; couleur: string };
	stats_votes: { total: number; pour: number; contre: number; abstention: number; non_votant: number };
	nb_dissidences: number;
	participation_pct: number;
	loyaute_pct: number;
}

export function fetchDeputeRandom() {
	return get<DeputeRandom>('/deputes/random');
}

export function fetchDeputes(params?: { page?: number; limit?: number; groupe?: string; nom?: string }) {
	const q = new URLSearchParams();
	if (params?.page) q.set('page', String(params.page));
	if (params?.limit) q.set('limit', String(params.limit));
	if (params?.groupe) q.set('groupe', params.groupe);
	if (params?.nom) q.set('nom', params.nom);
	const qs = q.toString();
	return get<{ page: number; limit: number; total: number; total_pages: number; deputes: DeputeResume[] }>(
		`/deputes${qs ? '?' + qs : ''}`
	);
}

export function fetchDepute(id: string) {
	return get<DeputeProfil>(`/deputes/${id}`);
}

export function fetchDeputeVotes(id: string, params?: { page?: number; limit?: number }) {
	const q = new URLSearchParams();
	if (params?.page) q.set('page', String(params.page));
	if (params?.limit) q.set('limit', String(params.limit));
	const qs = q.toString();
	return get<{ page: number; limit: number; total: number; votes: VoteDepute[] }>(
		`/deputes/${id}/votes${qs ? '?' + qs : ''}`
	);
}

export function fetchDissidences(id: string, limit?: number) {
	const qs = limit ? `?limit=${limit}` : '';
	return get<{ depute_id: string; nom: string; groupe_uid: string; nb_dissidences: number; dissidences: Dissidence[] }>(
		`/deputes/${id}/top-dissidences${qs}`
	);
}

export function fetchScrutin(uid: string) {
	return get<ScrutinFull>(`/scrutins/${uid}/full`);
}

export function fetchScrutinsLatest(params?: { page?: number; limit?: number }) {
	const q = new URLSearchParams();
	if (params?.page) q.set('page', String(params.page));
	if (params?.limit) q.set('limit', String(params.limit));
	const qs = q.toString();
	return get<{ page: number; limit: number; total: number; total_pages: number; scrutins: ScrutinResume[] }>(
		`/scrutins/latest${qs ? '?' + qs : ''}`
	);
}

export function fetchRecherche(q: string, params?: { page?: number; limit?: number }) {
	const p = new URLSearchParams({ q });
	if (params?.page) p.set('page', String(params.page));
	if (params?.limit) p.set('limit', String(params.limit));
	return get<{ query: string; page: number; limit: number; total: number; total_pages: number; scrutins: ScrutinResume[] }>(
		`/scrutins/recherche?${p}`
	);
}

export function fetchDeportsLatest(limit?: number) {
	const qs = limit ? `?limit=${limit}` : '';
	return get<{ count: number; deports: Deport[] }>(`/deports/latest${qs}`);
}

export function fetchDeputeDeports(id: string) {
	return get<{ depute_id: string; nom: string; nb_deports: number; badge_ethique_plus: boolean; deports: { uid: string; libelle_portee: string; explication_html: string }[] }>(
		`/deputes/${id}/deports`
	);
}
