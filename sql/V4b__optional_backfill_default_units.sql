-- OPTIONAL. Run after V4__add_units.sql if you want every existing employee / project to land in a unit
-- straight away: it creates one unit called "General" in each department that has no unit yet, then moves
-- the department's employees and projects into it. Rename or split the units later from the Units screen.
-- Skip this script if you prefer to assign people to real units by hand.

BEGIN;

INSERT INTO units (created_at, updated_at, name, description, active, department_id)
SELECT now(), now(), 'General', 'Default unit created during the v4 migration', TRUE, d.id
FROM departments d
WHERE NOT EXISTS (SELECT 1 FROM units u WHERE u.department_id = d.id);

UPDATE employees e
SET unit_id = (SELECT u.id FROM units u WHERE u.department_id = e.department_id AND u.name = 'General')
WHERE e.department_id IS NOT NULL AND e.unit_id IS NULL
  AND EXISTS (SELECT 1 FROM units u WHERE u.department_id = e.department_id AND u.name = 'General');

UPDATE projects p
SET owning_unit_id = (SELECT u.id FROM units u WHERE u.department_id = p.owning_department_id AND u.name = 'General')
WHERE p.owning_department_id IS NOT NULL AND p.owning_unit_id IS NULL
  AND EXISTS (SELECT 1 FROM units u WHERE u.department_id = p.owning_department_id AND u.name = 'General');

COMMIT;
