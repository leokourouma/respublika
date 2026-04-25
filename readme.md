# Respublika 🏛️

Projet Kotlin dédié à l'analyse et au traitement des données ouvertes de l'Assemblée Nationale française.

---

## Prerequis

- Docker & Docker Compose
- Node.js (>= 18) + npm

---

## Lancement

### 1. Cloner le projet

```bash
git clone git@github.com:leokourouma/respublika.git
cd respublika
```

### 2. Configurer les variables d'environnement

Copier le modele et renseigner les valeurs :

```bash
cp .env.example .env
# editer .env (DB_PASSWORD, JWT_SECRET, etc.)
```

Le fichier `.env` est ignore par Git.

### 3. Demarrer la base de donnees et Redis

```bash
docker compose up -d
```

Cela lance PostgreSQL (port 5432) et Redis (port 6379). Docker Compose lit automatiquement `.env`.

### 4. Demarrer le serveur Ktor

Dans un terminal, depuis la racine du projet :

```bash
docker run --rm -it \
  --env-file .env \
  -v "$PWD":/home/gradle/project \
  -w /home/gradle/project \
  -p 8081:8081 \
  -e DATA_PATH=/home/gradle/project/data \
  --network="respublika_default" \
  gradle:8.10-jdk21 ./gradlew :server:run --no-daemon
```

Le serveur API ecoute sur `http://localhost:8081`.

### 5. Demarrer le frontend SvelteKit

Dans un second terminal :

```bash
cd web
npm install
npm run dev
```

Le site est accessible sur `http://localhost:5173`.

### 6. Lancer l'ingestion des donnees

Une fois le serveur et la base demarres, declencher l'ingestion via :

```
http://localhost:8081/admin/ingest
```

L'ingestion tourne en tache de fond. Suivre la progression dans les logs du conteneur serveur.

---

## Pages principales

| URL | Description |
|-----|-------------|
| `http://localhost:5173` | Accueil — scrutins et depute du jour |
| `http://localhost:5173/deputes` | Liste des deputes |
| `http://localhost:5173/admin/agents` | Dashboard des agents (AnomalyDetector, SchemaWatcher) |

---

## Donnees (Open Data)

Le projet traite les donnees issues de [data.assemblee-nationale.fr](https://data.assemblee-nationale.fr).
Le contenu du dossier `data/` est ignore par Git (via `.gitignore`).

Telecharger les archives JSON depuis le portail et les decompresser dans :

```
respublika/
└── data/
    ├── acteurs/
    ├── amendements/
    ├── comptes_rendus/
    ├── deports/
    ├── organes/
    └── scrutins/
```

---

## Arret

```bash
# Arreter le serveur Ktor : Ctrl+C dans le terminal
# Arreter la base et Redis :
docker compose down
```

---

## 📝 Licence

Ce projet est sous licence [MIT](LICENSE).
