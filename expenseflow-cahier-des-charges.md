# ExpenseFlow - Cahier des charges

**Version** 1.1
**Date** 2026-10-05
**Durée de réalisation** 4 semaines

---

## 1. Objet du document

Ce document définit les exigences fonctionnelles et techniques de l'application ExpenseFlow, application web de gestion des notes de frais.

Il fixe le périmètre de la version 1.0 : seules les fonctionnalités décrites en section 4 sont à réaliser. Les éléments listés en section 5 sont explicitement hors périmètre. Toute demande nouvelle fait l'objet d'une décision consignée en section 12 avant toute réalisation.

---

## 2. Contexte fonctionnel

Un employé engage des frais professionnels (repas, transport, hébergement). Il les regroupe dans une note de frais mensuelle, y joint ses justificatifs et la soumet. Son manager valide ou rejette la note. Le comptable la marque comme payée.

L'application couvre ce seul processus.

---

## 3. Acteurs et rôles

| Rôle | Droits | Interdictions |
|---|---|---|
| `EMPLOYEE` | Créer, modifier et soumettre **ses** notes en brouillon. Consulter ses notes et leur historique. | Voir les notes d'un autre employé. Valider une note. |
| `MANAGER` | Droits d'un `EMPLOYEE`, plus : consulter et approuver/rejeter les notes **de ses subordonnés directs**. | Approuver sa propre note. Marquer une note comme payée. |
| `ACCOUNTANT` | Consulter toutes les notes approuvées. Les marquer comme payées. Les exporter en PDF. | Approuver ou rejeter une note. |

Un utilisateur possède **exactement un rôle** et **au plus un manager**. Les rôles multiples, la délégation et la suppléance ne sont pas gérés.

---

## 4. Exigences fonctionnelles

### 4.1 Authentification

- Connexion par email et mot de passe ; le serveur retourne un jeton JWT.
- Mots de passe stockés hashés (BCrypt).
- Durée de vie du jeton : 24 h. Pas de jeton de rafraîchissement.
- Pas d'inscription publique : les comptes sont créés par les managers.

### 4.2 Note de frais

Une note de frais appartient à un employé et couvre une période (mois et année). Elle contient une ou plusieurs lignes.

**Champs d'une note :** titre, période (mois/année), statut, montant total (calculé), dates de soumission et de décision, auteur de la décision, motif de rejet.

**Champs d'une ligne :** date de dépense, catégorie, description, montant HT, montant TVA, montant TTC (calculé), justificatif associé.

**Catégories :** `MEAL`, `TRANSPORT`, `ACCOMMODATION`, `SUPPLIES`, `OTHER`. Liste fermée.

**Devise :** EUR uniquement. Aucun champ devise n'est stocké.

**Règles :**
- Un employé possède au plus une note par période.
- Le montant TTC d'une ligne est égal à la somme du montant HT et du montant TVA.
- Le montant total d'une note est égal à la somme des montants TTC de ses lignes.

### 4.3 Workflow

```
DRAFT ──soumission──> SUBMITTED ──approbation──> APPROVED ──paiement──> PAID
                          │
                          └──rejet──> REJECTED ──correction──> DRAFT
```

**Règles de transition :**

| Transition | Acteur autorisé | Conditions |
|---|---|---|
| `DRAFT` → `SUBMITTED` | Propriétaire | Au moins une ligne. Chaque ligne a un justificatif. |
| `SUBMITTED` → `APPROVED` | Manager du propriétaire | Le manager n'est pas le propriétaire. |
| `SUBMITTED` → `REJECTED` | Manager du propriétaire | Motif de rejet obligatoire (10 caractères minimum). |
| `REJECTED` → `DRAFT` | Propriétaire | - |
| `APPROVED` → `PAID` | `ACCOUNTANT` | - |

Toute autre transition est refusée avec le code `409 Conflict`. Une note n'est modifiable qu'en statut `DRAFT`.

La validation comporte un seul niveau : pas de circuit à plusieurs valideurs, pas de seuil de montant déclenchant une validation supplémentaire.

### 4.4 Justificatifs

- Un fichier par ligne de frais.
- Formats acceptés : `application/pdf`, `image/jpeg`, `image/png`.
- Taille maximale : 5 Mo.
- Stockage sur le système de fichiers local, hors de tout répertoire servi statiquement.
- Téléchargement uniquement via un endpoint qui contrôle les droits d'accès.

