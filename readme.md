# Respublika 🏛️

Projet Kotlin dédié à l'analyse et au traitement des données ouvertes de l'Assemblée Nationale française.

## 📊 Gestion des données (Open Data)

Le projet traite les données issues de [data.assemblee-nationale.fr](https://data.assemblee-nationale.fr).
Le contenu du dossier `data/` est ignoré par Git (via `.gitignore`), à l'exception de la structure des dossiers.

### 1. Récupération des fichiers

Rends-toi sur le portail Open Data de l'Assemblée et télécharge les archives (format JSON) pour les catégories suivantes :

- **Acteurs** : Liste des députés et sénateurs.
- **Amendements** : Dossiers législatifs et amendements déposés.
- **Comptes Rendus** : Débats en séance publique.
- **Déports** : Déclarations d'intérêts et déports.
- **Organes** : Groupes politiques, commissions et instances.
- **Scrutins** : Détails des votes publics.

### 2. Organisation du dossier `data/`

Décompresse les fichiers dans les sous-dossiers respectifs à la racine du projet :

```
respublika/
└── data/
    ├── acteurs/          # Placer les JSON ici
    ├── amendements/      # Placer les JSON ici
    ├── comptes_rendus/   # Placer les JSON ici
    ├── deports/          # Placer les JSON ici
    ├── organes/          # Placer les JSON ici
    └── scrutins/         # Placer les JSON ici
```

> **Note :** Chaque dossier contient un fichier `.gitkeep` pour maintenir l'arborescence sur le dépôt distant.

---

## 📝 Licence

Ce projet est sous licence [MIT](LICENSE).
