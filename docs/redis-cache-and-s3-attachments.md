# Redis cache and S3 attachments

Backend implementation of the two requests from 10 October 2026: Redis cache
logic on top of the Redis infrastructure configuration, and attachment storage
in the `resolvex-itsm-attachments` S3 bucket.

## 1. Build fixes included in this change

`main` did not build after the Redis and EventBridge changes were merged. Both
are fixed here because nothing else could be tested without them.

| Problem | Cause | Fix |
| --- | --- | --- |
| `EventBridgePublisher` did not compile | Used Jackson 2 imports (`com.fasterxml.jackson…`); Spring Boot 4 uses Jackson 3 (`tools.jackson…`) | Imports updated |
| Application could not start (all tests failed with "No CacheManager") | Spring Boot 4 moved cache auto-configuration into `spring-boot-starter-cache`; `@EnableCaching` alone finds no cache manager | Dependency added |

Two related EventBridge changes, needed before it can run on EC2:

- Credentials now come from the **default AWS credential provider chain** instead
  of a hardcoded local profile named `java-process`, which does not exist on EC2.
  To keep using that profile locally, set `AWS_PROFILE=java-process`.
- A failed publish is now **logged instead of thrown**. It runs inside the SLA
  sweep's transaction, so an exception would roll back the sweep for every
  incident, not just the one whose email failed.

## 2. Redis cache

**What is cached:** `GET /api/sla-policies` (`AdminService.listSlaPolicies()`),
cache name `slaPolicies`. It is identical for every user and changes rarely.

**Invalidation:** `createSlaPolicy`, `updateSlaPolicy` and `setSlaPolicyActive`
each empty the cache (`@CacheEvict(allEntries = true)`). Evictions are
transaction-aware: they happen after the database commit, so a concurrent read
cannot put the old rows back.

**Not cached, on purpose:** incident lists, incident details and dashboard
counts. They depend on who is asking and on the SLA clock, so caching them
could show one user another user's data or stale SLA states.

**If Redis is down:** cache errors are logged (`LoggingCacheErrorHandler`) and
the request is answered from PostgreSQL. Users see no error. Timeouts are 2s,
so requests fail over quickly.

