-- The proxy plugin was split: wakeuplobby.* permissions became routing.* (Player Routing) and waitinglobby.queue.*
-- (Waiting Lobby). Bumping the revision makes every proxy reload grants.
UPDATE IGNORE auth_role_permissions SET permission_node = CONCAT('waitinglobby.', SUBSTRING(permission_node, LENGTH('wakeuplobby.') + 1)) WHERE permission_node LIKE 'wakeuplobby.queue.%';
UPDATE IGNORE auth_user_permissions SET permission_node = CONCAT('waitinglobby.', SUBSTRING(permission_node, LENGTH('wakeuplobby.') + 1)) WHERE permission_node LIKE 'wakeuplobby.queue.%';
UPDATE IGNORE auth_role_permissions SET permission_node = CONCAT('routing.', SUBSTRING(permission_node, LENGTH('wakeuplobby.') + 1)) WHERE permission_node LIKE 'wakeuplobby.%';
UPDATE IGNORE auth_user_permissions SET permission_node = CONCAT('routing.', SUBSTRING(permission_node, LENGTH('wakeuplobby.') + 1)) WHERE permission_node LIKE 'wakeuplobby.%';
DELETE FROM auth_role_permissions WHERE permission_node LIKE 'wakeuplobby.%';
DELETE FROM auth_user_permissions WHERE permission_node LIKE 'wakeuplobby.%';
UPDATE auth_state SET revision = revision + 1 WHERE state_key = 'authorization'