### 4.5 Export PDF

Un endpoint génère un récapitulatif PDF d'une note en statut `APPROVED` ou `PAID` : en-tête (employé, période, statut), tableau des lignes, total. Le document est généré à la volée et n'est pas stocké.

Le document ne comporte ni logo, ni mise en page personnalisée, ni template configurable.

### 4.6 Notifications email

Un email est envoyé au destinataire concerné lors des transitions vers `SUBMITTED`, `APPROVED` et `REJECTED`.

- Envoi uniquement **après validation (commit)** de la transaction (voir section 7.2).
- Corps en texte brut, sans template HTML.
- En environnement de développement : MailHog ou journalisation console, sans serveur SMTP réel.

### 4.7 Historique

Chaque transition de statut enregistre une entrée d'historique : statut avant, statut après, auteur, horodatage, commentaire. L'historique est consultable sur la note.

---

## 5. Hors périmètre

Les éléments suivants sont exclus de la version 1.0.

**Fonctionnel**
- Export Excel
- Notifications in-app, temps réel, WebSocket
- Hiérarchie managériale à plusieurs niveaux, délégation, suppléance
- Plafonds par catégorie, règles de politique de dépense, alertes de dépassement
- Multi-devises, taux de change
- Gestion des utilisateurs par interface (CRUD d'administration)
- Inscription publique, mot de passe oublié, vérification d'email
- Statistiques, tableaux de bord, graphiques
- Recherche plein texte, filtres avancés
- Import de relevés bancaires ou de cartes affaires
- OCR sur les justificatifs
- Commentaires ou fil de discussion sur une note
- Pièces jointes multiples par ligne
- Archivage, corbeille, suppression logique

**Technique**
- Spring State Machine : le workflow à 5 états est porté par un enum et des méthodes de garde
- Stockage objet (MinIO, S3) : le système de fichiers local couvre le besoin
- Jetons de rafraîchissement, OAuth2, SSO
- Cache (Redis, Caffeine)
- Message broker
- Multi-tenancy
- Microservices : application monolithique, un seul module Maven
- Internationalisation : interface en français uniquement
- Tests end-to-end, tests front

---

## 6. Modèle de données

```sql
users            (id, email UNIQUE, password_hash, full_name, role, manager_id → users, created_at)

expense_reports  (id, owner_id → users, title, period_month, period_year,
                  status, total_amount, submitted_at, decided_at,
                  decided_by → users, rejection_reason, version)

expense_lines    (id, report_id → expense_reports, spent_on, category,
                  description, amount_ht, vat_amount, amount_ttc,
                  receipt_id → receipts UNIQUE)

receipts         (id, original_filename, stored_key UNIQUE, content_type,
                  size_bytes, sha256, uploaded_by → users, uploaded_at)

status_history   (id, report_id → expense_reports, from_status, to_status,
                  actor_id → users, at, comment)
```

**Contraintes**
- Montants en `NUMERIC(12,2)`, mappés en `BigDecimal`. Aucun type flottant.
- Colonne `version` sur `expense_reports` pour le verrouillage optimiste.
- Unicité sur `(owner_id, period_month, period_year)`.
- Contraintes `CHECK` en base sur les énumérations, les bornes de période, la cohérence `amount_ttc = amount_ht + vat_amount`, le type et la taille des justificatifs.
- Schéma géré exclusivement par migrations Flyway. `spring.jpa.hibernate.ddl-auto: validate`.

---

## 7. Exigences techniques

### 7.1 Stack

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

### 7.2 Exigences techniques spécifiques

Les trois points suivants doivent être implémentés et documentés dans le README.

**Performance des requêtes (N+1).** Le chargement d'une liste de notes avec leurs lignes ne doit pas déclencher une requête par note. Le nombre de requêtes est mesuré avec les statistiques Hibernate avant et après optimisation (`@EntityGraph` ou `join fetch`) ; les deux mesures sont consignées.

**Envoi des emails après commit.** Aucun appel réseau n'est effectué à l'intérieur d'une transaction : un serveur SMTP lent immobiliserait la connexion à la base, et un rollback postérieur à l'envoi produirait un email annonçant une décision inexistante. Implémentation : `@TransactionalEventListener(phase = AFTER_COMMIT)`.

**Sécurité de l'upload.** Le nom de fichier et le `Content-Type` fournis par le client ne sont pas considérés comme fiables. Exigences : vérification des magic bytes, liste blanche d'extensions, nom de stockage généré (UUID), stockage hors du répertoire public, plafond de taille. Le fichier est servi par un endpoint qui contrôle les droits, jamais par une URL devinable.

### 7.3 Conventions API

- REST, préfixe `/api`.
- Erreurs au format `ProblemDetail` (RFC 9457).
- Gestionnaire d'exceptions global. Aucune stack trace n'est renvoyée au client.
- Validation par Bean Validation sur les DTO d'entrée.
- Aucune entité JPA exposée : DTO en entrée et en sortie.
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
POST   /api/reports/{id}/reopen          REJECTED → DRAFT

GET    /api/reports/{id}/history
GET    /api/reports/{id}/export.pdf

POST   /api/receipts                     multipart
GET    /api/receipts/{id}                contrôle d'accès
```

Les transitions sont exposées par des endpoints d'action dédiés et non par une modification du champ `status` : chaque action a une intention métier explicite et une autorisation propre.

---

## 9. Front Angular

Quatre écrans :

1. **Connexion** : formulaire, stockage du jeton, intercepteur HTTP, garde de route.
2. **Mes notes** : liste avec statut, accès au détail.
3. **Édition d'une note** : formulaire réactif avec lignes dynamiques (`FormArray`), upload de justificatif, bouton de soumission.
4. **Validation manager** : liste des notes en attente, approbation, rejet avec motif.

**Hors périmètre front :** design personnalisé (Angular Material par défaut), librairie de gestion d'état, SSR, lazy loading, tests, écran comptable (le passage en `PAID` s'effectue via l'interface OpenAPI).

---

## 10. Planning

| Semaine | Contenu | Livrable |
|---|---|---|
| 1 | Setup, Docker PostgreSQL, Flyway, entités, DTO/MapStruct, CRUD brouillon, gestion d'erreurs globale | CRUD fonctionnel via l'interface OpenAPI |
| 2 | Spring Security + JWT, rôles, autorisation au niveau ressource, transitions et gardes, historique, tests Testcontainers | Workflow complet testé |
| 3 | Upload, export PDF, email après commit, Dockerfile, CI, déploiement Coolify, README | Application en ligne |
| 4 | Front Angular (4 écrans), backend gelé | Front connecté au backend déployé |

**Échéance ferme :** l'application est déployée à la fin de la semaine 4. En cas de retard, le périmètre est réduit ; l'échéance n'est pas repoussée. Les écrans Angular sont réduits en priorité.

---

## 11. Critères d'acceptation

- [ ] Application accessible par une URL publique
- [ ] Pipeline CI verte sur la branche principale
- [ ] Au moins un test d'intégration Testcontainers par transition de statut
- [ ] Les transitions interdites renvoient `409` et sont couvertes par des tests
- [ ] Un employé ne peut pas accéder à la note d'un autre employé - testé
- [ ] Un manager ne peut pas approuver sa propre note - testé
- [ ] README contenant : schéma de données, les trois exigences de la section 7.2, commandes de lancement local
- [ ] Jeu de données de démonstration chargé par Flyway (3 employés, 1 manager, 1 comptable, des notes dans chaque statut)

---

## 12. Journal des décisions

Toute demande d'évolution ou écart par rapport à ce document est consigné ici avec sa décision.

| Date | Sujet | Décision |
|---|---|---|
| 2026-10-05 | Version de Spring Boot (3.x initialement prévue) | Spring Boot 4.x retenu : version courante, sans impact sur le périmètre fonctionnel. |
| 2026-10-05 | Endpoint de la transition `REJECTED` → `DRAFT` | Ajout de `POST /api/reports/{id}/reopen`, transition prévue en 4.3 mais absente de la liste des endpoints. |
| 2026-10-15 | Serveur SMTP de développement (MailHog prévu en 4.6) | Mailpit retenu : successeur maintenu de MailHog, même usage (SMTP sur 1025, interface web sur 8025). |
