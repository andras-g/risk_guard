terraform {
  required_version = ">= 1.6"
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # Remote state — update bucket name before first apply
  backend "gcs" {
    bucket = "risk-guard-terraform-state"
    prefix = "terraform/state"
  }
}

provider "google" {
  project = var.project_id
  region  = var.region
}

# ─────────────────────────────────────────────────────────────────────────────
# Enable required GCP APIs
# ─────────────────────────────────────────────────────────────────────────────
resource "google_project_service" "required_apis" {
  for_each = toset([
    "run.googleapis.com",
    "sqladmin.googleapis.com",
    "secretmanager.googleapis.com",
    "artifactregistry.googleapis.com",
    "compute.googleapis.com",
    "cloudscheduler.googleapis.com",
    "iam.googleapis.com",
    "iamcredentials.googleapis.com",
  ])
  service            = each.key
  disable_on_destroy = false
}

# ─────────────────────────────────────────────────────────────────────────────
# IAM — Service Account for Cloud Run (least-privilege)
# ─────────────────────────────────────────────────────────────────────────────
resource "google_service_account" "cloud_run_sa" {
  account_id   = "risk-guard-backend-sa"
  display_name = "Risk Guard Backend Cloud Run Service Account"
  project      = var.project_id
}

resource "google_project_iam_member" "cloud_run_cloudsql_client" {
  project = var.project_id
  role    = "roles/cloudsql.client"
  member  = "serviceAccount:${google_service_account.cloud_run_sa.email}"
}

resource "google_project_iam_member" "cloud_run_secret_accessor" {
  project = var.project_id
  role    = "roles/secretmanager.secretAccessor"
  member  = "serviceAccount:${google_service_account.cloud_run_sa.email}"
}

# ─────────────────────────────────────────────────────────────────────────────
# Artifact Registry — Docker repository for backend images
# ─────────────────────────────────────────────────────────────────────────────
resource "google_artifact_registry_repository" "backend_repo" {
  location      = var.region
  repository_id = "risk-guard"
  description   = "Risk Guard backend Docker images"
  format        = "DOCKER"

  depends_on = [google_project_service.required_apis]
}

# ─────────────────────────────────────────────────────────────────────────────
# Cloud SQL — PostgreSQL 17 (private IP, no public IP)
# Only provisioned when use_cloud_sql = true (production).
# Staging uses external Neon free-tier PostgreSQL to minimize cost.
# ─────────────────────────────────────────────────────────────────────────────
resource "google_sql_database_instance" "postgres" {
  count            = var.use_cloud_sql ? 1 : 0
  name             = "${var.cloud_sql_instance_name}-${var.environment}"
  database_version = "POSTGRES_17"
  region           = var.region

  settings {
    tier              = var.environment == "production" ? "db-g1-small" : "db-f1-micro"
    availability_type = var.environment == "production" ? "REGIONAL" : "ZONAL"
    disk_autoresize   = true
    disk_size         = 10

    ip_configuration {
      ipv4_enabled    = false # No public IP — private only
      private_network = google_compute_network.vpc[0].id
    }

    backup_configuration {
      enabled    = var.environment == "production"
      start_time = "02:00"
    }
  }

  deletion_protection = var.environment == "production"

  depends_on = [
    google_project_service.required_apis,
    google_service_networking_connection.private_vpc_connection,
  ]
}

resource "google_sql_database" "riskguard_db" {
  count    = var.use_cloud_sql ? 1 : 0
  name     = var.db_name
  instance = google_sql_database_instance.postgres[0].name
}

# Generate a random initial DB password (avoids circular dependency with Secret Manager data source).
# The generated password is stored as the first secret version. On subsequent applies, Terraform
# does NOT re-generate the password (lifecycle.ignore_changes) so the DB password remains stable.
resource "random_password" "db_password" {
  count            = var.use_cloud_sql ? 1 : 0
  length           = 32
  special          = true
  override_special = "!#$%^&*()-_=+[]{}|;:,.<>?"
}

resource "google_sql_user" "riskguard_user" {
  count    = var.use_cloud_sql ? 1 : 0
  name     = var.db_user
  instance = google_sql_database_instance.postgres[0].name
  password = random_password.db_password[0].result
}

