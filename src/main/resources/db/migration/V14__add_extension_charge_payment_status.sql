-- V14__add_extension_charge_payment_status.sql
-- An extension charge carries an explicit payment-acceptance state.
-- Existing rows (none in production yet) default to PENDING.

ALTER TABLE extension_charge
    ADD COLUMN payment_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';