**Serialization:** JDK serialization (Spring's default), which is why
`SlaPolicyResponse` implements `Serializable`. Keys look like
`resolvex:slaPolicies::SimpleKey []`, with a 10-minute TTL.

**Local development and tests** need no Redis: the `dev` profile uses an
in-memory cache, and the `test` profile uses none. `SlaPolicyCacheIntegrationTest`
turns an in-memory cache on to test the caching and evictions themselves.

## 3. S3 attachments

### Flow

```
Upload:  check incident access ─► validate file ─► PUT to S3 ─► save metadata row (transaction)
                                                                   │ fails?
                                                                   └─► DELETE the S3 object again
Download: check incident access ─► find row (scoped to this incident) ─► stream object from S3
Remove:   admin only ─► delete row (transaction) ─► DELETE S3 object (failure logged, not fatal)
```

- **Private bucket, no public URLs.** The bucket is never exposed; every download
  is streamed through the backend after the access check.
- **Authorization** reuses the incident's own rules (`IncidentService.requireViewable`),
  so attachments can never be more visible than their incident:
  - an employee sees attachments only on their own incidents
  - an agent sees their own, their team's, and untriaged NEW incidents
  - an admin sees all incidents
- **Unique keys:** `incidents/<incident-id>/<random-uuid>.<ext>`. The uploaded
  filename is never part of the key, so names cannot collide or inject paths.
  It is stored only for display and as the download name.
- **Validation:**
  - **Types:** only these file types are allowed: PNG, JPG/JPEG, GIF, WEBP, PDF,
    TXT, LOG, CSV, DOCX and XLSX.
  - **Contents:** the first bytes of the file must match its extension (magic
    numbers), so a renamed `.exe` is rejected.
  - **Content type:** set by the server; the browser's claimed type is ignored.
  - **Size:** empty files are rejected, and the maximum is 10 MB.
- **No orphans and no broken rows:**
  - If S3 fails during upload, no row is written.
  - If the DB write or commit fails after the S3 upload, the object is deleted
    again.
  - Remove deletes the row first, so a file that fails to delete is unreachable
    rather than leaving a row pointing at nothing.
- **Downloads** are sent with `Content-Disposition: attachment`,
  `X-Content-Type-Options: nosniff` and `Cache-Control: no-store`, so a file can
  never render as a page on our domain.

### Endpoints

| Method | Path | Who | Result |
| --- | --- | --- | --- |
| GET | `/api/incidents/{id}/attachments` | anyone who can view the incident | `200` list of metadata |
| POST | `/api/incidents/{id}/attachments` | anyone who can view the incident (not when CLOSED) | `201` metadata. Body: `multipart/form-data`, part `file` |
| GET | `/api/incidents/{id}/attachments/{attachmentId}/download` | anyone who can view the incident | `200` file stream |
| DELETE | `/api/incidents/{id}/attachments/{attachmentId}` | ADMIN | `204` |

Errors:
- `400`: bad file type, contents don't match, empty file, too large, missing `file` part, or closed incident.
- `403`: no access to the incident.
- `404`: incident or attachment not found.
- `413`: over the multipart limit.
- `503`: S3 unavailable.

Response shape:

```json
{ "id": "…", "incidentId": "…", "fileName": "screen.png", "contentType": "image/png",
  "sizeBytes": 48213, "uploadedById": "…", "uploadedByName": "Alex Employee",
  "createdAt": "2026-10-11T09:41:49Z" }
```

The S3 object key is deliberately **not** in the response.

### Database changes: `V2__incident_attachments.sql`

Flyway applies it automatically on the next start with the `postgres` profile
(the Jenkins deploy already sets `FLYWAY_ENABLED=true`). It has been verified on
PostgreSQL 16 with Hibernate `validate`.

- New table `incident_attachments`:
  - `id`, `incident_id` (FK to `incidents`), `uploaded_by_id` (FK to `users`)
  - `object_key` (unique), `original_filename`, `content_type`, `size_bytes` (> 0)
  - the usual audit columns
  - index on `incident_id`
- `incident_activities.ck_activities_action` now also allows `ATTACHMENT_ADDED`
  and `ATTACHMENT_REMOVED`, so uploads and removals appear in the activity trail.

### Frontend

The incident page has an **Attachments** card:
- **Upload:** anyone who can view the incident can attach a file.
- **Download:** fetched with the user's token, never through a bucket URL.
- **Remove:** admins only, with a confirmation step.

Nginx's `client_max_body_size` is raised to `11m`. Its default of 1 MB would
reject most screenshots before they reached the backend.

## 4. Configuration

All values come from environment variables. No secret is in the repository, and
there are no AWS keys anywhere: the S3 client uses the default credential
provider chain, which on EC2 resolves to the instance's IAM role.

| Variable | Default | Notes |
| --- | --- | --- |
| `CACHE_TYPE` | `redis` (`simple` in dev) | |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_SSL_ENABLED` | `localhost` / `6379` / `false` | Already set by the Jenkinsfile |
| `REDIS_TIMEOUT` / `REDIS_CONNECT_TIMEOUT` | `2s` | |
| `CACHE_TTL` | `10m` | |
| `REDIS_HEALTH_ENABLED` | `true` (`false` in dev) | Whether Redis is part of `/actuator/health` |
| `ATTACHMENT_STORAGE` | `s3` (`local` in dev) | |
| `S3_BUCKET_NAME` | none, required for `s3` | Already set by the Jenkinsfile. The app refuses to start without it |
| `AWS_REGION` | `ap-south-1` | Already set by the Jenkinsfile |
| `S3_KEY_PREFIX` | `incidents/` | |
| `ATTACHMENT_MAX_FILE_SIZE` / `ATTACHMENT_MAX_REQUEST_SIZE` | `10MB` / `11MB` | |
| `ATTACHMENT_LOCAL_DIR` | `./data/attachments` | `local` storage only |

## 5. New dependencies

- `org.springframework.boot:spring-boot-starter-cache`
- `software.amazon.awssdk:s3`, versioned by the `software.amazon.awssdk:bom`
  (2.31.54), which now also manages the existing `eventbridge` module.
- The AWS async Netty client (`netty-nio-client`) is excluded from both AWS
  modules. Only the synchronous clients are used, so this keeps the image smaller
  and gives Trivy less to scan.

## 6. Deployment checklist (infrastructure)

1. **IAM policy** `ResolveX-ITSM-S3-Attachments-Policy` must allow `s3:PutObject`,
   `s3:GetObject` and `s3:DeleteObject` on
   `arn:aws:s3:::resolvex-itsm-attachments/incidents/*`. No `s3:PutObjectAcl` or
   public access is needed.
2. Attach that policy to an **IAM role**, and the role to the app EC2 instance as
   its instance profile.
3. Keep **Block Public Access** on for the bucket.
4. **Redis:** the configured host is a `clustercfg.…` endpoint, which is cluster
   mode. The app connects in standalone mode. Check one of:
   - ElastiCache is cluster-mode disabled, using the primary endpoint, or
   - we add `spring.data.redis.cluster.nodes`.

   Also confirm whether in-transit encryption is on. If so, set
   `REDIS_SSL_ENABLED=true`. If Redis is misconfigured the app still works,
   because the cache falls back to the database, but `/actuator/health` reports
   DOWN while `REDIS_HEALTH_ENABLED=true`.

## 7. Tests

145 tests pass (`mvn test`): the existing 110 plus 35 new ones.

- `SlaPolicyCacheIntegrationTest`: list is cached; create, update and activate each evict it; rejected writes are harmless.
- `AttachmentIntegrationTest`: upload, list and download round trip; access rules per role; cross-incident access blocked; type, content, size and closed-incident validation; admin-only removal deletes the stored file; activity trail entry.
- `AttachmentServiceTest`:
  - S3 object deleted again when the DB save or commit fails
  - no row written when S3 fails
  - removal survives an S3 failure
  - key format
- `S3AttachmentStorageTest`: bucket, key, content type, no ACL; AWS errors mapped.
- `AttachmentFileTypesTest`: allowlist, filename cleaning, magic-number checks.
