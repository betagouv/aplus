# Droits d'accès (autorisations) et rôles


## Listes des rôles

- Aidant
    - Travailleurs Sociaux
    - Délégué du défenseur des droits
    - Agents d'administration
- Instructeur (Opérateur)
    - Agent d'administration (CAF, CPAM, Impôt, Préfecture, ...)
- Administrateur général
    - Intrapreneuse A+
    - Animateur de communauté d'A+
    - Développeur A+
- Administrateur de zone
    - Chargé de déploiement en région ou département
- Responsable de groupe
    - Responsable d'agents administratifs
- Expert
    - Juriste
    - Travailleurs sociaux
    - Agents d'administration
- Observateur
    - Pilote France Services
- Administrateur système
    - Développeur de l'équipe A+


## Fonctionnalités par rôle

Note
- Zone : zone géographique (département)
- Groupe : sous-division d'une zone
- Les administrateurs général et de zone n'ont pas accès aux données personnelles des usagers
- Metadonnées d'une demande = tout ce qui ne contient pas de données personnelles (créateur, date de création, utilisateurs invités, ...)


- :construction: : Fonctionnalités à venir
- :warning: Fonctionnalités sensible pouvant conduire à accéder à des données personnelles d'usager

### Connexion


#### Connexion à l'application

* `/login`
* `/validation-connexion`

|Admin Général|Admin zone|Respo Groupe|Aidant|Instructeur|Expert|Observateur|
|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✖ |


#### Double authentification (Par SMS) :construction:

|Admin Général|Admin zone|Respo Groupe|Aidant|Instructeur|Expert|Observateur|
|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| ✔ | ✔ | ✔ | ✖ | ✖ | ✖ | ✖ |


#### Déconnexion

* `/login/disconnect`


### Demande



#### Créer une demande

* `/nouvelle-demande`


|Admin Général|Admin zone|Respo Groupe|Aidant|Instructeur|Expert|Observateur|
|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| ✖ | ✖ | ✖ | ✔ | ✖ | ✖ | ✖ |


#### Visualisation de la conversation sur une demande :warning:

* `/toutes-les-demandes/:id`

|Admin Général|Admin zone|Respo Groupe|Aidant|Instructeur|Expert|Observateur|
|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| ✖ | ✖ | ✖ | ✔ (créateur ou invité)| ✔ (invité)| ✖ | ✖ |


#### Création d'un mandat par SMS

* `/mandats/sms`

|Admin Général|Admin zone|Respo Groupe|Aidant|Instructeur|Expert|Observateur|
|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| ✖ | ✖ | ✖ | ✔ | ✖ | ✖ | ✖ |


#### Visualisation d'un mandat SMS (avec données personnelles :warning:)

* `/mandats/:id`

|Admin Général|Admin zone|Respo Groupe|Aidant|Instructeur|Expert|Observateur|
|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| ✖ | ✖ | ✖ | ✔ (créateur) | ✖ | ✖ | ✖ |


#### Visualisation des métadonnées d'un mandat SMS

* `/mandats/:id`

|Admin Général|Admin zone|Respo Groupe|Aidant|Instructeur|Expert|Observateur|
|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| ✔ | ✔ | ✖ | ✔ (créateur) | ✖ | ✖ | ✖ |


#### Archiver (clôturer) une demande

* `/toutes-les-demandes/:applicationId/terminer`

|Admin Général|Admin zone|Respo Groupe|Aidant|Instructeur|Expert|Observateur|
|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| ✔ | ✖ | ✖ | ✔ (créateur) | ✖ | ✔ (invité) | ✖ |


#### Désarchiver une demande

* `/toutes-les-demandes/:applicationId/reopen`

|Admin Général|Admin zone|Respo Groupe|Aidant|Instructeur|Expert|Observateur|
|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| ✔ | ✖ | ✖ | ✔ (créateur) | ✖ | ✔ (invité) | ✖ |



#### Visualisation de mes demandes en cours (:warning: données personnelles)

* `/toutes-les-demandes`

