variable "project_id" {
  description = "GCP project ID"
  type        = string
}

variable "region" {
  description = "GCP region for resources (EU data residency required — NFR5)"
  type        = string
  default     = "europe-west3" # Frankfurt
}

variable "environment" {
  description = "Deployment environment: staging or production"
  type        = string
  validation {
    condition     = contains(["staging", "production"], var.environment)
    error_message = "Environment must be 'staging' or 'production'."
  }
}

variable "backend_image" {
  description = "Full Docker image path for the backend (e.g., europe-west3-docker.pkg.dev/PROJECT/risk-guard/backend:SHA)"
  type        = string
}

variable "cloud_sql_instance_name" {
  description = "Cloud SQL instance name (e.g., risk-guard-db)"
  type        = string
  default     = "risk-guard-db"
}

variable "db_name" {
  description = "PostgreSQL database name"
  type        = string
  default     = "riskguard"
}

variable "db_user" {
  description = "PostgreSQL database user"
  type        = string
  default     = "riskguard"
}

variable "frontend_bucket_name" {
  description = "Cloud Storage bucket name for frontend static assets"
  type        = string
}

variable "cloud_run_service_name" {
  description = "Cloud Run service name for the backend"
  type        = string
  default     = "risk-guard-backend"
}

variable "github_repository" {
  description = "GitHub repository in 'ORG/REPO' format for Workload Identity Federation binding (e.g., 'acme-corp/risk_guard')"
  type        = string
  # No default — must be explicitly set in tfvars or CLI to avoid WIF misconfiguration
}

variable "use_cloud_sql" {
  description = "Whether to provision Cloud SQL + VPC networking. Set false for staging (uses external Neon DB instead)."
  type        = bool
  default     = true
}
