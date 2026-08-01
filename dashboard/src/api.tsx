import { Setter } from 'solid-js';
import { z } from 'zod';
import { OrError } from './utils';

export function nullishOptional<R>(schema: z.ZodType<R>): z.ZodOptional<z.ZodType<R | undefined>> {
    return schema.nullish().transform( x => x ?? undefined ).optional();
}

export async function fetchJSON<S extends z.ZodType>(url: string, schema: S): Promise<z.infer<S>> {
    const response = await fetch(url).then((response) => {
        if (!response.ok) {
            throw new Error(`Status ${response.status}, ${response.statusText}`);
        }
        return response.json();
    });
    return schema.parse(response);
}

export async function postURL(url: string): Promise<void> {
    const response = await fetch(url, {
        method: 'POST',
    });
    if (!response.ok) {
        throw new Error(`Status ${response.status}, ${response.statusText}`);
    }
}

export async function deleteURL(url: string): Promise<void> {
    const response = await fetch(url, {
        method: 'DELETE',
    });
    if (!response.ok) {
        throw new Error(`Status ${response.status}, ${response.statusText}`);
    }
}

export async function postJSON<S extends z.ZodObject>(url: string, body: z.infer<S>): Promise<void> {
    const response = await fetch(url, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(body)
    });
    if (!response.ok) {
        throw new Error(`Status ${response.status}, ${response.statusText}`);
    }
}

export const User = z.object({
    email: z.email(),
    id: z.uuid()
});
export type User = z.infer<typeof User>;

export const ListResponse = function <T>(itemSchema: z.ZodType<T>) {
    return z.array(itemSchema);
};
export type ListResponse<T> = T[];

export const Users = ListResponse(User);
export type Users = ListResponse<User>;

export const UserCapability = z.enum(["dashboard", "admindashboard"])
export type UserCapability = z.infer<typeof UserCapability>;
export const UserCapabilities = ListResponse(UserCapability);
export type UserCapabilities = ListResponse<UserCapability>;

export const Namespace = z.object({
    namespace: z.string(),
    confirmed: z.boolean()
});
export type Namespace = z.infer<typeof Namespace>;

export const Namespaces = ListResponse(Namespace);
export type Namespaces = ListResponse<Namespace>;

export function isNamespaceValid(namespace: string): boolean {
    return /^[a-z0-9-]+(\.[a-z0-9-]+)*$/.test(namespace);
}

export const Backend = z.object({
    id: z.uuid(),
    type: z.enum(["s3backend", "filesystembackend"])
});
export type Backend = z.infer<typeof Backend>;

export const Backends = ListResponse(Backend);
export type Backends = ListResponse<Backend>;

export const S3BackendConfiguration = z.object({
    bucket: z.string().nonempty(),
    prefix: z.string()
});
export type S3BackendConfiguration = z.infer<typeof S3BackendConfiguration>;
export function newS3BackendConfiguration(): S3BackendConfiguration {
    return {
        bucket: "",
        prefix: ""
    };
}

export const FilesystemBackendConfiguration = z.object({
    prefix: z.string()
})
export type FilesystemBackendConfiguration = z.infer<typeof FilesystemBackendConfiguration>;
export function newFilesystemBackendConfiguration(): FilesystemBackendConfiguration {
    return {
        prefix: ""
    };
}

export const Repository = z.object({
    name: z.string(),
    supportsmavendeploy: z.boolean(),
    supportspublishportal: z.boolean(),
    expirationdays: z.number().nonnegative(),
    mutable: z.boolean(),
    supportssnapshots: z.boolean(),
    backend: nullishOptional(Backend.shape.id),
    s3backend: nullishOptional(S3BackendConfiguration),
    filesystembackend: nullishOptional(FilesystemBackendConfiguration)
});
export type Repository = z.infer<typeof Repository>;
export function newRepository(): Repository {
    return {
        name: "",
        supportsmavendeploy: false,
        supportspublishportal: false,
        mutable: false,
        supportssnapshots: false,
        expirationdays: 0
    };
}
export function validateRepository(repo: Repository, setStatus: Setter<OrError>): boolean {
    if (!isRepositoryNameValid(repo.name)) {
        setStatus({ status: "error", err: "Not a valid repository name! Must be lowercase alphanumeric, dots, dashes or underscores, and not a reserved path." });
        return false;
    }
    if (repo.backend === undefined) {
        setStatus({ status: "error", err: "You must select and configure a backend" });
        return false;
    }
    return true;
}

const reservedpaths = new Set(["api", "dashboard", "publish", "_internal", "portal", "login", "logout", "signin", "swagger", "openapi"]);

function isRepositoryNameValid(repositoryname: string): boolean {
    return /^[a-z0-9._-]+$/.test(repositoryname) && !reservedpaths.has(repositoryname);
}

export const Repositories = ListResponse(Repository);
export type Repositories = ListResponse<Repository>;

export const S3Backend = z.object({
    region: z.string(),
    endpoint: z.string(),
    accesskeyid: z.string(),
    secretaccesskey: nullishOptional(z.string())
});
export type S3Backend = z.infer<typeof S3Backend>;
export function newS3Backend(): S3Backend {
    return {
        region: "",
        endpoint: "",
        accesskeyid: "",
        secretaccesskey: ""
    };
}

export const FilesystemBackend = z.object({
    location: nullishOptional(z.string())
})
export type FilesystemBackend = z.infer<typeof FilesystemBackend>
export function newFilesystemBackend(): FilesystemBackend {
    return {}
}

export const BackendConfiguration = z.object({
    id: nullishOptional(z.uuid()),
    type: z.enum(["s3backend", "filesystembackend"]),
    s3backend: nullishOptional(S3Backend),
    filesystembackend: nullishOptional(FilesystemBackend),
});
export type BackendConfiguration = z.infer<typeof BackendConfiguration>;
export function newBackendConfiguration(): BackendConfiguration {
    return {
        type: "filesystembackend",
        filesystembackend: newFilesystemBackend()
    };
}

export function backendTypePrettyName(type: BackendConfiguration["type"]): string {
    switch (type) {
        case "s3backend": return "S3";
        case "filesystembackend": return "File System";
    }
}