# ─────────────────────────────────────────────────────────────────────────────
# VPC — Private network for Cloud SQL + Cloud Run VPC connector
# Only provisioned when use_cloud_sql = true (production).
# Staging connects to external Neon DB over public internet (SSL enforced).
# ─────────────────────────────────────────────────────────────────────────────
resource "google_compute_network" "vpc" {
  count                   = var.use_cloud_sql ? 1 : 0
  name                    = "risk-guard-vpc-${var.environment}"
  auto_create_subnetworks = false
}

resource "google_compute_subnetwork" "subnet" {
  count         = var.use_cloud_sql ? 1 : 0
  name          = "risk-guard-subnet-${var.environment}"
  ip_cidr_range = "10.0.0.0/24"
  region        = var.region
  network       = google_compute_network.vpc[0].id
}

resource "google_compute_global_address" "private_ip_range" {
  count         = var.use_cloud_sql ? 1 : 0
  name          = "risk-guard-private-ip-range-${var.environment}"
  purpose       = "VPC_PEERING"
  address_type  = "INTERNAL"
  prefix_length = 16
  network       = google_compute_network.vpc[0].id
}

resource "google_service_networking_connection" "private_vpc_connection" {
  count                   = var.use_cloud_sql ? 1 : 0
  network                 = google_compute_network.vpc[0].id
  service                 = "servicenetworking.googleapis.com"
  reserved_peering_ranges = [google_compute_global_address.private_ip_range[0].name]
}

resource "google_vpc_access_connector" "connector" {
  count         = var.use_cloud_sql ? 1 : 0
  name          = "risk-guard-connector-${var.environment}"
  region        = var.region
  subnet {
    name = google_compute_subnetwork.subnet[0].name
  }
}

# ─────────────────────────────────────────────────────────────────────────────
# Secret Manager — Secrets (values populated manually or via bootstrap script)
# ─────────────────────────────────────────────────────────────────────────────
resource "google_secret_manager_secret" "db_password" {
  count     = var.use_cloud_sql ? 1 : 0
  secret_id = "DB_PASSWORD_${upper(var.environment)}"
  replication {
    auto {}
  }
  depends_on = [google_project_service.required_apis]
}

# Neon database URL secret — used when use_cloud_sql = false (staging with external Neon DB)
resource "google_secret_manager_secret" "neon_database_url" {
  count     = var.use_cloud_sql ? 0 : 1
  secret_id = "NEON_DATABASE_URL_${upper(var.environment)}"
  replication {
    auto {}
  }
  depends_on = [google_project_service.required_apis]
}

resource "google_secret_manager_secret" "jwt_secret" {
  secret_id = "JWT_SECRET_${upper(var.environment)}"
  replication {
    auto {}
  }
  depends_on = [google_project_service.required_apis]
}

resource "google_secret_manager_secret" "google_client_secret" {
  secret_id = "GOOGLE_CLIENT_SECRET_${upper(var.environment)}"
  replication {
    auto {}
  }
  depends_on = [google_project_service.required_apis]
}

resource "google_secret_manager_secret" "google_client_id" {
  secret_id = "GOOGLE_CLIENT_ID_${upper(var.environment)}"
  replication {
    auto {}
  }
  depends_on = [google_project_service.required_apis]
}

resource "google_secret_manager_secret" "microsoft_client_secret" {
  secret_id = "MICROSOFT_CLIENT_SECRET_${upper(var.environment)}"
  replication {
    auto {}
  }
  depends_on = [google_project_service.required_apis]
}

resource "google_secret_manager_secret" "microsoft_client_id" {
  secret_id = "MICROSOFT_CLIENT_ID_${upper(var.environment)}"
  replication {
    auto {}
  }
  depends_on = [google_project_service.required_apis]
}

resource "google_secret_manager_secret" "resend_api_key" {
  secret_id = "RESEND_API_KEY_${upper(var.environment)}"
  replication {
    auto {}
  }
  depends_on = [google_project_service.required_apis]
}

# Store the generated DB password as the first secret version.
# Subsequent rotations: manually add a new version via gcloud or Cloud Console,
# then update Cloud Run service to use the new version.
resource "google_secret_manager_secret_version" "db_password_initial" {
  count       = var.use_cloud_sql ? 1 : 0
  secret      = google_secret_manager_secret.db_password[0].id
  secret_data = random_password.db_password[0].result

  lifecycle {
    # Prevent Terraform from overwriting a manually rotated password on subsequent applies
    ignore_changes = [secret_data]
  }
}

