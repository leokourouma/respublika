export const colors = {
	violet: '#6C4F82',
	rose: '#C75B7A',
	teal: '#4A9E8E',
	bleu: '#4A7EB5',
	vert: '#5A9E4F',
	rouge: '#C75050',
	ambre: '#C7944A',
	gris: '#8A8A8A',

	bg: '#1A1A1E',
	surface: '#2A2A30',
	surfaceHover: '#333339',
	border: '#3A3A42',
	textPrimary: '#E8E8EC',
	textSecondary: '#9A9AA0',
	textMuted: '#6A6A72',
} as const;

export function positionColor(position: string): string {
	switch (position) {
		case 'pour': return colors.vert;
		case 'contre': return colors.rouge;
		case 'abstention': return colors.ambre;
		case 'non_votant': return colors.gris;
		default: return colors.gris;
	}
}

export function positionLabel(position: string): string {
	switch (position) {
		case 'pour': return 'Pour';
		case 'contre': return 'Contre';
		case 'abstention': return 'Abstention';
		case 'non_votant': return 'Non votant';
		default: return position;
	}
}

export function sortLabel(sort: string): string {
	if (sort.toLowerCase().includes('adopté')) return 'Adopté';
	if (sort.toLowerCase().includes('pas adopté') || sort.toLowerCase().includes("n'a pas")) return 'Rejeté';
	return sort;
}

export function sortColor(sort: string): string {
	const label = sortLabel(sort);
	if (label === 'Adopté') return colors.vert;
	if (label === 'Rejeté') return colors.rouge;
	return colors.gris;
}

export function formatDate(dateStr: string): string {
	const d = new Date(dateStr + 'T00:00:00');
	return d.toLocaleDateString('fr-FR', { day: 'numeric', month: 'short', year: 'numeric' });
}

export function initials(nom: string): string {
	return nom
		.split(/\s+/)
		.filter((w) => w.length > 0 && w[0] === w[0].toUpperCase())
		.map((w) => w[0])
		.slice(0, 2)
		.join('');
}

export function photoUrl(idAn: string): string {
	const num = idAn.replace(/^PA/, '');
	return `https://www2.assemblee-nationale.fr/static/tribun/17/photos/${num}.jpg`;
}
