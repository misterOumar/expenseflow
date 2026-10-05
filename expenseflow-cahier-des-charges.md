# ExpenseFlow - Cahier des charges

**Version** 1.0 - figée
**Durée** 4 semaines
**Statut du projet** Projet d'apprentissage. Objectif : maîtriser les fondamentaux Spring Boot et découvrir Angular.

---

## 1. Objectif du document

Ce document existe pour **limiter le périmètre**, pas pour le décrire.

ExpenseFlow est un projet d'échauffement. Sa valeur ne vient pas de sa richesse fonctionnelle mais de la qualité technique d'un périmètre volontairement étroit. Toute fonctionnalité ajoutée en cours de route est une semaine volée au projet vitrine qui suit.

**Règle unique :** si une idée n'est pas dans la section 4, elle n'est pas dans le projet. Elle ne devient pas « une petite exception », elle ne va pas « juste en bonus ». Elle va dans la section 5.

---

## 2. Contexte fonctionnel

Un employé engage des frais professionnels (repas, transport, hébergement). Il les regroupe dans une note de frais mensuelle, y joint ses justificatifs, et la soumet. Son manager valide ou rejette. Le comptable marque comme payée.

C'est tout. Il n'y a pas de deuxième cas d'usage.

---

## 3. Acteurs et rôles

| Rôle | Peut faire | Ne peut pas faire |
|---|---|---|
| `EMPLOYEE` | Créer, modifier et soumettre **ses** notes en brouillon. Consulter ses notes et leur historique. | Voir les notes d'un autre employé. Valider quoi que ce soit. |
| `MANAGER` | Tout ce que fait un `EMPLOYEE`, plus : consulter et approuver/rejeter les notes **de ses subordonnés directs**. | Approuver sa propre note. Marquer comme payée. |
| `ACCOUNTANT` | Consulter toutes les notes approuvées. Les marquer comme payées. Exporter en PDF. | Approuver ou rejeter. |

Un utilisateur a **exactement un rôle** et **exactement un manager** (nullable). Pas de rôles multiples, pas de délégation, pas de remplaçant en cas d'absence.

---

## 4. Périmètre inclus

### 4.1 Authentification

- Connexion par email + mot de passe, retour d'un JWT.
- Mot de passe hashé (BCrypt).
- Durée de vie du token : 24 h. Pas de refresh token.
- Pas d'inscription publique : les comptes sont créés par les managers.

### 4.2 Note de frais

Une note de frais appartient à un employé et couvre une période (mois + année). Elle contient une ou plusieurs lignes.

**Champs d'une note :** titre, période (mois/année), statut, montant total (calculé), dates de soumission et de décision, motif de rejet.

**Champs d'une ligne :** date de dépense, catégorie, description, montant HT, montant TVA, montant TTC (calculé), justificatif associé.

**Catégories (enum figé) :** `MEAL`, `TRANSPORT`, `ACCOMMODATION`, `SUPPLIES`, `OTHER`.

**Devise :** EUR uniquement, en dur. Pas de champ devise en base.

### 4.3 Workflow

```
DRAFT ──soumission──> SUBMITTED ──approbation──> APPROVED ──paiement──> PAID
                          │
                          └──rejet──> REJECTED ──correction──> DRAFT
```

**Règles de transition :**

| Transition | Rôle requis | Conditions |
|---|---|---|
| `DRAFT` → `SUBMITTED` | Propriétaire | Au moins 1 ligne. Chaque ligne a un justificatif. |
| `SUBMITTED` → `APPROVED` | Manager du propriétaire | Le manager n'est pas le propriétaire. |
| `SUBMITTED` → `REJECTED` | Manager du propriétaire | Motif de rejet obligatoire (10 caractères min). |
| `REJECTED` → `DRAFT` | Propriétaire | - |
| `APPROVED` → `PAID` | `ACCOUNTANT` | - |

Toute autre transition renvoie `409 Conflict`. Une note n'est modifiable qu'en statut `DRAFT`.

**Un seul niveau de validation.** Pas de circuit à deux managers, pas de seuil de montant déclenchant une validation supplémentaire.

### 4.4 Justificatifs

- Upload d'un fichier par ligne de frais.
- Formats acceptés : `application/pdf`, `image/jpeg`, `image/png`.
- Taille maximale : 5 Mo.
- Stockage : système de fichiers local, hors du répertoire servi statiquement.
- Téléchargement par un endpoint qui revérifie les droits d'accès.

### 4.5 Export PDF

