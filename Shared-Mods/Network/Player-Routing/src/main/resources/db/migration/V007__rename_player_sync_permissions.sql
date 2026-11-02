-- Player Sync replaced the old player-data mod: its permission nodes moved from mpds.* to playersync.*
-- (inventory.metadata.* -> items.*, and the skip list is gone). Bumping the revision makes every proxy reload grants.
DELETE FROM auth_role_permissions WHERE permission_node LIKE 'mpds.skip.%';
DELETE FROM auth_user_permissions WHERE permission_node LIKE 'mpds.skip.%';
UPDATE IGNORE auth_role_permissions SET permission_node = CONCAT('playersync.items.', SUBSTRING(permission_node, LENGTH('mpds.inventory.metadata.') + 1)) WHERE permission_node LIKE 'mpds.inventory.metadata.%';
UPDATE IGNORE auth_user_permissions SET permission_node = CONCAT('playersync.items.', SUBSTRING(permission_node, LENGTH('mpds.inventory.metadata.') + 1)) WHERE permission_node LIKE 'mpds.inventory.metadata.%';
UPDATE IGNORE auth_role_permissions SET permission_node = CONCAT('playersync.', SUBSTRING(permission_node, LENGTH('mpds.') + 1)) WHERE permission_node LIKE 'mpds.%';
UPDATE IGNORE auth_user_permissions SET permission_node = CONCAT('playersync.', SUBSTRING(permission_node, LENGTH('mpds.') + 1)) WHERE permission_node LIKE 'mpds.%';
DELETE FROM auth_role_permissions WHERE permission_node LIKE 'mpds.%';
DELETE FROM auth_user_permissions WHERE permission_node LIKE 'mpds.%';
UPDATE auth_state SET revision = revision + 1 WHERE state_key = 'authorization'
