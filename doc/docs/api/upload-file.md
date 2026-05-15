# Upload file

`POST /api/v1/files/upload`

Uploads a file to object storage and registers a file reference. The returned `ref` and `storedWith` are used to
reference the file as an `initArtefact` of a TEG.

## Request

| Header             | Required | Value                                  |
|--------------------|----------|----------------------------------------|
| `X-Auth-Principal` | yes      | Caller identifier.                     |
| `X-Auth-Roles`     | yes      | Comma-separated roles.                 |
| `Content-Type`     | yes      | `multipart/form-data; boundary=...`    |

Body: a multipart form with a single `file` part.

```http
POST /api/v1/files/upload HTTP/1.1
X-Auth-Principal: "alice"
X-Auth-Roles: "admin"
Content-Type: multipart/form-data; boundary=boundary

--boundary
Content-Disposition: form-data; name="file"; filename="sample.mp4"
Content-Type: video/mp4

<binary content>
--boundary--
```

## Response (200 OK)

```json
{
  "ref": "dd5fa300-77f2-4e4c-aeaa-d1f3de0eca22",
  "storedWith": "s3"
}
```

| Field        | Description                                                    |
|--------------|----------------------------------------------------------------|
| `ref`        | Opaque identifier of the stored object.                        |
| `storedWith` | Name of the storage backend that holds it (for example `s3`).  |

## Using the reference

Plug `ref` and `storedWith` into an `initArtefacts` entry on the schedule call:

```json
{
  "name": "input.mp4",
  "type": "FILE",
  "ref": "dd5fa300-77f2-4e4c-aeaa-d1f3de0eca22",
  "storedWith": "s3"
}
```

## Limits

There is no server-side size limit in the application. Enforce it at the reverse proxy (for example nginx
`client_max_body_size`). For the demo stack, Spring's `multipart.max-file-size` is set to 500 MB.