Un endpoint génère un récapitulatif PDF d'une note en statut `APPROVED` ou `PAID` : en-tête (employé, période, statut), tableau des lignes, total. Généré à la volée, non stocké.

**Pas de logo d'entreprise, pas de mise en page travaillée, pas de template configurable.**

### 4.6 Notifications email

Un email est envoyé au destinataire concerné lors des transitions `SUBMITTED`, `APPROVED`, `REJECTED`.

- Envoi **après commit** de la transaction (voir section 7.2).
- Corps en texte brut. Pas de template HTML.
- En développement : MailHog ou log console. Pas de compte SMTP réel.

### 4.7 Historique

Chaque transition de statut écrit une ligne d'historique : statut avant, statut après, auteur, horodatage, commentaire. Consultable sur la note.

---

## 5. Périmètre exclu - définitif

Ces éléments sont écartés par décision, pas par manque de temps. Ils ne reviennent pas dans le projet, même si l'avance sur le planning le permettrait. En cas d'avance, on renforce les tests.

**Fonctionnel**
- Export Excel (Apache POI) - n'apprend rien que PDFBox n'enseigne déjà
- Notifications in-app, temps réel, WebSocket
- Hiérarchie managériale à plusieurs niveaux, délégation, suppléance
- Plafonds par catégorie, règles de politique de dépense, alertes de dépassement
- Multi-devises, taux de change
- Gestion des utilisateurs par interface (CRUD admin)
- Inscription publique, mot de passe oublié, vérification email
- Statistiques, tableaux de bord, graphiques
- Recherche full-text, filtres avancés
- Import de relevés bancaires ou de cartes affaires
- OCR sur les justificatifs
- Commentaires ou fil de discussion sur une note
- Pièces jointes multiples par ligne
- Archivage, corbeille, suppression logique

**Technique**
- Spring State Machine - surdimensionné pour 5 états ; un enum avec méthodes de garde suffit et se défend mieux
- MinIO / stockage objet - le système de fichiers couvre le besoin
- Refresh tokens, OAuth2, SSO
- Cache (Redis, Caffeine)
- Message broker
- Multi-tenancy
- Microservices - application monolithique, un seul module Maven
- Internationalisation - français uniquement
- Tests end-to-end, tests front

---

## 6. Modèle de données

```sql
users            (id, email UNIQUE, password_hash, full_name, role, manager_id → users)

expense_reports  (id, owner_id → users, title, period_month, period_year,
                  status, total_amount, submitted_at, decided_at,
                  decided_by → users, rejection_reason, version)

expense_lines    (id, report_id → expense_reports, spent_on, category,
                  description, amount_ht, vat_amount, amount_ttc,
                  receipt_id → receipts)

receipts         (id, original_filename, stored_key, content_type,
                  size_bytes, sha256, uploaded_by → users, uploaded_at)

status_history   (id, report_id → expense_reports, from_status, to_status,
                  actor_id → users, at, comment)
```

**Contraintes**
- Montants en `NUMERIC(12,2)`, mappés en `BigDecimal`. Jamais `double`.
- `version` sur `expense_reports` pour le verrouillage optimiste.
- Unicité sur `(owner_id, period_month, period_year)` : une seule note par employé et par mois.
- Toutes les migrations via Flyway. `spring.jpa.hibernate.ddl-auto: validate`, jamais `update`.

---

## 7. Exigences techniques

### 7.1 Stack imposée

| Couche | Choix |
|---|---|
| Langage | Java 21 |
| Framework | Spring Boot 4.x |
| Persistance | Spring Data JPA + Hibernate |
| Base | PostgreSQL 16 (Docker) |
| Migrations | Flyway |
| Sécurité | Spring Security + JWT |
| Mapping | MapStruct |
| PDF | Apache PDFBox |
| Tests | JUnit 5, Mockito, Testcontainers |
| Build | Maven |
| Front | Angular + Angular Material |
| CI | GitHub Actions |
| Déploiement | Docker + Coolify |

### 7.2 Trois points à traiter explicitement

Ce sont les arguments d'entretien du projet. Ils doivent être implémentés **et** documentés dans le README.

**Le problème N+1.** Charger une liste de notes avec leurs lignes déclenche naïvement N+1 requêtes. Constater le nombre avec les statistiques Hibernate, corriger avec `@EntityGraph` ou `join fetch`, consigner les deux chiffres.

**L'email après commit.** Aucun appel réseau à l'intérieur d'une transaction : un SMTP lent immobilise la connexion base, et un rollback survenant après l'envoi laisse un email annonçant une décision qui n'existe pas. Implémentation : `@TransactionalEventListener(phase = AFTER_COMMIT)`.