|Admin Général|Admin zone|Respo Groupe|Aidant|Instructeur|Expert|Observateur|
|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| ✖ | ✖ | ✖ | ✔ | ✔ | ✔ | ✖ |


#### Visualisation de mes demandes clôturées (:warning: données personnelles)

* `/toutes-les-demandes`

|Admin Général|Admin zone|Respo Groupe|Aidant|Instructeur|Expert|Observateur|
|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| ✖ | ✖ | ✖ | ✔ | ✔ | ✖ | ✖ |


#### Téléchargement de mes demandes en CSV (métadonnées)

* `/toutes-les-demandes.csv`
* `/exporter-mes-demandes`

|Admin Général|Admin zone|Respo Groupe|Aidant|Instructeur|Expert|Observateur|
|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| ✖ | ✖ | ✔ | ✔ | ✔ | ✖ | ✖ |


#### Répondre à une demande

* `/toutes-les-demandes/:applicationId/messages`

|Admin Général|Admin zone|Respo Groupe|Aidant|Instructeur|Expert|Observateur|
|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| ✖ | ✖ | ✖ | ✔ | ✔ | ✔ | ✖ |


#### Inviter un expert sur une demande :warning:

:warning: l'invité a accès aux données personnelles sur la demande

* `/toutes-les-demandes/:applicationId/inviter_des_experts`

|Admin Général|Admin zone|Respo Groupe|Aidant|Instructeur|Expert|Observateur|
|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| ✖ | ✖ | ✖ | ✔ | ✔ | ✖ | ✖ |


#### Inviter une administration sur une demande :warning:

:warning: les invités ont accès aux données personnelles sur la demande

* `/toutes-les-demandes/:applicationId/inviter_des_utilisateurs`

|Admin Général|Admin zone|Respo Groupe|Aidant|Instructeur|Expert|Observateur|
|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| ✖ | ✖ | ✖ | ✔ (si invité) | ✔ (si invité) | ✔ | ✖ |



#### Ajouter une pièce jointe

* `/nouvelle-demande`
* `/toutes-les-demandes/:applicationId/messages`

|Admin Général|Admin zone|Respo Groupe|Aidant|Instructeur|Expert|Observateur|
|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| ✖ | ✖ | ✖ | ✔ | ✔ | ✖ | ✖ |


#### Supprimer une pièce jointe :construction:

|Admin Général|Admin zone|Respo Groupe|Aidant|Instructeur|Expert|Observateur|
|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| ✖ | ✖ | ✖ | ✖ | ✖ | ✖ | ✖ |


#### Voir une pièce jointe

* `/toutes-les-demandes/:applicationId/fichiers/:filename`
* `/toutes-les-demandes/:applicationId/messages/:answerId/fichiers/:filename`

|Admin Général|Admin zone|Respo Groupe|Aidant|Instructeur|Expert|Observateur|
|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| ✖ | ✖ | ✖ | ✔ (créateur ET instructeur a marqué le message comme visible) | ✔ (invité) | ✖ | ✖ |






### Administration sur les demandes



#### Visualisation des demandes en cours et clôturées du groupe (métadonnées seulement)

* `/territoires/:areaId/demandes`
* `/territoires/:areaId/demandes.csv`

|Admin Général|Admin zone|Respo Groupe|Aidant|Instructeur|Expert|Observateur|
|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| ✖ | ✖ | ✔ | ✖ | ✖ | ✖ | ✖ |


#### Visualisation des demandes en cours et clôturées de la zone (métadonnées seulement)

* `/territoires/:areaId/demandes`
* `/territoires/:areaId/demandes.csv`

|Admin Général|Admin zone|Respo Groupe|Aidant|Instructeur|Expert|Observateur|
|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| ✔ | ✔ | ✖ | ✖ | ✖ | ✖ | ✖ |

#### Visualisation de la liste des demandes d'un utilisateur (métadonnées seulement)

* `/as/:userId/toutes-les-demandes`

|Admin Général|Admin zone|Respo Groupe|Aidant|Instructeur|Expert|Observateur|
|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| ✔ | ✔ | ✖ | ✖ | ✖ | ✖ | ✖ |




