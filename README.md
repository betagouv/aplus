# Administration+
- Conditions Générales d’Utilisation : https://docs.aplus.beta.gouv.fr/conditions-generales-dutilisation
- Fiche produit : https://beta.gouv.fr/startups/aplus.html
- Statistiques d’usage : https://infogram.com/stats-dusage-dadministration-1hmr6gm9mk5o6nl?live

# Code
- [Guidelines code](docs/guidelines-code.md)
- [Guidelines design](docs/guidelines-design.md)
- [Wiki](https://github.com/betagouv/aplus/wiki) (Le wiki est visé à être remplacer par la doc dans le repo)

# Commande

- Lancer une base de donnée PostgreSQL avec docker-compose :
`docker-compose up db`

- Lancer un serveur Play de dev avec docker-compose :
  `docker-compose up web`

- Pour lancer le serveur sans docker `sbt run` (Vous pouvez regarder les variables d'environnement indispensables dans le `docker-compose.yml` et la liste des variables dans le `application.conf`)

- Les commandes pour le frontend sont dans `typescript/package.json` : `npm run watch` (dev), `npm run clean` (supprime ce qui a été installé par `npm install`), `npm run build` (bundle prod)
