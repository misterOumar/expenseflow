-- Notes de démonstration couvrant chaque statut du workflow, avec lignes, justificatifs et historique.
-- Les justificatifs n'existent qu'en base : leur téléchargement renvoie 404 (fichier indisponible).

CREATE FUNCTION pg_temp.demo_report(p_owner TEXT, p_title TEXT, p_month INT, p_year INT, p_status TEXT,
                                    p_submitted_at TIMESTAMPTZ, p_decided_at TIMESTAMPTZ,
                                    p_decided_by TEXT, p_rejection_reason TEXT) RETURNS BIGINT AS $$
    INSERT INTO expense_reports (owner_id, title, period_month, period_year, status,
                                 submitted_at, decided_at, decided_by, rejection_reason)
    SELECT owner.id, p_title, p_month, p_year, p_status, p_submitted_at, p_decided_at, decider.id, p_rejection_reason
    FROM users owner
    LEFT JOIN users decider ON decider.email = p_decided_by
    WHERE owner.email = p_owner
    RETURNING id;
$$ LANGUAGE sql;

CREATE FUNCTION pg_temp.demo_line(p_report BIGINT, p_spent_on DATE, p_category TEXT, p_description TEXT,
                                  p_amount_ht NUMERIC, p_vat_amount NUMERIC, p_receipt_filename TEXT) RETURNS VOID AS $$
    WITH receipt AS (
        INSERT INTO receipts (original_filename, stored_key, content_type, size_bytes, sha256, uploaded_by)
        SELECT p_receipt_filename, gen_random_uuid()::TEXT, 'application/pdf', 1024, repeat('0', 64), report.owner_id
        FROM expense_reports report
        WHERE report.id = p_report AND p_receipt_filename IS NOT NULL
        RETURNING id
    )
    INSERT INTO expense_lines (report_id, spent_on, category, description, amount_ht, vat_amount, amount_ttc, receipt_id)
    VALUES (p_report, p_spent_on, p_category, p_description, p_amount_ht, p_vat_amount,
            p_amount_ht + p_vat_amount, (SELECT id FROM receipt));

    UPDATE expense_reports
    SET total_amount = total_amount + p_amount_ht + p_vat_amount
    WHERE id = p_report;
$$ LANGUAGE sql;

CREATE FUNCTION pg_temp.demo_history(p_report BIGINT, p_from TEXT, p_to TEXT, p_actor TEXT,
                                     p_at TIMESTAMPTZ, p_comment TEXT) RETURNS VOID AS $$
    INSERT INTO status_history (report_id, from_status, to_status, actor_id, at, comment)
    SELECT p_report, p_from, p_to, id, p_at, p_comment
    FROM users
    WHERE email = p_actor;
$$ LANGUAGE sql;

DO $$
DECLARE
    alice   CONSTANT TEXT := 'alice@expenseflow.dev';
    bob     CONSTANT TEXT := 'bob@expenseflow.dev';
    chloe   CONSTANT TEXT := 'chloe@expenseflow.dev';
    manager CONSTANT TEXT := 'manager@expenseflow.dev';
    compta  CONSTANT TEXT := 'compta@expenseflow.dev';
    report  BIGINT;
