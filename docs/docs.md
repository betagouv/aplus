# Documentation


## Checks PR

Toujours linter le code avant de faire une PR.

Lint Scala à faire avec sbt :

```
scalafixAll -r OrganizeImports
scalafmtAll
```


## Organisation du code

### Modèle métier

La plupart des classes dans `app/models` correspondent au modèle métier. Il faut utiliser les traductions suivantes :
-  `Area` = Territoire
-  `Application` = Demande
-  `Organisation` = Organisme
-  `UserGroup` = Structure (aussi appelé groupe)


### Sérialisation

#### Formulaires HTTP

Tous les formulaires sont dans `app/models/forms.scala`. Ils utilisent les classes `Form` ou `Mapping` de Play.


### Code réutilisable dont dépend le code métier

Tout le code réutilisable qui ne concerne pas directement le métier est dans `app/helper`. Il ne doit pas y avoir de logique métier ou de dépendance sur les classes métier.

-  `app/helper/forms` : extensions autour de la classe `Form` de Play
