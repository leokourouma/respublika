# Respublika 🏛️

Projet Kotlin dédié à l'analyse et au traitement des données ouvertes de l'Assemblée Nationale française.

---

## 🚀 Installation sur macOS

Suis ces étapes pour configurer et lancer le projet sur ton Mac :

### 1. Prérequis

Il est recommandé d'utiliser **Homebrew** pour installer le JDK et Git :
```bash
# Installer le JDK 17
brew install openjdk@17

# Installer Git
brew install git
` ` `

### 2. Cloner le projet

Récupère le code via SSH (assure-toi que ta clé id_ed25519 est configurée sur GitHub) :

` ` `bash
git clone git@github.com:leokourouma/respublika.git
cd respublika
` ` `

### 3. Compilation et lancement

Utilise le wrapper Gradle inclus pour compiler et exécuter :

` ` `bash
# Donner les droits d'exécution au script
chmod +x gradlew

# Compiler et lancer l'application
./gradlew build
./gradlew run
` ` `

---

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

` ` `
respublika/
└── data/
    ├── acteurs/          # Placer les JSON ici
    ├── amendements/      # Placer les JSON ici
    ├── comptes_rendus/   # Placer les JSON ici
    ├── deports/          # Placer les JSON ici
    ├── organes/          # Placer les JSON ici
    └── scrutins/         # Placer les JSON ici
` ` `

> **Note :** Chaque dossier contient un fichier `.gitkeep` pour maintenir l'arborescence sur le dépôt distant.

---

## 🛠️ Développement

Si tu utilises **IntelliJ IDEA** :

1. Ouvre le projet en sélectionnant le fichier `build.gradle.kts`.
2. Laisse IntelliJ importer les dépendances Gradle automatiquement.

---

## 📝 Licence

Ce projet est sous licence [MIT](LICENSE).
```

Les ` ` ` sont évidemment des ` ` ` sans espaces — je les ai espacés ici pour éviter que le rendu Markdown ne les interprète dans ma réponse.