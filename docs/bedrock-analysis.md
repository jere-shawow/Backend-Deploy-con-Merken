# Ground-station analysis with Amazon Nova Pro

Android inference continues to use its offline SLM. This optional backend feature
requires Internet and invokes AWS Bedrock Converse using the EC2 instance role.
Default region: us-east-1. Default inference profile: us.amazon.nova-pro-v1:0,
which can route within the US. Review data residency before production use.
IAM requires bedrock:InvokeModel on the profile and its underlying model ARNs;
no AWS access keys are needed. Do not use the frontend's SSM-only role for this.

## Enable

Set BEDROCK_ANALYSIS_ENABLED=true, BEDROCK_REGION=us-east-1,
BEDROCK_MODEL_ID=us.amazon.nova-pro-v1:0 and a random server-only
BEDROCK_ANALYSIS_API_TOKEN of at least 32 characters in the backend runtime
environment. Compose must pass these variables to the container, not merely
define them in a file. No token or a short token disables access (503).
Never commit tokens or put them in browser JavaScript, Android assets or URLs.

Every /api/analysis request requires X-Analysis-Token. Use this only from a
trusted backend-for-frontend or through a private administrative connection.
Do not send the token over a public HTTP connection. Existing frontend session
authentication must protect any future UI proxy; do not proxy arbitrary paths
or expose the token to the browser. This is a server-level capability, not a
per-user authorization system. A trusted caller must enforce crew access.

## Requests

POST /api/analysis/preview accepts synthetic fixtures only:

```json
{"readings":[
  {"metric":"heart_rate","value":75,"unit":"bpm","measuredAt":null,"source":"SIMULATED"},
  {"metric":"sleep","value":7,"unit":"h","measuredAt":null,"source":"SIMULATED"},
  {"metric":"hrv","value":40,"unit":"ms","measuredAt":null,"source":"SIMULATED"},
  {"metric":"body_battery","value":50,"unit":"score_0_100","measuredAt":null,"source":"SIMULATED"}
]}
```

POST /api/analysis/astronauts/{numericMySqlId} reads the current MySQL record:

```json
{"allowRemoteAnalysis":true,"reportId":null}
```

The explicit true authorizes transmitting that record's minimized metrics to
Bedrock. Omitting it fails before reading records or invoking AWS. Do not use
UUID health_records identifiers as MySQL numeric IDs. An optional reportId
loads up to twelve latest stored snapshots **for the selected astronaut only**
through the existing report store (DynamoDB or its local fallback). No scan of
the separate health_records table and no new database tables are introduced.
Report capture timestamps are not sensor measurement timestamps. There is no
automatic all-history lookup or claim that repeated snapshots are independent.

No names, IDs, API tokens, Garmin link records, moods or report comments are sent
to Bedrock. Current measuredAt, quality, freshness and exercise clearance remain
unknown because the schema does not verify them. A Garmin-linked account is
labeled GARMIN_LINKED_BACKEND_UNVERIFIED only for pulse/sleep/HRV/Body Battery;
other fields are BACKEND_UNVERIFIED. Linking does not prove per-field provenance:
the existing API can also update readings. No real values are labeled simulated.

The result includes original structured context, English summary, one explanation
per current metric, uncertainties, zero to two leisure IDs and deterministic
activity cards. Critical existing alert states bypass Nova; warning states permit
explanations while blocking leisure and retaining REVIEW_MEDICAL. These are
existing demonstration alert rules, not validated clinical decisions. All
exercise suggestions are blocked until a reviewed mission-plan/clearance
integration exists. There is no diagnosis, dose or prescription functionality.

## Reliability, cost and privacy

One in-flight request per process and a ten-second cooldown limit accidental
duplicate billing. This is not a global budget or distributed quota. Converse
has a 45-second deadline, no SDK retries and at most 1,800 output tokens.
Usage and elapsed time are returned. Output must satisfy the exact schema,
metric set, allowed activity IDs and source allowlist; numerical values absent
from the supplied readings and some explicit unsafe phrases are rejected.
Recognizing a number does not prove the model used it with the correct meaning
or unit; this check cannot establish factual accuracy. Truncated, malformed and rejected
responses return 503, with no template silently substituted as model output.
These checks do not establish factual, English-language or clinical accuracy
of arbitrary generated prose; human review is required. Keep alerts independent.
No patient values or model text are logged or persisted by this feature.
HTTP callers/proxies should also avoid body logs. Check account-level Bedrock
invocation logging/retention policies separately.

References:
- https://docs.aws.amazon.com/nova/latest/userguide/using-converse-api.html
- https://docs.aws.amazon.com/bedrock/latest/userguide/inference-profiles-use.html
- jaxa-life: https://humans-in-space.jaxa.jp/en/life/health-in-space/life/
- project-leisure/project-policy: original experimental Merken policy, not clinical sources.

Run mvn verify for mocked unit/integration tests. A real deployment smoke test
must use the synthetic preview and confirm AWS_BEDROCK, model ID, token usage,
all metric explanations and unauthorized access rejection. This is not
validation of physiological recommendations or an automatic clinical report.

## Alert recommendations (automatic consent)

`GET /api/notifications/{id}/recommendation` requests this same analysis when a user opens an
automatic alert. It does not use `X-Analysis-Token`; instead `NOTIFICATIONS_AI_ENABLED=true`
(default) is the crew-wide authorization to send the minimized metrics to Bedrock, equivalent
to `allowRemoteAnalysis=true`. `BEDROCK_ANALYSIS_ENABLED=true` is still required.
Model text is not persisted: it is kept only in process memory, one result per astronaut per
alert episode (first alert or level change), for at most `NOTIFICATIONS_AI_CACHE_HOURS` (24).
Alerts are created and repeated independently of Nova; failures return 503/429 and leave the
alert, its body and its guide unchanged. The endpoint is reachable by any caller that can reach
the backend, so restrict network access; the cache and the existing single-flight/10-second
cooldown limit repeated billing.

