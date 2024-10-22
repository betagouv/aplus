# Administration+

- Conditions Générales d’Utilisation : https://docs.aplus.beta.gouv.fr/conditions-generales-dutilisation
- Fiche produit : https://beta.gouv.fr/startups/aplus.html
- Statistiques d’usage : https://infogram.com/stats-dusage-dadministration-1hmr6gm9mk5o6nl?live

## Code

- [Guidelines code](docs/guidelines-code.md)
- [Guidelines design](docs/guidelines-design.md)
- [Wiki](https://github.com/betagouv/aplus/wiki) (Le wiki est visé à être remplacer par la doc dans le repo)

## Commandes

### ⚙️ Pré-requis ⚙️

-   Java
-   Docker
-   SBT
-   L'extension VSCode metals pour scala (non obligatoire mais conseillée !)

### 🗝️ Installer le projet clés en main 🗝️

Cloner le projet :

```shell
git clone https://github.com/betagouv/aplus
cd aplus
```

Pour lancer le projet aller dans le dossier `develop/aplus` puis lancer une base de donnée PostgreSQL avec docker-compose :

```shell
cd develop/aplus
docker-compose up db
```

Se connecter à la base de données avec Docker (pour connaître le nom du container, exécuter la commande `docker ps`):

`docker exec -it <NOM_DU_CONTAINER_DE_LA_BD> psql -U aplus`

Dans la console PSQL lancer la commande `\d` pour vérifier si des relations existent. Si aucune relation n'existe lancer la commande suivante puis quitter la console PSQL:

```shell
CREATE EXTENSION IF NOT EXISTS "unaccent";
\q
```

Ajouter un dump de la base de données à votre projet. Pour cela prendre contact avec l'équipe **Administration+** qui vous enverra le fichier correspondant (*contact@aplus.beta.gouv.fr* ou directement sur Mattermost). Ajouter le fichier à la racine du projet puis lancer la commande suivante (des erreurs apparaîtront, ne les prenez pas en compte !):

`docker exec -i <NOM_DU_CONTAINER_DE_LA_BD> pg_restore -U aplus -d aplus < <NOM_FICHIER_DUMP>`

Copier run.sh.example dans run.sh :

`cp run.sh.example run.sh`

Lancer le projet :

```shell
npm install
chmod +x run.sh
./run.sh
run
```

✨ Enjoy ✨ (si vous tombez sur l'erreur *"database 'default' needs evolution"*, cliquez sur *"apply this script now"*) !

http://localhost:9000

<!-- - Lancer une base de donnée PostgreSQL avec docker-compose :
`docker-compose up db`

- Lancer un serveur Play de dev avec docker-compose :
  `docker-compose up web`

- Pour lancer le serveur sans docker `sbt run` (Vous pouvez regarder les variables d'environnement indispensables dans le `docker-compose.yml` et la liste des variables dans le `application.conf`)

- Les commandes pour le frontend sont dans `package.json` : `npm run watch` (dev), `npm run clean` (supprime ce qui a été installé par `npm install`), `npm run build` (bundle prod) -->

## Attribution

Le projet inclut le fichier `data/french_passwords_top20000.txt` sous licence [Creative Commons Attribution 4.0 International](https://creativecommons.org/licenses/by/4.0/) provenant du dépôt [tarraschk/richelieu](https://github.com/tarraschk/richelieu).
