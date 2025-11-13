import { z } from 'zod';

export async function fetchJSON<S extends z.ZodObject>(url: string, schema: S): Promise<z.infer<S>> {
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
    return z.object({
        values: z.array(itemSchema)
    });
};
export type ListResponse<T> = {
    values: T[];
};

export const Users = ListResponse(User);
export type Users = ListResponse<User>;

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

export const Repository = z.object({
    name: z.string(),
    supportsmavendeploy: z.boolean(),
    supportspublishportal: z.boolean(),
    expirationdays: z.number().nonnegative(),
    mutable: z.boolean()
});
export type Repository = z.infer<typeof Repository>;
export function newRepository(): Repository {
    return {
        name: "",
        supportsmavendeploy: false,
        supportspublishportal: false,
        mutable: false,
        expirationdays: 0
    };
}

const reservedpaths = new Set(['api', 'dashboard', 'publish', '_internal']);

export function isRepositoryNameValid(repositoryname: string): boolean {
    return /^[a-z0-9._-]+$/.test(repositoryname) && !reservedpaths.has(repositoryname);
}

export const Repositories = ListResponse(Repository);
export type Repositories = ListResponse<Repository>;

export const Backend = z.object({
    id: z.uuid(),
    type: z.enum(["s3backend"])
});
export type Backend = z.infer<typeof Backend>;

export const Backends = ListResponse(Backend);
export type Backends = ListResponse<Backend>;

export const S3Backend = z.object({
    region: z.string(),
    endpoint: z.string(),
    accesskeyid: z.string(),
    secretaccesskey: z.string().optional()
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

export const BackendConfiguration = z.object({
    id: z.uuid().optional(),
    type: z.enum(["s3backend"]),
    s3config: S3Backend.optional()
});
export type BackendConfiguration = z.infer<typeof BackendConfiguration>;
export function newBackendConfiguration(): BackendConfiguration {
    return {
        type: "s3backend",
        s3config: newS3Backend()
    };
}
export function calculateBackendName(backend: BackendConfiguration): string {
    switch (backend.type) {
        case "s3backend":
            return `S3 (${backend.s3config?.endpoint})`;
    }
}

export function backendTypePrettyName(type: BackendConfiguration["type"]): string {
    switch (type) {
        case "s3backend": return "S3 Backend";
    }
}