**L'upload sécurisé.** Ne jamais faire confiance au nom de fichier ni au `Content-Type` fourni par le client. Vérifier les magic bytes, liste blanche d'extensions, nom de stockage en UUID, chemin hors du répertoire public, plafond de taille. Servir le fichier par un endpoint qui contrôle les droits, jamais par une URL devinable.

### 7.3 Conventions API

- REST, préfixe `/api`.
- Erreurs au format `ProblemDetail` (RFC 7807), natif Spring 6.
- Gestionnaire d'exceptions global. Aucune stack trace renvoyée au client.
- Validation par Bean Validation sur les DTO d'entrée.
- Jamais d'entité JPA exposée : DTO en entrée et en sortie.
- Documentation OpenAPI via springdoc.

---

## 8. Endpoints

```
POST   /api/auth/login

GET    /api/reports                      liste filtrée selon le rôle
POST   /api/reports                      création d'un brouillon
GET    /api/reports/{id}
PUT    /api/reports/{id}                 statut DRAFT uniquement
DELETE /api/reports/{id}                 statut DRAFT uniquement

POST   /api/reports/{id}/lines
PUT    /api/reports/{id}/lines/{lineId}
DELETE /api/reports/{id}/lines/{lineId}

POST   /api/reports/{id}/submit
POST   /api/reports/{id}/approve
POST   /api/reports/{id}/reject          body : { reason }
POST   /api/reports/{id}/pay

GET    /api/reports/{id}/history
GET    /api/reports/{id}/export.pdf

POST   /api/receipts                     multipart
GET    /api/receipts/{id}                contrôle d'accès
```

Les transitions sont des endpoints d'action dédiés, pas un `PATCH` sur le champ `status` - l'intention métier reste explicite et l'autorisation se contrôle par action.

---

## 9. Front Angular

**Semaine 4 uniquement, backend gelé.** Quatre écrans, pas un de plus.

1. **Login** - formulaire, stockage du token, intercepteur HTTP, guard de route.
2. **Mes notes** - liste avec statut, accès au détail.
3. **Édition d'une note** - formulaire réactif avec lignes dynamiques (`FormArray`), upload de justificatif, bouton de soumission.
4. **Validation manager** - liste des notes en attente, approbation, rejet avec motif.

**Exclu du front :** design personnalisé (Angular Material par défaut), librairie de state management, SSR, lazy loading, tests, écran comptable (le passage en `PAID` se fait via Swagger).

---

## 10. Planning

| Semaine | Contenu | Livrable |
|---|---|---|
| 1 | Setup, Docker PostgreSQL, Flyway, entités, DTO/MapStruct, CRUD brouillon, gestion d'erreurs globale | CRUD fonctionnel sur Swagger |
| 2 | Spring Security + JWT, rôles, autorisation au niveau ressource, transitions et gardes, historique, tests Testcontainers | Workflow complet testé |
| 3 | Upload, export PDF, email après commit, Dockerfile, CI, déploiement Coolify, README | Application en ligne |
| 4 | Angular : 4 écrans | Front connecté au back déployé |

**Date butoir dure :** à la fin de la semaine 4, ExpenseFlow est déployé quel qu'en soit l'état. En cas de retard, on réduit le périmètre - on ne prolonge pas. Les écrans Angular se coupent avant les tests.

---

## 11. Définition de terminé

- [ ] Application accessible par une URL publique
- [ ] Pipeline CI verte sur la branche principale
- [ ] Au moins un test d'intégration Testcontainers par transition de statut
- [ ] Les transitions interdites renvoient `409` et sont couvertes par des tests
- [ ] Un employé ne peut accéder à la note d'un autre - testé
- [ ] Un manager ne peut pas approuver sa propre note - testé
- [ ] README contenant : schéma de données, les trois points de la section 7.2 expliqués, commandes de lancement local
- [ ] Jeu de données de démonstration chargé par Flyway (3 employés, 1 manager, 1 comptable, quelques notes dans chaque statut)

---

## 12. Journal des refus

À remplir en cours de route. Toute idée écartée s'écrit ici plutôt que de rentrer dans le code. C'est ce qui protège le planning.

| Date | Idée | Raison du refus |
|---|---|---|
| 2026-10-05 | Rester sur Spring Boot 3.x | Initializr fournit la 4.x ; c'est la version actuelle et celle du projet vitrine. Ce n'est pas un ajout de périmètre, juste une mise à jour de version. |
