-- MySQL has no native row-level-security policy equivalent. Tenant isolation
-- is enforced by the QMS service's tenant-scoped repository predicates and
-- TenantContextGuardInterceptor.
SELECT 1;
