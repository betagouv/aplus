# Guidelines de Code

> Ces guidelines sont là pour nous simplifier la vie sur le long terme.

## Scala
- Log des événements avec eventService, pas d'informations personnelles de l'usager dans les logs (mais les ids doivent être loggués pour être recoupés). Le nom d'un utilisateur peut être mentionné.
   - Info : action normale
   - Warn : quelque chose de bizarre c'est passé mais on continue l'execution
   - Error : une erreur c'est produite et on a refusé de faire l'action
- Vérifier la matrice des droits : [docs/authorization.md](authorization.md)
- Gérer les erreurs immédiatement, écrire l'action ensuite
- Utiliser le router de Play pour la gestion des URLs : https://www.playframework.com/documentation/2.7.x/ScalaRouting
- Les urls publiques de l'applications sont en francais (/utilisateurs), le reste est en anglais.
- Il y a des migrations, les faire au fur et à mesure
- Privilégier la definition d'une méthode avec `def` plutôt que sur une `var`
- Les services ne peuvent être utilisés que depuis les controlleurs
- Les arguments boolean doivent être nommés
- Lancer scalafmt avant de push

## Concept de développement
- Utiliser un maximum de fonctions pures
- Séparation des préoccupations (Separation of concerns) : https://fr.wikipedia.org/wiki/S%C3%A9paration_des_pr%C3%A9occupations
- Convention plutôt que configuration (Convention over configuration) : https://fr.wikipedia.org/wiki/Convention_plut%C3%B4t_que_configuration
- The Boy Scout Rule : "Always leave the campground cleaner than you found it." https://www.oreilly.com/library/view/97-things-every/9780596809515/ch08.html
- Les noms de variables, noms de méthodes sont en anglais (sauf les termes francais intraduisibles) et très explicite (éviter les abréviations)
- Ne pas faire de référence circulaire
- Penser à la loi de murphy (si ça peut tourner mal, statistiquement ça va arriver)

## CSS
- Utiliser BEM pour écrire du CSS http://getbem.com/introduction/
- Mettre un maximum de code CSS dans des fichiers .css
- Utiliser des éléments CSS compatible Internet Explorer 11 
   - https://caniuse.com/
   - Les var ne sont pas dispo
   - Flexbox est dispo
- Placer le code css dans un fichier `.css` (éviter les inlines)
- Ecrire du css réutilisable si c'est possible

## Javascript/Typescript
- Le Javascript est compilé à 100% depuis le Typescript (sauf ce qui est auto-généré ou le script de stats)
- Privilégier le Javascript Vanille
- Utiliser le router Javascript de Play : https://www.playframework.com/documentation/2.7.x/ScalaJavascriptRouting
- Le Javascript inline est interdit (pas de CSP unsafe-inline)
- Ecrire des fonctions javascript réutilisables si c'est possible
- Les dépendances dans `package.json` sont données avec une version exacte (pas de `^` ou `~`)
- Le `package-lock.json` n'est pas commit, du à la difficulté de vérifier ce qu'il y a à l'intérieur


## Git
- Branche
   - master : version de dev : déployé automatiquement sur http://demo-aplus.beta.gouv.fr
   - prod : version de prod : déployé manuellement : PR mergé en fast-forward depuis master
- Une branche pour chaque développement 
   - feature/123-feature-name-card : Ajout d'un nouvelle fonctionnalité
   - task/234-task-name : Refactoring ou modification d'une fonctionnalité existante
   - fix/125-fix-name : Correction d'un bug
- Pull request pour chaque développement
   - On peut regrouper dans une même PR des taches ou fonctionnalités similiares
   - Une PR ne peut pas représenter plus d'une journée de boulot (sans inclure les reviews)
   - Nom de la "Nom de la fonctionnalité/tache" -> Un truc facile à mettre dans un Changelog
   - Dans le descriptif de la PR : mettre la liste des changements et des éléments à tester pour le reviewer
   - Mettre des captures d'écran pour les changements visuels
   - Demander à un membre de l'équipe non développeur de tester les nouvelles fonctionnalités
   - Avoir une review ou un go d'un autre développeur avant de merger
  

## Infos
### Demo
- Version de demo sur : http://demo-aplus.beta.gouv.fr
- Les emails de la démo arrive sur : https://mailspons.com/
- La démo est hébergé sur Heroku
- Les exceptions en demo sont renvoyé sur sentry.io
### Prod
- L'URL de prod est : https://aplus.beta.gouv.fr
- Les emails de la prod sont envoyé par Mailjet
- La prod est hébergé sur OVH
- Les exceptions en prod sont renvoyé sur sentry.io
### Navigateurs
- Navigateurs cible
   - Internet Explorer 11
   - Chrome
   - Firefox
