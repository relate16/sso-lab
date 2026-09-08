#!/usr/bin/env python3
"""Regression tests for repository architecture-boundary audit policy."""

from __future__ import annotations

import pathlib
import runpy
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]
AUDIT = runpy.run_path(str(ROOT / "scripts" / "test" / "audit-phase9-repository.py"))
REQUIRE_BOUNDARY = AUDIT["require_service_source_boundary"]
COMPOSE_BLOCKS = AUDIT["compose_service_blocks"]
REQUIRE_SAFE_PATH = AUDIT["require_safe_path"]
AUDIT_CONTENT = AUDIT["audit_content"]


class RepositoryEnvironmentFileAuditTest(unittest.TestCase):

    def test_only_root_environment_examples_are_allowed_and_content_is_audited(self) -> None:
        REQUIRE_SAFE_PATH(ROOT / ".env.example")
        REQUIRE_SAFE_PATH(ROOT / ".env.frontend.local.example")
        AUDIT_CONTENT(ROOT / ".env.frontend.local.example")

        for relative in (
            ".env.frontend.local",
            ".env.other.example",
            "nested/.env.frontend.local.example",
        ):
            with self.subTest(relative=relative):
                with self.assertRaisesRegex(AssertionError, "runtime environment file"):
                    REQUIRE_SAFE_PATH(ROOT / relative)


class ServiceSchemaOwnershipAuditTest(unittest.TestCase):

    def assert_allowed(self, service: str, relative: str, text: str) -> None:
        REQUIRE_BOUNDARY(service, pathlib.Path(relative), text)

    def assert_blocked(self, service: str, relative: str, text: str, message: str) -> None:
        with self.assertRaisesRegex(AssertionError, message):
            REQUIRE_BOUNDARY(service, pathlib.Path(relative), text)

    def test_service_owned_persistence_technology_and_schema_are_allowed(self) -> None:
        self.assert_allowed(
            "hr-server",
            "build.gradle.kts",
            'implementation("org.springframework.boot:spring-boot-starter-data-jpa")\n'
            'implementation("org.springframework.boot:spring-boot-starter-flyway")\n'
            'runtimeOnly("org.postgresql:postgresql")',
        )
        self.assert_allowed(
            "hr-server",
            "EmployeeEntity.java",
            '@Entity @Table(name = "employees", schema = "hr") '
            'class EmployeeEntity { JdbcTemplate jdbc; JpaRepository<?, ?> repository; }',
        )
        self.assert_allowed(
            "hr-server",
            "V1__hr.sql",
            "CREATE SCHEMA IF NOT EXISTS hr; CREATE TABLE hr.employees (id uuid PRIMARY KEY);",
        )

    def test_auth_server_implementation_dependency_and_package_reference_are_blocked(self) -> None:
        self.assert_blocked(
            "admin-server",
            "build.gradle.kts",
            'implementation(project(":backend:auth-server"))',
            "auth-server implementation module",
        )
        self.assert_blocked(
            "approval-server",
            "ApprovalService.java",
            "import com.ssolab.auth.identity.repository.UserIdentityRepository;",
            "auth-server implementation package",
        )

    def test_foreign_schema_reference_is_blocked_but_owner_schema_is_allowed(self) -> None:
        self.assert_blocked(
            "admin-server",
            "AdminQuery.java",
            'String sql = "select * from auth.users";',
            "another service.*auth",
        )
        self.assert_blocked(
            "hr-server",
            "V2__cross_service.sql",
            'INSERT INTO "approval".requests(id) VALUES (gen_random_uuid());',
            "another service.*approval",
        )
        self.assert_blocked(
            "auth-server",
            "CrossDomainQuery.java",
            'String sql = "select * from hr.employees";',
            "another service.*hr",
        )
        self.assert_allowed(
            "auth-server",
            "IdentityQuery.java",
            'String sql = "select * from auth.users";',
        )

    def test_sql_comment_does_not_count_as_direct_schema_access(self) -> None:
        self.assert_allowed(
            "hr-server",
            "V1__hr.sql",
            "-- Identity remains in auth.users and is reached through its API\n"
            "CREATE TABLE hr.employees (id uuid PRIMARY KEY);",
        )

    def test_oidc_hostname_is_not_treated_as_a_schema_reference(self) -> None:
        self.assert_allowed(
            "hr-server",
            "OidcPropertiesTest.java",
            'URI issuer = URI.create("https://auth.example.test");',
        )

    def test_auth_datasource_configuration_is_blocked_for_non_owner(self) -> None:
        self.assert_blocked(
            "hr-server",
            "application.yml",
            "spring.datasource.url: ${AUTH_DB_URL}",
            "Auth Identity datasource configuration",
        )
        self.assert_allowed(
            "hr-server",
            "application.yml",
            "spring.datasource.url: ${HR_DB_URL}\n"
            "spring.jpa.properties.hibernate.default_schema: hr\n"
            "spring.flyway.default-schema: hr",
        )
        self.assert_blocked(
            "hr-server",
            "application.yml",
            "spring.datasource.url: jdbc:postgresql://postgres/app?currentSchema=auth",
            "another service.*auth",
        )
        self.assert_blocked(
            "approval-server",
            "ApprovalQuery.java",
            'jdbc.execute("SET search_path TO admin, public");',
            "another service.*admin",
        )

    def test_compose_service_blocks_keep_service_context(self) -> None:
        blocks = COMPOSE_BLOCKS(
            "services:\n"
            "  auth-server:\n"
            "    environment:\n"
            "      AUTH_DB_URL: jdbc:postgresql://postgres/app\n"
            "  hr-server:\n"
            "    environment:\n"
            "      HR_DB_URL: jdbc:postgresql://postgres/app\n"
        )
        self.assertIn("auth-server", blocks)
        self.assertIn("hr-server", blocks)
        self.assertNotIn("AUTH_DB_URL", blocks["hr-server"])
        self.assert_allowed("hr-server", "docker-compose.yml", blocks["hr-server"])


if __name__ == "__main__":
    unittest.main()