# ─────────────────────────────────────────────────────────────────────────────
# Cloud Run — Backend service
# ─────────────────────────────────────────────────────────────────────────────
resource "google_cloud_run_v2_service" "backend" {
  name     = "${var.cloud_run_service_name}-${var.environment}"
  location = var.region

  template {
    service_account = google_service_account.cloud_run_sa.email

    scaling {
      min_instance_count = var.environment == "production" ? 1 : 0  # Scale-to-zero for staging ($0 when idle)
      max_instance_count = 10
    }

    dynamic "vpc_access" {
      for_each = var.use_cloud_sql ? [1] : []
      content {
        connector = google_vpc_access_connector.connector[0].id
        egress    = "PRIVATE_RANGES_ONLY"
      }
    }

    containers {
      image = var.backend_image

      ports {
        container_port = 8080
      }

      resources {
        limits = {
          cpu    = "1"
          memory = "512Mi"
        }
      }

      # Liveness probe — /actuator/health/liveness (AC: 8)
      liveness_probe {
        http_get {
          path = "/actuator/health/liveness"
          port = 8080
        }
        initial_delay_seconds = 30
        period_seconds        = 10
        failure_threshold     = 3
      }

      # Readiness probe — /actuator/health/readiness (AC: 8)
      startup_probe {
        http_get {
          path = "/actuator/health/readiness"
          port = 8080
        }
        initial_delay_seconds = 20
        period_seconds        = 10
        failure_threshold     = 5
      }

      # ── Spring profile: "staging" for Neon, "prod" for Cloud SQL ──
      env {
        name  = "SPRING_PROFILES_ACTIVE"
        value = var.use_cloud_sql ? "prod" : "staging"
      }

      # ── Cloud SQL env vars (production only) ──
      dynamic "env" {
        for_each = var.use_cloud_sql ? [1] : []
        content {
          name  = "INSTANCE_CONNECTION_NAME"
          value = google_sql_database_instance.postgres[0].connection_name
        }
      }

      dynamic "env" {
        for_each = var.use_cloud_sql ? [1] : []
        content {
          name  = "DB_NAME"
          value = var.db_name
        }
      }

      dynamic "env" {
        for_each = var.use_cloud_sql ? [1] : []
        content {
          name  = "DB_USER"
          value = var.db_user
        }
      }

      dynamic "env" {
        for_each = var.use_cloud_sql ? [1] : []
        content {
          name = "DB_PASSWORD"
          value_source {
            secret_key_ref {
              secret  = google_secret_manager_secret.db_password[0].secret_id
              version = "latest"
            }
          }
        }
      }

      # ── Neon database URL (staging only — external PostgreSQL) ──
      dynamic "env" {
        for_each = var.use_cloud_sql ? [] : [1]
        content {
          name = "NEON_DATABASE_URL"
          value_source {
            secret_key_ref {
              secret  = google_secret_manager_secret.neon_database_url[0].secret_id
              version = "latest"
            }
          }
        }
      }

      env {
        name = "JWT_SECRET"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.jwt_secret.secret_id
            version = "latest"
          }
        }
      }

      env {
        name = "GOOGLE_CLIENT_SECRET"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.google_client_secret.secret_id
            version = "latest"
          }
        }
      }

      env {
        name = "GOOGLE_CLIENT_ID"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.google_client_id.secret_id
            version = "latest"
          }
        }
      }

      env {
        name = "MICROSOFT_CLIENT_SECRET"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.microsoft_client_secret.secret_id
            version = "latest"
          }
        }
      }

      env {
        name = "MICROSOFT_CLIENT_ID"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.microsoft_client_id.secret_id
            version = "latest"
          }
        }
      }

      env {
        name = "RESEND_API_KEY"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.resend_api_key.secret_id
            version = "latest"
          }
        }
      }
    }

    # Cloud SQL Auth Proxy — socket-based connection (production only)
    dynamic "volumes" {
      for_each = var.use_cloud_sql ? [1] : []
      content {
        name = "cloudsql"
        cloud_sql_instance {
          instances = [google_sql_database_instance.postgres[0].connection_name]
        }
      }
    }
  }

  traffic {
    type    = "TRAFFIC_TARGET_ALLOCATION_TYPE_LATEST"
    percent = 100
  }

  depends_on = [
    google_project_service.required_apis,
    google_artifact_registry_repository.backend_repo,
  ]
}