BEGIN
    -- PAID
    report := pg_temp.demo_report(alice, 'Salon VivaTech Paris', 6, 2026, 'PAID',
                                  '2026-07-01 09:12+02', '2026-07-02 14:30+02', manager, NULL);
    PERFORM pg_temp.demo_line(report, '2026-06-11', 'TRANSPORT', 'Train Lyon - Paris aller-retour', 95.00, 9.50, 'billet-train.pdf');
    PERFORM pg_temp.demo_line(report, '2026-06-11', 'ACCOMMODATION', 'Hôtel 2 nuits', 220.00, 22.00, 'facture-hotel.pdf');
    PERFORM pg_temp.demo_line(report, '2026-06-12', 'MEAL', 'Dîner client', 45.45, 4.55, 'ticket-restaurant.pdf');
    PERFORM pg_temp.demo_history(report, 'DRAFT', 'SUBMITTED', alice, '2026-07-01 09:12+02', NULL);
    PERFORM pg_temp.demo_history(report, 'SUBMITTED', 'APPROVED', manager, '2026-07-02 14:30+02', NULL);
    PERFORM pg_temp.demo_history(report, 'APPROVED', 'PAID', compta, '2026-07-10 11:00+02', NULL);

    -- SUBMITTED
    report := pg_temp.demo_report(alice, 'Rendez-vous clients', 7, 2026, 'SUBMITTED',
                                  '2026-08-02 10:05+02', NULL, NULL, NULL);
    PERFORM pg_temp.demo_line(report, '2026-07-08', 'TRANSPORT', 'Taxi aéroport', 41.82, 4.18, 'recu-taxi.pdf');
    PERFORM pg_temp.demo_line(report, '2026-07-09', 'MEAL', 'Déjeuner client', 31.82, 3.18, 'ticket-dejeuner.pdf');
    PERFORM pg_temp.demo_history(report, 'DRAFT', 'SUBMITTED', alice, '2026-08-02 10:05+02', NULL);

    -- DRAFT, ligne sans justificatif
    report := pg_temp.demo_report(alice, 'Fournitures', 10, 2026, 'DRAFT', NULL, NULL, NULL, NULL);
    PERFORM pg_temp.demo_line(report, '2026-10-05', 'SUPPLIES', 'Cartouches d''encre', 40.00, 8.00, NULL);

    -- APPROVED
    report := pg_temp.demo_report(bob, 'Formation Kubernetes', 7, 2026, 'APPROVED',
                                  '2026-08-01 08:45+02', '2026-08-03 16:20+02', manager, NULL);
    PERFORM pg_temp.demo_line(report, '2026-07-15', 'OTHER', 'Inscription à la formation', 450.00, 90.00, 'facture-formation.pdf');
    PERFORM pg_temp.demo_line(report, '2026-07-16', 'MEAL', 'Déjeuner', 18.18, 1.82, 'ticket-dejeuner.pdf');
    PERFORM pg_temp.demo_history(report, 'DRAFT', 'SUBMITTED', bob, '2026-08-01 08:45+02', NULL);
    PERFORM pg_temp.demo_history(report, 'SUBMITTED', 'APPROVED', manager, '2026-08-03 16:20+02', NULL);

    -- REJECTED
    report := pg_temp.demo_report(bob, 'Mission Bordeaux', 8, 2026, 'REJECTED',
                                  '2026-09-01 09:30+02', '2026-09-02 10:15+02', manager,
                                  'La facture de l''hôtel est illisible, merci de la remplacer.');
    PERFORM pg_temp.demo_line(report, '2026-08-20', 'ACCOMMODATION', 'Hôtel 1 nuit', 110.00, 11.00, 'facture-hotel.pdf');
    PERFORM pg_temp.demo_line(report, '2026-08-20', 'TRANSPORT', 'Location de voiture', 75.00, 15.00, 'contrat-location.pdf');
    PERFORM pg_temp.demo_history(report, 'DRAFT', 'SUBMITTED', bob, '2026-09-01 09:30+02', NULL);
    PERFORM pg_temp.demo_history(report, 'SUBMITTED', 'REJECTED', manager, '2026-09-02 10:15+02',
                                 'La facture de l''hôtel est illisible, merci de la remplacer.');

    -- SUBMITTED
    report := pg_temp.demo_report(chloe, 'Visite fournisseur Lille', 8, 2026, 'SUBMITTED',
                                  '2026-09-03 17:40+02', NULL, NULL, NULL);
    PERFORM pg_temp.demo_line(report, '2026-08-27', 'TRANSPORT', 'Train Paris - Lille', 54.55, 5.45, 'billet-train.pdf');
    PERFORM pg_temp.demo_history(report, 'DRAFT', 'SUBMITTED', chloe, '2026-09-03 17:40+02', NULL);

    -- DRAFT après rejet puis réouverture
    report := pg_temp.demo_report(chloe, 'Séminaire équipe', 7, 2026, 'DRAFT', NULL, NULL, NULL, NULL);
    PERFORM pg_temp.demo_line(report, '2026-07-24', 'MEAL', 'Dîner d''équipe', 120.00, 12.00, 'facture-restaurant.pdf');
    PERFORM pg_temp.demo_history(report, 'DRAFT', 'SUBMITTED', chloe, '2026-08-01 12:00+02', NULL);
    PERFORM pg_temp.demo_history(report, 'SUBMITTED', 'REJECTED', manager, '2026-08-02 09:00+02',
                                 'Le dîner d''équipe doit être imputé au budget du service.');
    PERFORM pg_temp.demo_history(report, 'REJECTED', 'DRAFT', chloe, '2026-08-03 14:10+02', NULL);
END
$$;

DROP FUNCTION pg_temp.demo_report(TEXT, TEXT, INT, INT, TEXT, TIMESTAMPTZ, TIMESTAMPTZ, TEXT, TEXT);
DROP FUNCTION pg_temp.demo_line(BIGINT, DATE, TEXT, TEXT, NUMERIC, NUMERIC, TEXT);
DROP FUNCTION pg_temp.demo_history(BIGINT, TEXT, TEXT, TEXT, TIMESTAMPTZ, TEXT);
