-- Comptes de démonstration. Mot de passe commun : "password" (BCrypt).
INSERT INTO users (email, password_hash, full_name, role, manager_id)
VALUES ('manager@expenseflow.dev', '$2a$10$Nllal6dNT.RFW0UUB5nANOJw/b8MpBnJfKRkGKTImhBfVjbPZ8f6y', 'Claire Martin', 'MANAGER', NULL),
       ('compta@expenseflow.dev', '$2a$10$Nllal6dNT.RFW0UUB5nANOJw/b8MpBnJfKRkGKTImhBfVjbPZ8f6y', 'Paul Bernard', 'ACCOUNTANT', NULL);

INSERT INTO users (email, password_hash, full_name, role, manager_id)
SELECT e.email, '$2a$10$Nllal6dNT.RFW0UUB5nANOJw/b8MpBnJfKRkGKTImhBfVjbPZ8f6y', e.full_name, 'EMPLOYEE', m.id
FROM (VALUES ('alice@expenseflow.dev', 'Alice Durand'),
             ('bob@expenseflow.dev', 'Bob Petit'),
             ('chloe@expenseflow.dev', 'Chloé Moreau')) AS e (email, full_name)
CROSS JOIN users m
WHERE m.email = 'manager@expenseflow.dev';