# Allow unauthenticated access to Cloud Run (public API — consumed by Nuxt frontend and external clients).
# The backend enforces authentication at the Spring Security layer (JWT / OAuth2 resource server);
# Cloud Run does NOT enforce auth. deploy.yml MUST use --allow-unauthenticated to match this.
resource "google_cloud_run_v2_service_iam_member" "backend_public" {
  project  = var.project_id
  location = var.region
  name     = google_cloud_run_v2_service.backend.name
  role     = "roles/run.invoker"
  member   = "allUsers"
}

# ─────────────────────────────────────────────────────────────────────────────
# Cloud Storage — Frontend static assets bucket (AC: 3)
# ─────────────────────────────────────────────────────────────────────────────
resource "google_storage_bucket" "frontend" {
  name          = var.frontend_bucket_name
  location      = var.region
  force_destroy = var.environment != "production"

  uniform_bucket_level_access = true

  website {
    main_page_suffix = "index.html"
    not_found_page   = "index.html" # SPA fallback
  }

  cors {
    origin          = ["*"]
    method          = ["GET", "HEAD", "OPTIONS"]
    response_header = ["*"]
    max_age_seconds = 3600
  }
}

resource "google_storage_bucket_iam_member" "frontend_public_read" {
  bucket = google_storage_bucket.frontend.name
  role   = "roles/storage.objectViewer"
  member = "allUsers"
}

# ─────────────────────────────────────────────────────────────────────────────
# Cloud CDN — Backend bucket pointing to Cloud Storage (AC: 3)
# ─────────────────────────────────────────────────────────────────────────────
resource "google_compute_backend_bucket" "frontend_cdn" {
  name        = "risk-guard-frontend-cdn-${var.environment}"
  bucket_name = google_storage_bucket.frontend.name
  enable_cdn  = true

  cdn_policy {
    cache_mode        = "CACHE_ALL_STATIC"
    default_ttl       = 3600
    max_ttl           = 86400
    client_ttl        = 3600
    negative_caching  = true
    serve_while_stale = 86400
  }
}

resource "google_compute_url_map" "frontend" {
  name            = "risk-guard-frontend-${var.environment}"
  default_service = google_compute_backend_bucket.frontend_cdn.id
}

resource "google_compute_target_https_proxy" "frontend" {
  name    = "risk-guard-frontend-https-${var.environment}"
  url_map = google_compute_url_map.frontend.id
  ssl_certificates = [
    google_compute_managed_ssl_certificate.frontend.id,
  ]
}

resource "google_compute_managed_ssl_certificate" "frontend" {
  name = "risk-guard-frontend-cert-${var.environment}"
  managed {
    # Update with actual domain
    domains = [var.environment == "production" ? "app.riskguard.hu" : "staging.riskguard.hu"]
  }
}

resource "google_compute_global_forwarding_rule" "frontend_https" {
  name        = "risk-guard-frontend-https-${var.environment}"
  target      = google_compute_target_https_proxy.frontend.id
  port_range  = "443"
  ip_protocol = "TCP"
}

# ─────────────────────────────────────────────────────────────────────────────
# Workload Identity Federation — GitHub Actions authentication (AC: 4, no SA keys)
# ─────────────────────────────────────────────────────────────────────────────
resource "google_iam_workload_identity_pool" "github" {
  workload_identity_pool_id = "github-actions-pool"
  display_name              = "GitHub Actions Pool"
  description               = "Identity pool for GitHub Actions CI/CD"

  depends_on = [google_project_service.required_apis]
}

resource "google_iam_workload_identity_pool_provider" "github" {
  workload_identity_pool_id          = google_iam_workload_identity_pool.github.workload_identity_pool_id
  workload_identity_pool_provider_id = "github-provider"
  display_name                       = "GitHub Actions Provider"

  attribute_mapping = {
    "google.subject"       = "assertion.sub"
    "attribute.actor"      = "assertion.actor"
    "attribute.repository" = "assertion.repository"
  }

  attribute_condition = "assertion.repository == '${var.github_repository}'"

  oidc {
    issuer_uri = "https://token.actions.githubusercontent.com"
  }
}

# Allow GitHub Actions to impersonate the Cloud Run service account
resource "google_service_account_iam_member" "github_wif_binding" {
  service_account_id = google_service_account.cloud_run_sa.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "principalSet://iam.googleapis.com/${google_iam_workload_identity_pool.github.name}/attribute.repository/${var.github_repository}"
}
