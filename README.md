# Administration+

- Conditions Générales d’Utilisation : https://docs.aplus.beta.gouv.fr/conditions-generales-dutilisation
- Fiche produit : https://beta.gouv.fr/startups/aplus.html
- Statistiques d’usage : https://infogram.com/stats-dusage-dadministration-1hmr6gm9mk5o6nl?live

## Code

- [Guidelines code](docs/guidelines-code.md)
- [Guidelines design](docs/guidelines-design.md)
- [Wiki](https://github.com/betagouv/aplus/wiki) (Le wiki est visé à être remplacer par la doc dans le repo)

## Commandes

### 🚀 Déployer le projet en local avec docker compose 🚀

Cloner le projet :

```shell
git clone https://github.com/betagouv/aplus
cd aplus
```

Docker compose démarre les services suivants: 

- web : Play server pour la web app A+ - <http://localhost:9000>
- db : postgresql (database) - access with `docker compose run db psql -hdb -Uaplus -W`
- mailhog : mailhog pour les emails - <http://localhost:8025>
- minio : object storage s3 - <http://localhost:9091> (minioadmin / minioadmin)

```shell
# Start the database, mailhog and minio services in detached mode (background)
docker compose up -d db mailhog minio
# Start the web service
docker compose up web
```

Ajouter un dump de la base de données à votre projet. Pour cela prendre contact avec l'équipe **Administration+** qui vous enverra le fichier correspondant (*contact@aplus.beta.gouv.fr* ou directement sur Mattermost). Ajouter le fichier à la racine du projet puis lancer la commande suivante (des erreurs apparaîtront, ne les prenez pas en compte !):


```shell
# Restaurer la base de données avec le dump
docker compose exec -T db bash -c 'PGPASSWORD=mysecretpassword pg_restore -h localhost -U aplus -d aplus' < [path_to_dump]/dump.pgsql
```

✨ Enjoy ✨ (si vous tombez sur l'erreur *"database 'default' needs evolution"*, cliquez sur *"apply this script now"*) !

## Attribution

Le projet inclut le fichier `data/french_passwords_top20000.txt` sous licence [Creative Commons Attribution 4.0 International](https://creativecommons.org/licenses/by/4.0/) provenant du dépôt [tarraschk/richelieu](https://github.com/tarraschk/richelieu).