### Utilisateurs et groupes


#### Terminer sa préinscription et choisir son groupe

* `/inscription`

|Admin Général|Admin zone|Respo Groupe|Aidant|Instructeur|Expert|Observateur|
|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| ✖ | ✖ | ✖ | ✔ | ✖ | ✖ | ✖ |


#### Modifier mes données (mon profil = nom, prénom, qualité, téléphone)

* `/me`

|Admin Général|Admin zone|Respo Groupe|Aidant|Instructeur|Expert|Observateur|
|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |


#### Créer un compte utilisateur dans A+

* `/groups/:groupId/users`

|Admin Général|Admin zone|Respo Groupe|Aidant|Instructeur|Expert|Observateur|
|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| ✔ | ✔ | ✔ (dans ses groupes) | ✖ | ✖ | ✖ | ✖ |

#### Ajouter un utilisateur créé dans A+ dans l'un de ses groupes

* `/utilisateurs/:userId`
* `/groups/:groupId/add`

|Admin Général|Admin zone|Respo Groupe|Aidant|Instructeur|Expert|Observateur|
|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |

#### Désactiver un utilisateur

* `/groups`
* `/groups/:groupId/remove/:userId`
* `/utilisateurs/:userId`

|Admin Général|Admin zone|Respo Groupe|Aidant|Instructeur|Expert|Observateur|
|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| ✔ | ✔ | ✔ (de ses groupes) | ✔ (de ses groupes) | ✔ (de ses groupes) | ✔ (de ses groupes) | ✔ (de ses groupes) |

#### Réactiver un utilisateur

* `/groups`
* `/utilisateurs/:userId/reactivation`
* `/utilisateurs/:userId`

|Admin Général|Admin zone|Respo Groupe|Aidant|Instructeur|Expert|Observateur|
|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| ✔ | ✔ | ✔ (de ses groupes) | ✔ (de ses groupes) | ✔ (de ses groupes) | ✔ (de ses groupes) | ✔ (de ses groupes) |


#### Supprimer un utilisateur inactif

* `/user/delete/unused/:userId`
* `/utilisateurs/:userId`

|Admin Général|Admin zone|Respo Groupe|Aidant|Instructeur|Expert|Observateur|
|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| ✔ | ✔ | ✖ | ✖ | ✖ | ✖ | ✖ |

#### Nommer un utilisateur aidant

* `/utilisateurs/:userId`

|Admin Général|Admin zone|Respo Groupe|Aidant|Instructeur|Expert|Observateur|
|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| ✔ | ✔ | ✔ | ✖ | ✖ | ✖ | ✖ |

#### Nommer un utilisateur instructeur

* `/utilisateurs/:userId`

|Admin Général|Admin zone|Respo Groupe|Aidant|Instructeur|Expert|Observateur|
|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| ✔ | ✔ | ✔ | ✖ | ✖ | ✖ | ✖ |


#### Nommer un responsable de groupe

* `/utilisateurs/:userId`

|Admin Général|Admin zone|Respo Groupe|Aidant|Instructeur|Expert|Observateur|
|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| ✔ | ✔ | ✔ | ✖ | ✖ | ✖ | ✖ |


#### Nommer un administrateur de zone

|Admin Général|Admin zone|Respo Groupe|Aidant|Instructeur|Expert|Observateur|
|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| ✖ | ✖ | ✖ | ✖ | ✖ | ✖ | ✖ |

#### Nommer un utilisateur expert

|Admin Général|Admin zone|Respo Groupe|Aidant|Instructeur|Expert|Observateur|
|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| ✖ | ✖ | ✖ | ✖ | ✖ | ✖ | ✖ |


#### Modifier les données d'un utilisateur (nom, prénom, qualité, téléphone)

* `/utilisateurs/:userId`

|Admin Général|Admin zone|Respo Groupe|Aidant|Instructeur|Expert|Observateur|
|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| ✔ | ✔ | ✔ | ✖ | ✖ | ✖ | ✖ |


#### Modifier l'adresse email d'un utilisateur :warning:

