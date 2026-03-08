output "cloud_run_url" {
  description = "Cloud Run backend service URL"
  value       = google_cloud_run_v2_service.backend.uri
}

output "cloud_sql_connection_name" {
  description = "Cloud SQL instance connection name (for Cloud SQL Auth Proxy)"
  value       = google_sql_database_instance.postgres.connection_name
}

output "frontend_bucket_name" {
  description = "Cloud Storage bucket name for frontend assets"
  value       = google_storage_bucket.frontend.name
}

output "artifact_registry_repo" {
  description = "Artifact Registry Docker repository URL"
  value       = "${var.region}-docker.pkg.dev/${var.project_id}/${google_artifact_registry_repository.backend_repo.repository_id}"
}

output "cloud_run_service_account_email" {
  description = "Cloud Run service account email"
  value       = google_service_account.cloud_run_sa.email
}

output "workload_identity_pool_provider" {
  description = "Workload Identity Federation provider resource name (for GitHub Actions auth)"
  value       = google_iam_workload_identity_pool_provider.github.name
}