* `/utilisateurs/:userId`

|Admin Général|Admin zone|Respo Groupe|Aidant|Instructeur|Expert|Observateur|
|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| ✔ | ✖ | ✖ | ✖ | ✖ | ✖ | ✖ |


#### Créer un groupe d'utilisateurs

* `/utilisateurs`
* `/groups`

|Admin Général|Admin zone|Respo Groupe|Aidant|Instructeur|Expert|Observateur|
|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| ✔ | ✔ | ✖ | ✖ | ✖ | ✖ | ✖ |


#### Modifier un groupe

* `/groups/:groupId`

|Admin Général|Admin zone|Respo Groupe|Aidant|Instructeur|Expert|Observateur|
|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| ✔ | ✔ | ✔ (est dans ce groupe) | ✖ | ✖ | ✖ | ✖ |


#### Supprimer un groupe vide

* `/group/delete/unused/:groupId`

|Admin Général|Admin zone|Respo Groupe|Aidant|Instructeur|Expert|Observateur|
|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| ✔ | ✔ | ✖ | ✖ | ✖ | ✖ | ✖ |

#### Lister les utilisateurs

* `/utilisateurs`
* `/territoires/:areaId/utilisateurs`
* `/territoires/:areaId/utilisateurs.csv`


|Admin Général|Admin zone|Respo Groupe|Aidant|Instructeur|Expert|Observateur|
|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| ✔ | ✔ | ✔ (ses groupes) | ✖ | ✖ | ✖ | ✖ |

#### Lister les groupes

* `/territoires`
* `/territoires/:areaId` (deprecated)

|Admin Général|Admin zone|Respo Groupe|Aidant|Instructeur|Expert|Observateur|
|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| ✔ | ✔ | ✔ (ses groupes) | ✖ | ✖ | ✖ | ✖ |


#### Importer des utilisateurs et groupes par CSV

* `/importation/utilisateurs-depuis-csv`
* `/importation/revue-import`
* `/importation/import`

|Admin Général|Admin zone|Respo Groupe|Aidant|Instructeur|Expert|Observateur|
|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| ✔ | ✔ | ✖ | ✖ | ✖ | ✖ | ✖ |


#### Ajouter des emails sans groupes (préinscriptions)

* `/utilisateurs/inscriptions`
* `/utilisateurs/inscriptions/add-emails`

|Admin Général|Admin zone|Respo Groupe|Aidant|Instructeur|Expert|Observateur|
|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| ✔ | ✖ | ✖ | ✖ | ✖ | ✖ | ✖ |



### Statistiques et déploiement



#### Voir l'état de déploiement

* `/territoires/deploiement`
* `/territoires/deploiement/france-service`
* `/api/deploiement/france-service`

|Admin Général|Admin zone|Respo Groupe|Aidant|Instructeur|Expert|Observateur|
|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| ✔ | ✖ | ✖ | ✖ | ✖ | ✖ | ✔ |

#### Voir les stats globales

* `/stats`

|Admin Général|Admin zone|Respo Groupe|Aidant|Instructeur|Expert|Observateur|
|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |

#### Voir les stats de la zone

* `/stats`

|Admin Général|Admin zone|Respo Groupe|Aidant|Instructeur|Expert|Observateur|
|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |

#### Voir les stats du groupe

* `/stats`

|Admin Général|Admin zone|Respo Groupe|Aidant|Instructeur|Expert|Observateur|
|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| ✔ | ✔ | ✔ (ses groupes) | ✔ (ses groupes) | ✔ (ses groupes) | ✖ | ✖ |


#### Voir le log d'évènements (sans données personnelles)

* `/events`

|Admin Général|Admin zone|Respo Groupe|Aidant|Instructeur|Expert|Observateur|
|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| ✔ | ✔ | ✖ | ✖ | ✖ | ✖ | ✖ |


### Autres

#### Après connexion

Validation des CGUs

* `/cgu`

Inscription à la newsletter

* `/newsletter`


#### Sans connexion

Status de l'application

* `/status`

Webhook SMS

* `/mandats/sms/webhook`
